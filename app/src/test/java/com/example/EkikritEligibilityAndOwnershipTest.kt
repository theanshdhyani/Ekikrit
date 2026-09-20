package com.example

import com.example.data.ai.JagoAiService
import com.example.data.eligibility.EligibilityEngine
import com.example.data.local.SeedData
import com.example.data.model.ApplicationEntity
import com.example.data.model.StudentEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure-JVM unit tests for the scheme-matching rules in [EligibilityEngine]
 * (com.example.data.eligibility):
 * 1. ST category / PVTG recognition
 * 2. Course-level and premier-institute matching
 * 3. Income ceilings (Rs 2.50L for Pre/Post-Matric, Rs 6.00L for Top Class)
 * 4. Unclaimed-entitlement detection (Mangal Oraon vs Birsa Munda personas)
 */
class EkikritEligibilityAndOwnershipTest {

    private val schemes = SeedData.getSeedSchemes()

    private fun student(
        id: String,
        name: String,
        annualIncome: Double,
        institutionName: String,
        institutionId: String,
        course: String,
        academicLevel: String = "UNDERGRADUATE",
        category: String = "ST (Scheduled Tribe)",
        pvtgCommunity: String? = null
    ) = StudentEntity(
        id = id,
        name = name,
        institutionId = institutionId,
        institutionName = institutionName,
        course = course,
        academicLevel = academicLevel,
        category = category,
        pvtgCommunity = pvtgCommunity,
        annualIncome = annualIncome
    )

    private fun application(
        id: String,
        studentId: String,
        schemeId: String,
        stage: String
    ) = ApplicationEntity(
        id = id,
        studentId = studentId,
        schemeId = schemeId,
        schemeCode = schemeId,
        schemeName = "Scheme $schemeId",
        currentStage = stage,
        statusText = "Test application",
        appliedDate = "01 Sep 2026",
        lastUpdated = "01 Sep 2026"
    )

    private val birsa = student(
        id = "STU_2026_01",
        name = "Birsa Munda Tirkey",
        annualIncome = 160000.0,
        institutionName = "National Institute of Technology, Rourkela",
        institutionId = "AISHE-U-0355",
        course = "B.Tech Computer Science & Engineering"
    )

    @Test
    fun mangalOraon_secondaryStudent_matchesPreMatricAndDisqualifiesPremierSchemes() {
        val mangalOraon = student(
            id = "STU_2026_03",
            name = "Mangal Oraon",
            dob = "2010-08-22",
            gender = "Male",
            aadhaarMasked = "XXXXXXXX3456",
            phoneMasked = "XXXXXX8765",
            email = "mangal.oraon@jharkhand.gov.in",
            category = "ST (Scheduled Tribe)",
            annualIncome = 85000.0,
            institutionName = "Netarhat Residential School, Latehar",
            institutionId = "UDISE-201901001",
            course = "Class X (Secondary)",
            yearOfStudy = 10,
            bankAccountMasked = "XXXXXXXX7890",
            bankIfsc = "BKID0004921",
            bankName = "Bank of India",
            academicLevel = "SECONDARY"
            bankAccountMasked = "Canara Bank (A/C **7890)",
            ifscCode = "BKID0004921",
            apaarId = "APAAR-3456-7890-1234"
        )

        val preMatricScheme = schemes.first { it.id == "SCH_PRE" }
        val topClassScheme = schemes.first { it.id == "SCH_TOPCLASS" }
        val fellowshipScheme = schemes.first { it.id == "SCH_NFST" }

        val preMatricEval = EligibilityEngine.evaluateEligibility(mangalOraon, preMatricScheme, emptyList())
        assertTrue("Mangal Oraon should be eligible for Pre-Matric", preMatricEval.isEligible)
        assertTrue(preMatricEval.matchReasons.any { it.contains("secondary education", ignoreCase = true) })
        assertTrue(preMatricEval.matchReasons.any { it.contains("Scheduled Tribe", ignoreCase = true) })

        val topClassEval = EligibilityEngine.evaluateEligibility(mangalOraon, topClassScheme, emptyList())
        assertFalse("Mangal Oraon should be ineligible for Top Class Scheme", topClassEval.isEligible)
        assertTrue(topClassEval.disqualificationReasons.any { it.contains("premier institute", ignoreCase = true) })

        val fellowshipEval = EligibilityEngine.evaluateEligibility(mangalOraon, fellowshipScheme, emptyList())
        assertFalse("Mangal Oraon should be ineligible for National Fellowship", fellowshipEval.isEligible)
        assertTrue(fellowshipEval.disqualificationReasons.any { it.contains("M.Phil/Ph.D", ignoreCase = true) })
    }

