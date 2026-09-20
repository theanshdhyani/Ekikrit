package com.example

import com.example.data.local.SeedData
import com.example.data.model.ApplicationEntity
import com.example.data.model.StudentEntity
import com.example.domain.EligibilityEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure-JVM unit tests for the scheme-matching rules in [EligibilityEngine]:
 * 1. ST category / PVTG recognition
 * 2. Course-level and premier-institute matching
 * 3. Income ceilings (Rs 2.50L for Pre/Post-Matric, Rs 6.00L for Top Class)
 * 4. Unclaimed-entitlement detection (Mangal Oraon vs Birsa Munda personas)
 */
class EkikritEligibilityAndOwnershipTest {

    private val schemes = SeedData.schemes

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
        dob = "15-08-2003",
        mobile = "+91 98765 43210",
        state = "Odisha",
        institutionId = institutionId,
        institutionName = institutionName,
        course = course,
        academicLevel = academicLevel,
        category = category,
        pvtgCommunity = pvtgCommunity,
        aadhaarMasked = "XXXX-XXXX-8924",
        annualIncome = annualIncome,
        apaarId = "APAAR-8839-4021-9920"
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
            annualIncome = 85000.0,
            institutionName = "Netarhat Residential School, Latehar",
            institutionId = "UDISE-201901001",
            course = "Class X (Secondary)",
            academicLevel = "SECONDARY"
        )

        val preMatricScheme = schemes.first { it.id == "SCH_PRE" }
        val topClassScheme = schemes.first { it.id == "SCH_TOPCLASS" }
        val fellowshipScheme = schemes.first { it.id == "SCH_NFST" }

        val preMatricEval = EligibilityEngine.evaluateEligibility(mangalOraon, preMatricScheme, emptyList())
        assertTrue("Mangal Oraon should be eligible for Pre-Matric", preMatricEval.isEligible)
        assertTrue(preMatricEval.matchReasons.any { it.contains("secondary education", ignoreCase = true) })

        val topClassEval = EligibilityEngine.evaluateEligibility(mangalOraon, topClassScheme, emptyList())
        assertFalse("Mangal Oraon should be ineligible for Top Class Scheme", topClassEval.isEligible)

        val fellowshipEval = EligibilityEngine.evaluateEligibility(mangalOraon, fellowshipScheme, emptyList())
        assertFalse("Mangal Oraon should be ineligible for National Fellowship", fellowshipEval.isEligible)
    }

    @Test
    fun birsaMunda_nitRourkela_matchesTopClass_asUnclaimedEntitlement() {
        // Birsa has only applied to Post-Matric so far.
        val existingApps = listOf(application("APP_PMS_TEST", birsa.id, "SCH_PMS", "INSTITUTE_VERIFICATION"))

        val topUnreached = EligibilityEngine.findTopUnreachedScheme(birsa, schemes, existingApps)

        assertNotNull("Birsa Munda should have an unclaimed entitlement detected", topUnreached)
        assertEquals("SCH_TOPCLASS", topUnreached!!.schemeId)
        assertTrue(topUnreached.isEligible)
        assertTrue(topUnreached.isUnclaimed)
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
        )

        val postMatricScheme = schemes.first { it.id == "SCH_PMS" }
        val topClassScheme = schemes.first { it.id == "SCH_TOPCLASS" }

        val pmsEval = EligibilityEngine.evaluateEligibility(studentAbove250k, postMatricScheme, emptyList())
        assertFalse("Income > 2.50L must disqualify from Post-Matric Scholarship", pmsEval.isEligible)

        val topClassEval = EligibilityEngine.evaluateEligibility(studentAbove250k, topClassScheme, emptyList())
        assertTrue("Income of 3.50L remains within Top Class ceiling of 6.00L", topClassEval.isEligible)
    }

    @Test
    fun pvtgStudent_receivesPriorityEntitlementRecognition() {
        val sunitaSoren = student(
            id = "STU_2026_02",
            name = "Sunita Soren",
            annualIncome = 120000.0,
            institutionName = "Indian Institute of Technology, Bhubaneswar",
            institutionId = "AISHE-U-0356",
            course = "B.Tech Mechanical Engineering",
            category = "ST (Santhal)",
            pvtgCommunity = "Santhal PVTG"
        )

        val topClassScheme = schemes.first { it.id == "SCH_TOPCLASS" }
        val eval = EligibilityEngine.evaluateEligibility(sunitaSoren, topClassScheme, emptyList())

        assertTrue(eval.isEligible)
        assertTrue(eval.matchReasons.any { it.contains("PVTG") })
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
        )

        for (scheme in schemes) {
            val eval = EligibilityEngine.evaluateEligibility(generalStudent, scheme, emptyList())
            assertFalse("${scheme.id} must be closed to non-ST applicants", eval.isEligible)
        }
    }

    @Test
    fun missingStudentProfile_isNeverEligible() {
        val eval = EligibilityEngine.evaluateEligibility(null, schemes.first(), emptyList())

        assertFalse(eval.isEligible)
        assertFalse(eval.isUnclaimed)
        assertNull(EligibilityEngine.findTopUnreachedScheme(null, schemes, emptyList()))
    }
}
