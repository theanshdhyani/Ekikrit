package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EkikritDatabase
import com.example.data.local.SeedData
import com.example.data.model.ApplicationDraftEntity
import com.example.data.model.ApplicationEntity
import com.example.data.model.DocumentEntity
import com.example.data.model.StudentEntity
import com.example.data.repository.EkikritRepository
import com.example.domain.EligibilityEngine
import com.example.domain.EligibilityStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EkikritFinalValidationTest {

    private lateinit var db: EkikritDatabase
    private lateinit var repository: EkikritRepository

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, EkikritDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        SeedData.resetDemo(db)
        repository = EkikritRepository(db, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testEligibilityEnforcementBlocksIneligibleAndConflicts() = runBlocking {
        // Student 3 (Mangal Oraon, secondary school student) trying to apply for Top Class (Higher Ed).
        // switchStudent only allows the demo identities, so target the student explicitly.
        val student3 = SeedData.students[2]
        val (success, msg) = repository.applyForScheme("SCH_TOPCLASS", studentIdOverride = student3.id)
        assertFalse(success)
        assertTrue(msg.contains("eligible", ignoreCase = true))

        // Student 1 (Birsa Munda) trying to apply for Top Class when already having an active PMS award
        val student1 = SeedData.students[0]
        repository.switchStudent(student1.id)

        // Birsa already has an active Post-Matric (SCH_PMS) application
        val (topSuccess, topMsg) = repository.applyForScheme("SCH_TOPCLASS")
        assertFalse(topSuccess)
        assertTrue(topMsg.contains("active scholarship", ignoreCase = true) || topMsg.contains("Conflict", ignoreCase = true))
    }

    @Test
    fun testSwitchStudentRejectsNonDemoIdentities() = runBlocking {
        for (blockedId in listOf("STU_2026_02", "STU_2026_03", "STU_ATTACKER_99", "")) {
            val error = runCatching { repository.switchStudent(blockedId) }.exceptionOrNull()
            assertTrue("switchStudent('$blockedId') must be rejected", error is IllegalArgumentException)
        }
        // A rejected switch must not change the active session.
        assertEquals("STU_2026_01", repository.activeStudentId.value)
    }

    @Test
    fun testScholarshipConflictLogicIgnoresCompletedHistoricalAwards() = runBlocking {
        val student1 = SeedData.students[0]
        val topClassScheme = SeedData.schemes.first { it.id == "SCH_TOPCLASS" }
        val docs = SeedData.documents.filter { it.studentId == student1.id }

        // Historical application that has been fully disbursed/completed
        val historicalApp = ApplicationEntity(
            id = "APP_HISTORICAL_001",
            studentId = student1.id,
            schemeId = "SCH_PRE",
            schemeCode = "PRE-MATRIC-ST",
            schemeName = "Pre-Matric Scholarship Scheme for ST Students",
            currentStage = "DISBURSED",
            statusText = "DBT Disbursed",
            appliedDate = "01 Jan 2025",
            lastUpdated = "01 Jan 2025"
        )

        val eval = EligibilityEngine.evaluate(
            student = student1,
            scheme = topClassScheme,
            documents = docs,
            existingApplications = listOf(historicalApp)
        )

        assertEquals(EligibilityStatus.ELIGIBLE, eval.status)
        assertNull(eval.conflictReason)
    }

    @Test
    fun testOfflineDraftStudentIsolation() = runBlocking {
        // The active session stays on Birsa (the only student switchStudent allows besides the reviewer).
        val activeStudent = SeedData.students[0]
        assertEquals(activeStudent.id, repository.activeStudentId.value)

        // A separate, eligible secondary-school student owns the pending offline draft.
        val draftOwner = StudentEntity(
            id = "STU_TEST_DRAFT_OWNER",
            name = "Draft Owner",
            institutionId = "UDISE-000000000",
            institutionName = "Government High School",
            course = "Class X (Secondary)",
            academicLevel = "SECONDARY",
            category = "ST (Oraon)",
            pvtgCommunity = null,
            annualIncome = 100000.0
        )
        db.studentDao().insertStudent(draftOwner)
        listOf("Aadhaar", "Caste", "Income", "Marksheet").forEachIndexed { index, type ->
            db.documentDao().insert(
                DocumentEntity(
                    id = "DOC_TEST_DRAFT_$index",
                    studentId = draftOwner.id,
                    type = type,
                    title = type,
                    docNumberMasked = "XXXX",
                    verificationStatus = "VERIFIED",
                    issuedDate = "01 Jan 2026",
                    issuedBy = "Test Authority"
                )
            )
        }

        val activeStudentPreMatricBefore = db.applicationDao()
            .getApplicationsForStudent(activeStudent.id).count { it.schemeId == "SCH_PRE" }

        db.applicationDraftDao().insert(
            ApplicationDraftEntity(
                id = "DRAFT_TEST_001",
                studentId = draftOwner.id,
                schemeId = "SCH_PRE",
                declaredIncome = 100000.0,
                lastSavedTimestamp = "2026-09-19 10:00:00",
                isPendingSync = true
            )
        )

        // Trigger sync of pending drafts while a different student is the active session.
        repository.syncPendingDrafts()

        val ownerApps = db.applicationDao().getApplicationsForStudent(draftOwner.id)
        val activeStudentPreMatricAfter = db.applicationDao()
            .getApplicationsForStudent(activeStudent.id).count { it.schemeId == "SCH_PRE" }

        // The draft owner received the application ...
        assertTrue(ownerApps.any { it.schemeId == "SCH_PRE" })
        // ... and the active student's applications were left alone.
        assertEquals(activeStudentPreMatricBefore, activeStudentPreMatricAfter)
    }

    @Test
    fun testVerificationStateTransitionsAndOfficerResolution() = runBlocking {
        val student1 = SeedData.students[0]
        repository.switchStudent(student1.id)

        val app = db.applicationDao().getApplicationsForStudent(student1.id).first()

        // Execute verification across 7 national registries
        repository.runSevenSourceVerification(app.id)

        val updatedApp = db.applicationDao().getApplicationById(app.id)
        assertNotNull(updatedApp)
        assertTrue(updatedApp!!.hasDiscrepancy)
        assertEquals("INSTITUTE_VERIFICATION", updatedApp.currentStage)

        // Officer resolves review item
        val reviewItem = db.reviewQueueDao().getByAppId(app.id)
        assertNotNull(reviewItem)

        repository.switchStudent("REV_OFFICER_01")
        repository.resolveReviewItem(reviewItem!!.id, isApproved = true, notes = "Verified within tolerance")

        val clearedApp = db.applicationDao().getApplicationById(app.id)
        assertNotNull(clearedApp)
        assertFalse(clearedApp!!.hasDiscrepancy)
        assertEquals("STATE_VERIFICATION", clearedApp.currentStage)
    }

    @Test
    fun testJagoSourceOfTruthReadsActualDatabaseRecords() = runBlocking {
        val student1 = SeedData.students[0]
        repository.switchStudent(student1.id)

        val statusResponse = repository.generateJagoResponse("What is my application status?")
        assertTrue(statusResponse.content.contains("Top Class") || statusResponse.content.contains("INSTITUTE") || statusResponse.content.contains("Stage"))

        val paymentResponse = repository.generateJagoResponse("When will my payment disburse?")
        assertTrue(paymentResponse.content.contains("DBT") || paymentResponse.content.contains("Aadhaar Rail"))
    }

    @Test
    fun testJagoOfflineFallbackIndicator() = runBlocking {
        repository.setOfflineMode(true)
        val response = repository.generateJagoResponse("Check my eligibility")
        assertTrue(response.content.contains("Offline Assistance Mode Active"))
    }

    @Test
    fun testReviewerApprovalNotificationWording() = runBlocking {
        val student1 = SeedData.students[0]
        repository.switchStudent(student1.id)

        val app = db.applicationDao().getApplicationsForStudent(student1.id).first()
        repository.runSevenSourceVerification(app.id)

        val reviewItem = db.reviewQueueDao().getByAppId(app.id)
        assertNotNull(reviewItem)

        repository.switchStudent("REV_OFFICER_01")
        repository.resolveReviewItem(reviewItem!!.id, isApproved = true, notes = "Verified within tolerance")

        val notifications = db.notificationDao().getNotificationsForStudentFlow(student1.id).first()
        val reviewNotif = notifications.firstOrNull { it.title == "Verification issue resolved" }

        assertNotNull(reviewNotif)
        assertTrue(reviewNotif!!.message.contains("moved to State Verification"))
        assertEquals("REVIEW", reviewNotif.type)
    }

    @Test
    fun testScholarshipsFilterStrictlyChecksEligibleStatus() = runBlocking {
        val student3 = SeedData.students[2] // Secondary school student
        val topClassScheme = SeedData.schemes.first { it.id == "SCH_TOPCLASS" }

        val eval = EligibilityEngine.evaluate(student3, topClassScheme, emptyList(), emptyList())
        assertEquals(EligibilityStatus.NOT_ELIGIBLE, eval.status)
        assertNotEquals(EligibilityStatus.ELIGIBLE, eval.status)
    }

    @Test
    fun testStudentDataIsolationQueries() = runBlocking {
        val studentA = SeedData.students[0]
        val studentB = SeedData.students[1]

        val docsA = db.documentDao().getDocumentsForStudent(studentA.id)
        val docsB = db.documentDao().getDocumentsForStudent(studentB.id)

        assertTrue(docsA.all { it.studentId == studentA.id })
        assertTrue(docsB.all { it.studentId == studentB.id })

        val appsA = db.applicationDao().getApplicationsForStudent(studentA.id)
        val appsB = db.applicationDao().getApplicationsForStudent(studentB.id)

        assertTrue(appsA.all { it.studentId == studentA.id })
        assertTrue(appsB.all { it.studentId == studentB.id })
    }

    @Test
    fun testFreshInstallSeedingAllFourIdentitiesAndTables() = runBlocking {
        // Verify all 4 identities exist in the seeded database
        val students = db.studentDao().getAllStudents()
        assertEquals(4, students.size)
        val studentIds = students.map { it.id }.toSet()
        assertTrue(studentIds.contains("STU_2026_01"))
        assertTrue(studentIds.contains("STU_2026_02"))
        assertTrue(studentIds.contains("STU_2026_03"))
        assertTrue(studentIds.contains("REV_OFFICER_01"))

        // Verify schemes, applications, documents, verification records, review queue, disbursements, notifications
        val schemes = db.schemeDao().getAllSchemes()
        assertEquals(5, schemes.size)

        val applications = db.applicationDao().getAllApplicationsFlow().first()
        assertTrue("Applications must be seeded for multiple students", applications.size >= 3)

        val documents = db.documentDao().getAllDocumentsFlow().first()
        assertTrue("Documents must be seeded", documents.isNotEmpty())

        val verificationRecords = db.verificationRecordDao().getRecordsForApp("APP_PMS_2026_001")
        assertTrue("Verification records must be seeded", verificationRecords.isNotEmpty())

        val reviewQueue = db.reviewQueueDao().getAllReviewItemsFlow().first()
        assertTrue("Review queue must have pending item for STU_2026_01", reviewQueue.isNotEmpty())

        val disbursements = db.disbursementDao().getDisbursementsForStudent("STU_2026_01")
        assertTrue("Disbursements must be seeded", disbursements.isNotEmpty())

        val notifications = db.notificationDao().getNotificationsForStudentFlow("STU_2026_01").first()
        assertTrue("Notifications must be seeded", notifications.isNotEmpty())
    }

    @Test
    fun testUniqueIndexBlocksDuplicateApplicationInSameYear() = runBlocking {
        val app1 = ApplicationEntity(
            id = "APP_TEST_UNIQUE_01",
            studentId = "STU_2026_01",
            schemeId = "SCH_PRE",
            schemeCode = "PRE-MATRIC-ST",
            schemeName = "Pre-Matric Scholarship",
            academicYear = "2026-27",
            currentStage = "SUBMITTED",
            statusText = "Submitted",
            appliedDate = "01 Sep 2026",
            lastUpdated = "01 Sep 2026"
        )
        db.applicationDao().insert(app1)

        val duplicateApp = ApplicationEntity(
            id = "APP_TEST_UNIQUE_02",
            studentId = "STU_2026_01",
            schemeId = "SCH_PRE",
            schemeCode = "PRE-MATRIC-ST",
            schemeName = "Pre-Matric Scholarship",
            academicYear = "2026-27",
            currentStage = "SUBMITTED",
            statusText = "Duplicate",
            appliedDate = "02 Sep 2026",
            lastUpdated = "02 Sep 2026"
        )

        // The DAO uses OnConflictStrategy.REPLACE, which respects the unique index by replacing or repository rejects
        db.applicationDao().insert(duplicateApp)
        val apps = db.applicationDao().getApplicationsForStudent("STU_2026_01").filter { it.schemeId == "SCH_PRE" && it.academicYear == "2026-27" }
        assertEquals("Unique index ensures only one application exists for the same student, scheme, and academic year", 1, apps.size)
    }

    @Test
    fun testDpdpConsentPersistence() = runBlocking {
        val student1 = SeedData.students[0]

        // Grant consent
        repository.updateStudentConsent(student1.id, true)
        var updatedStudent = db.studentDao().getStudent(student1.id)
        assertTrue(updatedStudent!!.hasConsentGiven)

        // Revoke consent
        repository.updateStudentConsent(student1.id, false)
        updatedStudent = db.studentDao().getStudent(student1.id)
        assertFalse(updatedStudent!!.hasConsentGiven)

        // Grant again
        repository.updateStudentConsent(student1.id, true)
        updatedStudent = db.studentDao().getStudent(student1.id)
        assertTrue(updatedStudent!!.hasConsentGiven)
    }
}