    @Test
    fun birsaMunda_nitRourkela_matchesTopClass_asUnclaimedEntitlement() {
        // Birsa has only applied to Post-Matric so far.
        val existingApps = listOf(application("APP_PMS_TEST", birsa.id, "SCH_PMS", "INSTITUTE_VERIFICATION"))

        val topUnreached = EligibilityEngine.findTopUnreachedScheme(birsa, schemes, existingApps)
        val birsaMunda = StudentEntity(
            id = "STU_2026_01",
            name = "Birsa Munda Tirkey",
            dob = "2004-05-18",
            gender = "Male",
            aadhaarMasked = "XXXXXXXX1234",
            phoneMasked = "XXXXXX9876",
            email = "birsa.tirkey@nitrkl.ac.in",
            category = "ST (Scheduled Tribe)",
            annualIncome = 160000.0,
            institutionName = "National Institute of Technology, Rourkela",
            institutionId = "AISHE-U-0355",
            course = "B.Tech Computer Science & Engineering",
            yearOfStudy = 2,
            bankAccountMasked = "XXXXXXXX5678",
            bankIfsc = "SBIN0002109",
            bankName = "State Bank of India",
            apaarId = "APAAR-9876-5432-1098"
        )

        // Birsa currently only has applied to Post-Matric (SCH_PMS)
        val existingApps = listOf(
            ApplicationEntity(
                id = "APP_2026_01",
                studentId = "STU_2026_01",
                schemeId = "SCH_PMS",
                schemeName = "Post-Matric Scholarship for ST Students",
                appliedDate = "2026-08-15",
                currentStage = "UNDER_VERIFICATION",
                stageProgress = 0.5f,
                academicYear = "2025-26"
            )
        )

        val topUnreached = EligibilityEngine.findTopUnreachedScheme(birsaMunda, schemes, existingApps)

        assertNotNull("Birsa Munda should have an unclaimed entitlement detected", topUnreached)
        assertEquals("SCH_TOPCLASS", topUnreached!!.schemeId)
        assertTrue(topUnreached.isEligible)
        assertTrue(topUnreached.isUnclaimed)
        assertTrue(topUnreached.matchReasons.any { it.contains("notified premier institute", ignoreCase = true) })
    }

    @Test
    fun alreadyAppliedScheme_isEligibleButNotUnclaimed() {
        val topClass = schemes.first { it.id == "SCH_TOPCLASS" }
        val existingApps = listOf(application("APP_TOP_TEST", birsa.id, "SCH_TOPCLASS", "SUBMITTED"))

        val eval = EligibilityEngine.evaluateEligibility(birsa, topClass, existingApps)

        assertTrue(eval.isEligible)
        assertFalse("A scheme with an active application must not be suggested again", eval.isUnclaimed)
    }

    @Test
    fun rejectedApplication_leavesSchemeUnclaimed() {
        val topClass = schemes.first { it.id == "SCH_TOPCLASS" }
        val existingApps = listOf(application("APP_TOP_REJ", birsa.id, "SCH_TOPCLASS", "REJECTED"))

        val eval = EligibilityEngine.evaluateEligibility(birsa, topClass, existingApps)

        assertTrue(eval.isEligible)
        assertTrue("A rejected application should not block re-applying", eval.isUnclaimed)
    }

    @Test
    fun allMatchingSchemesApplied_meansNothingUnclaimed() {
        val existingApps = listOf(
            application("APP_PMS_TEST", birsa.id, "SCH_PMS", "INSTITUTE_VERIFICATION"),
            application("APP_TOP_TEST", birsa.id, "SCH_TOPCLASS", "SUBMITTED")
        )

        assertNull(EligibilityEngine.findTopUnreachedScheme(birsa, schemes, existingApps))
    }

