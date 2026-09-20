package com.example

import com.example.data.ai.JagoAiService
import com.example.data.local.SeedData
import com.example.data.model.ApplicationEntity
import com.example.domain.EligibilityEvaluation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the local (no Gemini / Firebase configured) fallback of [JagoAiService].
 *
 * Runs under Robolectric because the service calls android.util.Log when it falls back,
 * and plain JVM unit tests cannot call android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JagoAiServiceTest {

    private val jago = JagoAiService()
    private val birsa = SeedData.students[0]

    private fun flaggedApplication() = ApplicationEntity(
        id = "APP_TEST_01",
        studentId = birsa.id,
        schemeId = "SCH_PMS",
        schemeCode = "POST-MATRIC-ST",
        schemeName = "Post-Matric Scholarship",
        academicYear = "2026-27",
        currentStage = "INSTITUTE_VERIFICATION",
        statusText = "Under review",
        appliedDate = "15 Aug 2026",
        lastUpdated = "15 Aug 2026",
        hasDiscrepancy = true
    )

    private fun topClassEvaluation() = EligibilityEvaluation(
        schemeId = "SCH_TOPCLASS",
        schemeCode = "TOP-CLASS-ST",
        schemeName = "Top Class Education Scheme",
        isEligible = true,
        matchReasons = emptyList(),
        disqualificationReasons = emptyList(),
        estimatedGrant = "Up to 2 Lakh",
        isUnclaimed = true
    )

    @Test
    fun statusQuery_isAnsweredInHindiOdiaAndGondi() = runBlocking {
        val app = flaggedApplication()

        val hindi = jago.generateResponse("What is my application status?", "hi", birsa, listOf(app), pendingReviewCount = 1, unclaimedEvaluations = emptyList())
        assertTrue(hindi.content.contains("नमस्ते Birsa"))
        assertTrue(hindi.content.contains("Reviewer Desk"))

        val odia = jago.generateResponse("status of application", "or", birsa, listOf(app), pendingReviewCount = 1, unclaimedEvaluations = emptyList())
        assertTrue(odia.content.contains("ନମସ୍କାର Birsa"))

        val gondi = jago.generateResponse("status", "gon", birsa, listOf(app), pendingReviewCount = 1, unclaimedEvaluations = emptyList())
        assertTrue(gondi.content.contains("जोहार Birsa"))
    }

    @Test
    fun englishStatusQuery_mentionsReviewerDeskOnlyWhenSomethingIsFlagged() = runBlocking {
        val flagged = jago.generateResponse("status", "en", birsa, listOf(flaggedApplication()), pendingReviewCount = 1, unclaimedEvaluations = emptyList())
        assertTrue(flagged.content.contains("Reviewer Desk"))

        val clean = jago.generateResponse(
            "status", "en", birsa, listOf(flaggedApplication().copy(hasDiscrepancy = false)), pendingReviewCount = 0, unclaimedEvaluations = emptyList()
        )
        assertFalse(clean.content.contains("Reviewer Desk"))
        assertTrue(clean.content.contains("no pending discrepancies"))
    }

    @Test
    fun eligibilityQuery_surfacesTheTopUnclaimedScheme() = runBlocking {
        val reply = jago.generateResponse(
            "Am I eligible for Top Class?", "en", birsa, emptyList(), pendingReviewCount = 0, unclaimedEvaluations = listOf(topClassEvaluation())
        )
        assertTrue(reply.content.contains("Top Class Education Scheme"))
    }

    @Test
    fun responses_neverLeakAadhaarIfscOrMobile() = runBlocking {
        val app = flaggedApplication()
        val queries = listOf("status", "Am I eligible?", "which documents are linked", "hello")
        val languages = listOf("en", "hi", "or", "gon")

        val allContent = buildString {
            for (lang in languages) {
                for (q in queries) {
                    append(jago.generateResponse(q, lang, birsa, listOf(app), pendingReviewCount = 1, unclaimedEvaluations = listOf(topClassEvaluation())).content)
                    append(' ')
                }
            }
        }

        assertFalse(allContent.contains(birsa.aadhaarMasked))
        assertFalse(allContent.contains(birsa.ifscCode))
        assertFalse(allContent.contains(birsa.mobile))
        assertFalse(allContent.contains(birsa.apaarId))
    }

    @Test
    fun unknownQuery_fallsBackToIntroductionWithQuickChips() = runBlocking {
        val reply = jago.generateResponse("hello", "en", birsa, emptyList(), pendingReviewCount = 0, unclaimedEvaluations = emptyList())

        assertTrue(reply.content.contains("JAGO"))
        assertTrue(reply.quickChips.isNotEmpty())
    }
}
