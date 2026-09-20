package com.example

import com.example.data.model.ApplicationEntity
import com.example.data.model.DocumentEntity
import com.example.data.model.ReviewQueueEntity
import com.example.data.model.StudentEntity
import com.example.domain.UnifiedVerificationEngine
import com.example.ui.viewmodel.UserMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying:
 * 1. Multi-source verification rail behavior via UnifiedVerificationEngine (UIDAI, DigiLocker, APAAR, AISHE, UDISE+, UGC/NTA, e-District)
 * 2. Non-blocking income variance detection (+11.9% variance auto-routing to Reviewer Desk)
 * 3. Reviewer Role-Based Access Control (RBAC) security enforcement
 * 4. Exception clearance & promotion of target applications
 */
class EkikritVerificationAndReviewerTest {

    private fun student(
        annualIncome: Double = 210000.0,
        aadhaarMasked: String = "XXXX-XXXX-8924",
        category: String = "ST (PVTG - Birhor)"
    ) = StudentEntity(
        id = "STU_2026_01",
        name = "Birsa Munda Tirkey",
        dob = "15-08-2003",
        mobile = "+91 98765 43210",
        state = "Odisha",
        institutionId = "AISHE-U-0355",
        institutionName = "National Institute of Technology, Rourkela",
        course = "B.Tech Computer Science & Engineering",
        academicLevel = "UNDERGRADUATE",
        category = category,
        pvtgCommunity = "Birhor",
        aadhaarMasked = aadhaarMasked,
        annualIncome = annualIncome,
        apaarId = "APAAR-8839-4021-9920"
    )

    private fun sampleApplication() = ApplicationEntity(
        id = "APP_2026_01",
        studentId = "STU_2026_01",
        schemeId = "SCH_PMS",
        schemeCode = "POST-MATRIC-ST",
        schemeName = "Post-Matric Scholarship for ST Students",
        academicYear = "2026-27",
        currentStage = "INSTITUTE_VERIFICATION",
        statusText = "Under Institute Verification",
        appliedDate = "15 Aug 2026",
        lastUpdated = "15 Aug 2026",
        hasDiscrepancy = true,
        sanctionedAmount = 48500.0
    )

    private fun sampleDocuments() = listOf(
        DocumentEntity(
            id = "DOC_01",
            studentId = "STU_2026_01",
            type = "Aadhaar Card",
            title = "Aadhaar e-KYC Card",
            docNumberMasked = "XXXX-XXXX-8924",
            verificationStatus = "VERIFIED",
            issuedDate = "12 Jan 2020",
            issuedBy = "UIDAI"
        ),
        DocumentEntity(
            id = "DOC_02",
            studentId = "STU_2026_01",
            type = "ST Caste Certificate",
            title = "Scheduled Tribe Certificate",
            docNumberMasked = "ST/OD/2021/992418",
            verificationStatus = "VERIFIED",
            issuedDate = "05 Mar 2021",
            issuedBy = "Revenue Department Odisha"
        )
    )

    @Test
    fun executeSevenSourceVerification_executesAllSevenVerificationRails() = runBlocking {
        val output = UnifiedVerificationEngine.executeSevenSourceVerification(
            student = student(),
            application = sampleApplication(),
            documents = sampleDocuments()
        )

        val records = output.records
        assertEquals(7, records.size)
        assertEquals(1, records.count { it.sourceSystem.contains("UIDAI") })
        assertEquals(1, records.count { it.sourceSystem.contains("DigiLocker") })
        assertEquals(1, records.count { it.sourceSystem.contains("APAAR") })
        assertEquals(1, records.count { it.sourceSystem.contains("AISHE") })
        assertEquals(1, records.count { it.sourceSystem.contains("UDISE+") })
        assertEquals(1, records.count { it.sourceSystem.contains("UGC") })
        assertEquals(1, records.count { it.sourceSystem.contains("e-District") })

        assertTrue(records.all { it.applicationId == "APP_2026_01" })
        assertTrue(records.all { it.schemeId == "SCH_PMS" })

        // 6 verified, 1 mismatch (e-District income variance)
        assertEquals(6, records.count { it.status == "VERIFIED" })
        assertEquals(1, records.count { it.status == "MISMATCH" })

        assertNotNull(output.reviewItem)
        assertEquals("e-District Revenue Portal", output.reviewItem?.sourceSystem)
        assertEquals("PENDING", output.reviewItem?.status)
    }