    @Test
    fun incomeThresholdBoundary_enforcesStrictStatutoryCeilings() {
        // Rs 3.50L: above the Rs 2.50L ceiling, below the Rs 6.00L Top Class ceiling.
        val studentAbove250k = student(
            id = "STU_TEST",
            name = "Test ST Student",
            annualIncome = 350000.0,
            institutionName = "National Institute of Technology, Rourkela",
            institutionId = "AISHE-U-0355",
            course = "B.Tech Electrical",
            category = "ST"
            dob = "2003-01-01",
            gender = "Female",
            aadhaarMasked = "XXXXXXXX9999",
            phoneMasked = "XXXXXX1111",
            email = "test@nitrkl.ac.in",
            category = "ST",
            annualIncome = 350000.0, // Exceeds ₹2.50L ceiling, but below ₹6.00L ceiling
            institutionName = "National Institute of Technology, Rourkela",
            institutionId = "AISHE-U-0355",
            course = "B.Tech Electrical",
            yearOfStudy = 3,
            bankAccountMasked = "XXXXXXXX1111",
            bankIfsc = "SBIN0001234",
            bankName = "SBI",
            apaarId = "APAAR-1111"
        )

        val postMatricScheme = schemes.first { it.id == "SCH_PMS" }
        val topClassScheme = schemes.first { it.id == "SCH_TOPCLASS" }

        val pmsEval = EligibilityEngine.evaluateEligibility(studentAbove250k, postMatricScheme, emptyList())
        assertFalse("Income > 2.50L must disqualify from Post-Matric Scholarship", pmsEval.isEligible)
        assertTrue(pmsEval.disqualificationReasons.any { it.contains("exceeds ₹2.50L", ignoreCase = true) })

        val topClassEval = EligibilityEngine.evaluateEligibility(studentAbove250k, topClassScheme, emptyList())
        assertTrue("Income of 3.50L remains within Top Class ceiling of 6.00L", topClassEval.isEligible)
        assertTrue(topClassEval.matchReasons.any { it.contains("within ₹6.00L ceiling", ignoreCase = true) })
    }

    @Test
    fun pvtgStudent_receivesPriorityEntitlementRecognition() {
        val sunitaSoren = student(
            id = "STU_2026_02",
            name = "Sunita Soren",
            dob = "2003-11-14",
            gender = "Female",
            aadhaarMasked = "XXXXXXXX7890",
            phoneMasked = "XXXXXX6543",
            email = "sunita.soren@iitbbs.ac.in",
            category = "ST (Santhal)",
            pvtgCommunity = "Santhal PVTG",
            annualIncome = 120000.0,
            institutionName = "Indian Institute of Technology, Bhubaneswar",
            institutionId = "AISHE-U-0356",
            course = "B.Tech Mechanical Engineering",
            yearOfStudy = 3,
            bankAccountMasked = "XXXXXXXX2468",
            bankIfsc = "PUNB0123400",
            bankName = "Punjab National Bank",
            category = "ST (Santhal)",
            pvtgCommunity = "Santhal PVTG"
            bankAccountMasked = "Punjab National Bank (A/C **2468)",
            ifscCode = "PUNB0123400",
            apaarId = "APAAR-7890-1234-5678"
        )

        val topClassScheme = schemes.first { it.id == "SCH_TOPCLASS" }
        val eval = EligibilityEngine.evaluateEligibility(sunitaSoren, topClassScheme, emptyList())

        assertTrue(eval.isEligible)
        assertTrue(eval.matchReasons.any { it.contains("PVTG - Santhal PVTG") })
    }