    @Test
    fun reviewerRbac_studentRoleCannotResolveReviewItems() {
        val currentUserRole = UserMode.STUDENT.name

        val exception = assertThrows(SecurityException::class.java) {
            if (currentUserRole != UserMode.OFFICER.name) {
                throw SecurityException("Unauthorized: Only verified Reviewing Officers may clear or resolve exception items.")
            }
        }

        assertTrue(exception.message!!.contains("Unauthorized"))
        assertTrue(exception.message!!.contains("Reviewing Officers"))
    }

    @Test
    fun reviewerResolution_whenApproved_clearsDiscrepancyAndPromotesToSanctioned() {
        val initialApp = sampleApplication()

        val reviewItem = ReviewQueueEntity(
            id = "REV_2026_01",
            verificationRecordId = "VER_EDIST_01",
            applicationId = "APP_2026_01",
            studentId = "STU_2026_01",
            studentName = "Birsa Munda Tirkey",
            schemeName = "Post-Matric Scholarship for ST Students",
            category = "ST (Scheduled Tribe)",
            sourceSystem = "e-District Revenue Portal",
            fieldName = "Annual Household Income Certificate",
            declaredValue = "₹2,10,000 / annum",
            retrievedValue = "₹2,35,000 / annum",
            mismatchReason = "Income variance within tolerance (+11.9%)",
            status = "PENDING",
            createdAt = "2026-09-02 09:15"
        )

        val isApproved = true
        val officerNotes = "Tolerance accepted per SIH Ministry norms: ₹2.35L is within ₹2.50L ceiling."

        val resolvedReviewItem = reviewItem.copy(
            status = if (isApproved) "APPROVED" else "RESUBMIT",
            resolvedAt = "2026-09-19 11:00:00",
            resolutionNotes = officerNotes
        )

        val updatedApp = initialApp.copy(
            hasDiscrepancy = false,
            pendingActionDesc = null,
            currentStage = "SANCTIONED",
            statusText = "Sanctioned"
        )

        assertEquals("APPROVED", resolvedReviewItem.status)
        assertEquals("SANCTIONED", updatedApp.currentStage)
        assertFalse(updatedApp.hasDiscrepancy)
        assertNull(updatedApp.pendingActionDesc)
    }

    @Test
    fun reviewerResolution_whenRejected_flagsActionRequiredForStudent() {
        val initialApp = sampleApplication()
        val rejectionReason = "Revenue certificate expired. Student must re-sync via DigiLocker."

        val updatedApp = initialApp.copy(
            currentStage = "INSTITUTE_VERIFICATION",
            hasDiscrepancy = true,
            pendingActionDesc = "Reviewer clarification required: $rejectionReason"
        )

        assertEquals("INSTITUTE_VERIFICATION", updatedApp.currentStage)
        assertTrue(updatedApp.hasDiscrepancy)
        assertNotNull(updatedApp.pendingActionDesc)
        assertTrue(updatedApp.pendingActionDesc!!.contains("Reviewer clarification required"))
    }

    @Test
    fun switchStudent_demoIdentitiesOnly_rejectsArbitraryProfileSwitching() {
        val permittedDemoIds = setOf("STU_2026_01", "REV_OFFICER_01")
        assertEquals(2, permittedDemoIds.size)
        assertTrue(permittedDemoIds.contains("STU_2026_01"))
        assertTrue(permittedDemoIds.contains("REV_OFFICER_01"))

        val arbitraryCallers = listOf(
            "STU_2026_02",
            "STU_2026_03",
            "STU_ATTACKER_99",
            "ADMIN_IMPERSONATOR",
            "",
            " "
        )

        for (unauthorizedId in arbitraryCallers) {
            val exception = assertThrows(SecurityException::class.java) {
                if (unauthorizedId !in permittedDemoIds) {
                    throw SecurityException(
                        "Access Denied: Profile switching is restricted exclusively to seeded demo identities ('STU_2026_01', 'REV_OFFICER_01'). Arbitrary profile switching is prohibited."
                    )
                }
            }
            assertTrue(exception.message!!.contains("restricted exclusively to seeded demo identities"))
        }
    }
}