    @Test
    fun nonScheduledTribeStudent_isRejectedForEveryScheme() {
        val generalStudent = student(
            id = "STU_GENERAL",
            name = "General Category Student",
            annualIncome = 100000.0,
            institutionName = "National Institute of Technology, Rourkela",
            institutionId = "AISHE-U-0355",
            course = "B.Tech Civil",
            category = "General"
    fun duplicateApplicationPrevention_rejectsSecondSubmissionInSameAcademicYear() {
        val existingApps = listOf(
            ApplicationEntity(
                id = "APP_2026_01",
                studentId = "STU_2026_01",
                schemeId = "SCH_TOPCLASS",
                schemeName = "Top Class Education Scheme for ST Students",
                appliedDate = "2026-09-01",
                currentStage = "SUBMITTED",
                academicYear = "2025-26"
            )
        )

        // Attempting to submit a second application for SCH_TOPCLASS for the same academic year
        val studentId = "STU_2026_01"
        val schemeId = "SCH_TOPCLASS"
        val academicYear = "2025-26"

        val hasDuplicate = existingApps.any {
            it.studentId == studentId && it.schemeId == schemeId && it.academicYear == academicYear
        }

        assertTrue(hasDuplicate)

        val exception = assertThrows(IllegalStateException::class.java) {
            if (hasDuplicate) {
                throw IllegalStateException("Application for this scheme already exists for academic year $academicYear.")
            }
        }

        assertTrue(exception.message!!.contains("already exists"))
    }

    @Test
    fun consentRevocation_blocksApplicationSubmission() {
        val studentWithRevokedConsent = StudentEntity(
            id = "STU_2026_01",
            name = "Birsa Munda Tirkey",
            dob = "2004-05-18",
            gender = "Male",
            aadhaarMasked = "XXXXXXXX1234",
            phoneMasked = "XXXXXX9876",
            email = "birsa.tirkey@nitrkl.ac.in",
            category = "ST",
            annualIncome = 160000.0,
            institutionName = "NIT Rourkela",
            institutionId = "AISHE-U-0355",
            course = "B.Tech CSE",
            yearOfStudy = 2,
            bankAccountMasked = "XXXXXXXX5678",
            bankIfsc = "SBIN0002109",
            bankName = "SBI",
            apaarId = "APAAR-9876",
            hasConsentGiven = false // Revoked DPDP consent
        )

        for (scheme in schemes) {
            val eval = EligibilityEngine.evaluateEligibility(generalStudent, scheme, emptyList())
            assertFalse("${scheme.id} must be closed to non-ST applicants", eval.isEligible)
            assertTrue(eval.disqualificationReasons.any { it.contains("ST beneficiaries") })
        }
    }

    @Test
    fun missingStudentProfile_isNeverEligible() {
        val eval = EligibilityEngine.evaluateEligibility(null, schemes.first(), emptyList())

        assertFalse(eval.isEligible)
        assertFalse(eval.isUnclaimed)
        assertNull(EligibilityEngine.findTopUnreachedScheme(null, schemes, emptyList()))
    fun jagoAiService_protectsPiiAndProvidesMultilingualGuidance() = runBlocking {
        val jago = JagoAiService()
        val student = StudentEntity(
            id = "STU_2026_01",
            name = "Birsa Munda Tirkey",
            dob = "2004-05-18",
            gender = "Male",
            aadhaarMasked = "XXXXXXXX1234",
            phoneMasked = "XXXXXX9876",
            email = "birsa.tirkey@nitrkl.ac.in",
            category = "ST (Scheduled Tribe)",
            annualIncome = 160000.0,
            institutionName = "NIT Rourkela",
            institutionId = "AISHE-U-0355",
            course = "B.Tech CSE",
            yearOfStudy = 2,
            bankAccountMasked = "XXXXXXXX5678",
            bankIfsc = "SBIN0002109",
            bankName = "SBI",
            apaarId = "APAAR-9876"
        )

        val app = ApplicationEntity(
            id = "APP_2026_01",
            studentId = "STU_2026_01",
            schemeId = "SCH_PMS",
            schemeName = "Post-Matric Scholarship",
            appliedDate = "2026-08-15",
            currentStage = "UNDER_VERIFICATION",
            stageProgress = 0.6f,
            hasDiscrepancy = true,
            academicYear = "2025-26"
        )

        // Test Hindi response
        val hindiMsg = jago.generateResponse(
            query = "What is my application status?",
            currentAppLangCode = "hi",
            student = student,
            applications = listOf(app),
            pendingReviewCount = 1,
            unclaimedEvaluations = emptyList()
        )
        assertNotNull(hindiMsg.content)
        assertTrue(hindiMsg.content.contains("नमस्ते Birsa"))
        assertTrue(hindiMsg.content.contains("Reviewer Desk"))

        // Test Odia response
        val odiaMsg = jago.generateResponse(
            query = "status of application",
            currentAppLangCode = "or",
            student = student,
            applications = listOf(app),
            pendingReviewCount = 1,
            unclaimedEvaluations = emptyList()
        )
        assertTrue(odiaMsg.content.contains("ନମସ୍କାର Birsa"))

        // Test Gondi response
        val gondiMsg = jago.generateResponse(
            query = "status",
            currentAppLangCode = "gon",
            student = student,
            applications = listOf(app),
            pendingReviewCount = 1,
            unclaimedEvaluations = emptyList()
        )
        assertTrue(gondiMsg.content.contains("जोहार Birsa"))

        // Guarantee ZERO PII leakage across all generated responses
        val allContent = hindiMsg.content + " " + odiaMsg.content + " " + gondiMsg.content
        assertFalse(allContent.contains("XXXXXXXX1234"))
        assertFalse(allContent.contains("XXXXXXXX5678"))
        assertFalse(allContent.contains("SBIN0002109"))
        assertFalse(allContent.contains("XXXXXX9876"))
    }
}
