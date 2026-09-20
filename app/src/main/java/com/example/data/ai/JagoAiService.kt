package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import com.example.domain.EligibilityEvaluation
import com.example.data.model.ApplicationEntity
import com.example.data.model.DisbursementEntity
import com.example.data.model.JagoMessage
import com.example.data.model.StudentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Robust Multilingual AI Service for JAGO (Unified Tribal Scholarship Assistant).
 *
 * Architecture & Guarantees:
 * - Direct REST call to Gemini 2.5 Flash using BuildConfig.GEMINI_API_KEY with 8-second timeout.
 * - Zero UI thread blocking (Dispatchers.IO).
 * - Multi-layered privacy: Excludes Aadhaar, full bank account numbers, IFSC codes, and mobile numbers.
 * - Dynamic system instructions on every call including localized persona and compact JSON state snapshot.
 * - Automatic script detection (Devanagari, Odia, Latin, and Romanized Indic keywords).
 * - Resilient 9-intent local multilingual fallback for offline/no-network/quota conditions.
 */
class JagoAiService {

    companion object {
        private const val TAG = "JagoAiService"
        private const val GEMINI_MODEL = "gemini-2.5-flash"
        private const val API_TIMEOUT_MS = 8000
    }

    enum class DetectedScript {
        DEVANAGARI,
        ODIA,
        LATIN
    }

    data class LanguageMatch(
        val detectedLanguageCode: String,
        val detectedLanguageName: String,
        val offersSwitchFrom: String? = null
    )

    suspend fun generateResponse(
        query: String,
        currentAppLangCode: String,
        student: StudentEntity?,
        applications: List<ApplicationEntity>,
        disbursements: List<DisbursementEntity> = emptyList(),
        pendingReviewCount: Int = 0,
        unclaimedEvaluations: List<EligibilityEvaluation> = emptyList()
    ): JagoMessage = withContext(Dispatchers.IO) {
        val sanitizedQuery = query.trim()
        if (sanitizedQuery.isEmpty()) {
            return@withContext getEmptyQueryResponse(currentAppLangCode)
        }

        // Detect user's typed language/script
        val langMatch = detectUserLanguage(sanitizedQuery, currentAppLangCode)
        val targetLangCode = langMatch.detectedLanguageCode

        // 1. Attempt direct Gemini API call
        val apiKey = getApiKey()
        if (!apiKey.isNullOrBlank() && !apiKey.contains("TODO", ignoreCase = true) && !apiKey.contains("PLACEHOLDER", ignoreCase = true)) {
            val liveResponse = tryLiveGeminiCall(
                query = sanitizedQuery,
                targetLangCode = targetLangCode,
                appLangCode = currentAppLangCode,
                langMatch = langMatch,
                student = student,
                applications = applications,
                disbursements = disbursements,
                pendingReviewCount = pendingReviewCount,
                unclaimedEvaluations = unclaimedEvaluations,
                apiKey = apiKey
            )
            if (liveResponse != null) {
                return@withContext liveResponse
            }
        }

        // 2. Multilingual local intent engine fallback
        return@withContext generateLocalMultilingualResponse(
            query = sanitizedQuery,
            targetLangCode = targetLangCode,
            currentAppLangCode = currentAppLangCode,
            langMatch = langMatch,
            student = student,
            applications = applications,
            disbursements = disbursements,
            pendingReviewCount = pendingReviewCount,
            unclaimedEvaluations = unclaimedEvaluations
        )
    }

    private fun getApiKey(): String? {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isNullOrBlank()) null else key
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun tryLiveGeminiCall(
        query: String,
        targetLangCode: String,
        appLangCode: String,
        langMatch: LanguageMatch,
        student: StudentEntity?,
        applications: List<ApplicationEntity>,
        disbursements: List<DisbursementEntity>,
        pendingReviewCount: Int,
        unclaimedEvaluations: List<EligibilityEvaluation>,
        apiKey: String
    ): JagoMessage? {
        return try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent?key=$apiKey"
            val systemInstruction = buildSystemInstruction(
                targetLangCode = targetLangCode,
                student = student,
                applications = applications,
                disbursements = disbursements,
                pendingReviewCount = pendingReviewCount,
                unclaimedEvaluations = unclaimedEvaluations
            )

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val messageObj = JSONObject().apply {
                        put("role", "user")
                        val partsArray = JSONArray().apply {
                            put(JSONObject().put("text", query))
                        }
                        put("parts", partsArray)
                    }
                    put(messageObj)
                }
                put("contents", contentsArray)

                val systemInstructionObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    }
                    put("parts", partsArray)
                }
                put("systemInstruction", systemInstructionObj)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 250)
                }
                put("generationConfig", generationConfig)
            }

            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = API_TIMEOUT_MS
                readTimeout = API_TIMEOUT_MS
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val responseJson = JSONObject(responseText)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        var text = parts.getJSONObject(0).optString("text", "").trim()
                        if (text.isNotBlank()) {
                            // Append language switch offer if user typed in a different language
                            if (langMatch.offersSwitchFrom != null) {
                                val switchOffer = getLanguageSwitchOffer(langMatch.detectedLanguageCode, langMatch.detectedLanguageName)
                                text = "$text\n\n$switchOffer"
                            }

                            val chips = getQuickChipsForLanguage(targetLangCode, langMatch.offersSwitchFrom != null, langMatch.detectedLanguageName)
                            return JagoMessage(
                                sender = "JAGO",
                                content = text,
                                quickChips = chips
                            )
                        }
                    }
                }
            } else {
                val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it, "UTF-8")).use { reader -> reader.readText() } }
                Log.w(TAG, "Gemini API call returned HTTP $responseCode: $errorStream")
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Gemini API call failed: ${e.message}. Falling back to local engine.")
            null
        }
    }

    private fun buildSystemInstruction(
        targetLangCode: String,
        student: StudentEntity?,
        applications: List<ApplicationEntity>,
        disbursements: List<DisbursementEntity>,
        pendingReviewCount: Int,
        unclaimedEvaluations: List<EligibilityEvaluation>
    ): String {
        val studentSnapshot = JSONObject().apply {
            put("name", student?.name ?: "Student")
            put("category", student?.category ?: "ST")
            put("institution", student?.institutionName ?: "National Institute of Technology, Rourkela")
            put("course", student?.course ?: "B.Tech CSE")
            put("annualIncome", student?.annualIncome ?: 210000.0)
            put("isDigiLockerLinked", student?.isDigiLockerLinked ?: true)
        }

        val appsSnapshot = JSONArray().apply {
            applications.forEach { app ->
                put(JSONObject().apply {
                    put("scheme", app.schemeName)
                    put("stage", app.currentStage)
                    put("amount", app.sanctionedAmount)
                    put("hasDiscrepancy", app.hasDiscrepancy)
                })
            }
        }

        val stateSnapshot = JSONObject().apply {
            put("student", studentSnapshot)
            put("applications", appsSnapshot)
            put("pendingReviewCount", pendingReviewCount)
            put("disbursementsCount", disbursements.size)
            put("unclaimedCount", unclaimedEvaluations.count { it.isUnclaimed })
        }

        val languageDirective = when (targetLangCode) {
            "hi" -> "Reply ONLY in Hindi using standard Devanagari script (हिन्दी)."
            "or" -> "Reply ONLY in Odia using Odia script (ଓଡ଼ିଆ)."
            "gon" -> "Reply in Gondi (or simple Hindi in Devanagari) starting with 'सेवा जोहार!'."
            else -> "Reply in simple, clear English."
        }

        return """
            You are JAGO, the friendly and knowledgeable AI Assistant for 'Ekikrit' (एकीकृत), the Unified Tribal Scholarship App by the Ministry of Tribal Affairs (SIH26238).
            
            GUIDELINES:
            1. $languageDirective
            2. Strict length: Stay under 80 words. Be concise, direct, and supportive.
            3. Use only facts, numbers, and dates present in the current state snapshot. Never fabricate fake schemes, dates, or guarantee instant sanction.
            4. Clarify that this application is a prototype demonstrating single-window DBT, DigiLocker reusable credentials, and zero paperwork.
            
            CURRENT COMPACT STATE:
            ${stateSnapshot.toString()}
        """.trimIndent()
    }

    // =====================================================================
    // SCRIPT & LANGUAGE DETECTION
    // =====================================================================

    private fun detectUserLanguage(text: String, currentAppLangCode: String): LanguageMatch {
        var devanagariCount = 0
        var odiaCount = 0
        var latinCount = 0

        for (char in text) {
            val code = char.code
            when {
                code in 0x0900..0x097F -> devanagariCount++
                code in 0x0B00..0x0B7F -> odiaCount++
                (code in 0x0041..0x005A) || (code in 0x0061..0x007A) -> latinCount++
            }
        }

        if (odiaCount > devanagariCount && odiaCount > 2) {
            return LanguageMatch("or", "ଓଡ଼ିଆ", if (currentAppLangCode != "or") currentAppLangCode else null)
        }

        if (devanagariCount > 2) {
            // Check for Gondi specific greetings in Devanagari
            val lower = text.lowercase(Locale.ROOT)
            return if (lower.contains("सेवा जोहार") || lower.contains("जोहार") || lower.contains("मावा") || lower.contains("मंदा")) {
                LanguageMatch("gon", "गोंडी (Beta)", if (currentAppLangCode != "gon") currentAppLangCode else null)
            } else {
                LanguageMatch("hi", "हिन्दी", if (currentAppLangCode != "hi") currentAppLangCode else null)
            }
        }

        // Check for Romanized Hindi / Odia
        val lower = text.lowercase(Locale.ROOT)
        val romanizedHindiKeywords = listOf("namaste", "johar", "kya", "kab", "kaise", "milega", "paisa", "yojana", "chahiye", "karo", "hai", "nahi", "kyu", "kyun", "hoga")
        val romanizedOdiaKeywords = listOf("namaskar", "juhar", "kete", "taka", "milba", "asiba", "kipari", "karibi", "achhi", "nahin", "kahinki")

        val matchHindi = romanizedHindiKeywords.count { lower.contains(it) }
        val matchOdia = romanizedOdiaKeywords.count { lower.contains(it) }

        if (matchOdia >= 2) {
            return LanguageMatch("or", "ଓଡ଼ିଆ", if (currentAppLangCode != "or") currentAppLangCode else null)
        }
        if (matchHindi >= 2) {
            return LanguageMatch("hi", "हिन्दी", if (currentAppLangCode != "hi") currentAppLangCode else null)
        }

        return LanguageMatch(currentAppLangCode, getLanguageDisplayName(currentAppLangCode), null)
    }

    private fun getLanguageDisplayName(code: String): String {
        return when (code) {
            "hi" -> "हिन्दी"
            "or" -> "ଓଡ଼ିଆ"
            "gon" -> "गोंडी (Beta)"
            else -> "English"
        }
    }

    private fun getLanguageSwitchOffer(langCode: String, langName: String): String {
        return when (langCode) {
            "hi" -> "💡 आपने हिन्दी में प्रश्न पूछा है। क्या आप ऐप की भाषा 'हिन्दी' में बदलना चाहते हैं?"
            "or" -> "💡 ଆପଣ ଓଡ଼ିଆରେ ପ୍ରଶ୍ନ ପଚାରିଛନ୍ତି। ଆପଣ ଆପ୍ ଭାଷା 'ଓଡ଼ିଆ'କୁ ବଦଳାଇବାକୁ ଚାହାଁନ୍ତି କି?"
            "gon" -> "💡 मीर गोंडी ते सवाल पूछे कीती। मीर ऐप भाषा 'गोंडी' ते बदला कीयाना चाह्यात का?"
            else -> "💡 You asked in English. Would you like to switch the app language to English?"
        }
    }

    // =====================================================================
    // 9-INTENT LOCAL MULTILINGUAL INTENT ENGINE
    // =====================================================================

    enum class UserIntent {
        GREETING,
        PENDING_ACTIONS,
        ELIGIBILITY_SCHEMES,
        DOCUMENTS_DIGILOCKER,
        DBT_PAYMENT_STATUS,
        INCOME_FLAG_REASON,
        HOW_TO_APPLY,
        OFFLINE_MODE,
        HELP_UNKNOWN
    }

    fun classifyIntent(query: String): UserIntent {
        val q = query.lowercase(Locale.ROOT)

        // 1. Income Flag / Discrepancy Reason
        if (q.contains("why was income flagged") || q.contains("income flag") || q.contains("discrepancy") ||
            q.contains("variance") || q.contains("आय में अंतर") || q.contains("आय विसंगति") ||
            q.contains("ଆୟରେ ପାର୍ଥକ୍ୟ") || q.contains("ଆୟ ବ୍ୟତିକ୍ରମ") || q.contains("आय ते अंतर") ||
            q.contains("income mismatch") || q.contains("income kyu") || q.contains("income kyo") ||
            q.contains("statutory") || q.contains("tolerance") || q.contains("review") ||
            q.contains("समीक्षा") || q.contains("सहिष्णुता") || q.contains("सहनशीलता") ||
            q.contains("ସମୀକ୍ଷା") || q.contains("ସହନଶୀଳତା") || q.contains("difference") ||
            q.contains("mismatch") || q.contains("अंतर") || q.contains("ଭିନ୍ନତା") || q.contains("ତ୍ରୁଟି") ||
            q.contains("फ्लैग") || q.contains("ଫ୍ଲାଗ୍")
        ) {
            return UserIntent.INCOME_FLAG_REASON
        }

        // 2. DBT / Payment Status
        if (q.contains("dbt") || q.contains("payment") || q.contains("disburs") || q.contains("bank") ||
            q.contains("paisa") || q.contains("tanka") || q.contains("पैसा") || q.contains("भुगतान") ||
            q.contains("ଟଙ୍କା") || q.contains("पैसे कब") || q.contains("money") || q.contains("utr") ||
            q.contains("pfms") || q.contains("credit") || q.contains("बैंक") || q.contains("ବ୍ୟାଙ୍କ୍") ||
            q.contains("खाता") || q.contains("ଖାତା") || q.contains("राशि") || q.contains("ରାଶି")
        ) {
            return UserIntent.DBT_PAYMENT_STATUS
        }

        // 3. Documents / DigiLocker
        if (q.contains("document") || q.contains("digilocker") || q.contains("wallet") || q.contains("caste") ||
            q.contains("certificate") || q.contains("दस्तावेज़") || q.contains("प्रमाण पत्र") ||
            q.contains("ଦସ୍ତାବିଜ") || q.contains("ପ୍ରମାଣପତ୍ର") || q.contains("कागजात") || q.contains("upload") ||
            q.contains("credential") || q.contains("reused") || q.contains("reuse") || q.contains("କାଗଜପତ୍ର")
        ) {
            return UserIntent.DOCUMENTS_DIGILOCKER
        }

        // 4. Pending Actions / Application Status
        if (q.contains("pending") || q.contains("status") || q.contains("needs attention") ||
            q.contains("क्या बाकी है") || q.contains("लंबित") || q.contains("स्थिति") || q.contains("କଣ ବାକି") ||
            q.contains("ସ୍ଥିତି") || q.contains("का बाकी") || q.contains("stage") || q.contains("progress") ||
            q.contains("action") || q.contains("action required") || q.contains("प्रगति") || q.contains("ପ୍ରଗତି") ||
            q.contains("कार्रवाई") || q.contains("कार्रवाई जरूरी") || q.contains("ପେଣ୍ଡିଂ") ||
            q.contains("କାର୍ଯ୍ୟାନୁଷ୍ଠାନ") || q.contains("का कीना") || q.contains("कीना मंदा") || q.contains("पेंडिंग")
        ) {
            return UserIntent.PENDING_ACTIONS
        }

        // 5. How to Apply / 1-Click
        if (q.contains("how to apply") || q.contains("1-click") || q.contains("apply") || q.contains("आवेदन कैसे") ||
            q.contains("ଆବେଦନ କିପରି") || q.contains("आवेदन कसं") || q.contains("form") || q.contains("wizard") ||
            q.contains("submit") || q.contains("application") || q.contains("आवेदन") || q.contains("ଆବେଦନ") ||
            q.contains("जमा") || q.contains("ଦାଖଲ") || q.contains("भरें") || q.contains("ପୂରଣ") ||
            q.contains("फॉर्म") || q.contains("ଫର୍ମ")
        ) {
            return UserIntent.HOW_TO_APPLY
        }

        // 6. Eligibility / Which Schemes
        if (q.contains("eligib") || q.contains("which scheme") || q.contains("top class") || q.contains("fellowship") ||
            q.contains("overseas") || q.contains("पात्र") || q.contains("योजना") || q.contains("ଯୋଗ୍ୟ") ||
            q.contains("ଯୋଜନା") || q.contains("eligible") || q.contains("match") || q.contains("टॉप क्लास") ||
            q.contains("ଟପ୍ କ୍ଲାସ୍") || q.contains("छात्रवृत्ति") || q.contains("ଛାତ୍ରବୃତ୍ତି") || q.contains("स्कॉलरशिप")
        ) {
            return UserIntent.ELIGIBILITY_SCHEMES
        }

        // 7. Offline Mode
        if (q.contains("offline") || q.contains("no internet") || q.contains("नेटवर्क") ||
            q.contains("ऑफलाइन") || q.contains("ଅଫଲାଇନ୍") || q.contains("sync") || q.contains("airplane") ||
            q.contains("इंटरनेट") || q.contains("ଇଣ୍ଟରନେଟ୍") || q.contains("नेट") || q.contains("ନେଟ")
        ) {
            return UserIntent.OFFLINE_MODE
        }

        // 8. Greeting
        if (q.contains("hi") || q.contains("hello") || q.contains("hey") || q.contains("namaste") ||
            q.contains("johar") || q.contains("pranam") || q.contains("नमस्ते") || q.contains("नमस्कार") ||
            q.contains("ନମସ୍କାର") || q.contains("ଜୁହାର") || q.contains("जोहार") || q.contains("सेवा जोहार") ||
            q.contains("morning") || q.contains("evening") || q.contains("afternoon") || q.contains("greeting") ||
            q.contains("राम राम") || q.contains("राम") || q.contains("ପ୍ରଣାମ") || q.contains("प्रणाम") ||
            q.contains("हेलो") || q.contains("ହେଲୋ") || q.contains("ହାଏ")
        ) {
            return UserIntent.GREETING
        }

        return UserIntent.HELP_UNKNOWN
    }

    private fun generateLocalMultilingualResponse(
        query: String,
        targetLangCode: String,
        currentAppLangCode: String,
        langMatch: LanguageMatch,
        student: StudentEntity?,
        applications: List<ApplicationEntity>,
        disbursements: List<DisbursementEntity>,
        pendingReviewCount: Int,
        unclaimedEvaluations: List<EligibilityEvaluation>
    ): JagoMessage {
        val intent = classifyIntent(query)
        val firstName = student?.name?.split(" ")?.firstOrNull() ?: "Friend"
        val hasFlaggedApp = applications.any { it.hasDiscrepancy } || pendingReviewCount > 0
        val topUnclaimed = unclaimedEvaluations.firstOrNull { it.isUnclaimed }
        val maskedBank = student?.bankAccountMasked ?: "Aadhaar-seeded Bank Account"

        val baseResponse: String = when (intent) {
            UserIntent.GREETING -> when (targetLangCode) {
                "hi" -> "नमस्ते $firstName! मैं जागो (JAGO) हूँ, आपका एकीकृत जनजातीय छात्रवृत्ति सहायक। आप अपनी योजनाओं, दस्तावेज़ों, डीबीटी भुगतान या पात्रता के बारे में कुछ भी पूछ सकते हैं।"
                "or" -> "ନମସ୍କାର $firstName! ମୁଁ ଜାଗୋ (JAGO), ଆପଣଙ୍କ ଏକୀକୃତ ଜନଜାତି ଛାତ୍ରବୃତ୍ତି ସହାୟକ। ଆପଣଙ୍କ ଆବେଦନ, DBT ପେମେଣ୍ଟ ବା ଯୋଗ୍ୟତା ବିଷୟରେ ଯେକୌଣସି ପ୍ରଶ୍ନ ପଚାରନ୍ତୁ।"
                "gon" -> "सेवा जोहार $firstName! नना जागो (JAGO) आन, मावा एकीकृत जनजातीय छात्रवृत्ति सहायक। मीर योजना, दस्तावेज़, डीबीटी पैसा या पात्रता बारे ते पूछ सक्यात।"
                else -> "Hello $firstName! I am JAGO, your Unified Tribal Scholarship Assistant. How can I help you today with your applications, DigiLocker documents, DBT tracking, or eligibility?"
            }

            UserIntent.INCOME_FLAG_REASON -> when (targetLangCode) {
                "hi" -> "आपके आवेदन में राज्य ई-डिस्ट्रिक्ट रिकॉर्ड (₹2,25,000) और घोषित आय (₹2,10,000) में ₹15,000 का अंतर है। यह 10% की वैधानिक सहिष्णुता सीमा के भीतर है। अधिकारी डेस्क पर इसकी समीक्षा चल रही है और आपका आवेदन रुका नहीं है।"
                "or" -> "ଆପଣଙ୍କ ଆବେଦନରେ ରାଜ୍ୟ ଡାଟାବେସ୍ (₹2,25,000) ଓ ଘୋଷିତ ଆୟ (₹2,10,000) ମଧ୍ୟରେ ₹15,000 ର ପାର୍ଥକ୍ୟ ଅଛି। ଏହା 10% ସହନଶୀଳତା ସୀମା ମଧ୍ୟରେ ଥିବାରୁ ଅଧିକାରୀ ସ୍ତରରେ ସମାଧାନ ହେଉଛି।"
                "gon" -> "मावा आवेदन ते सरकारी रिकॉर्ड (₹2,25,000) व घोषित आय (₹2,10,000) ते ₹15,000 ना अंतर मिला। इद्द 10% नियम भीतर मंदा। अधिकारी डेस्क ते जांच चालू मंदा, अर्जी रुकी सिल्ले।"
                else -> "An income variance of ₹15,000 was detected between state records (₹2,25,000) and declared income (₹2,10,000). It is within the 10% statutory tolerance band and is being cleared by the District Reviewer without holding your application."
            }

            UserIntent.DBT_PAYMENT_STATUS -> when (targetLangCode) {
                "hi" -> "आपकी छात्रवृत्ति राशि सीधे आपके आधार-सीडेड बैंक खाते ($maskedBank) में PFMS के माध्यम से भेजी जाती है। डैशबोर्ड पर 'डीबीटी रेल' टैब से आप लाइव किश्त स्थिति देख सकते हैं।"
                "or" -> "ଆପଣଙ୍କ ଛାତ୍ରବୃତ୍ତି ରାଶି ସିଧାସଳଖ ଆପଣଙ୍କ ଆଧାର ସଂଯୁକ୍ତ ବ୍ୟାଙ୍କ ଖାତା ($maskedBank) କୁ PFMS ମାଧ୍ୟମରେ ପ୍ରଦାନ କରାଯାଏ। 'DBT ରେଳ' ଟ୍ୟାବରୁ ସ୍ଥିତି ଯାଞ୍ଚ କରନ୍ତୁ।"
                "gon" -> "मावा छात्रवृत्ति पैसा सीधा आधार-सीडेड बैंक खाता ($maskedBank) ते PFMS माध्यम से वाय। 'डीबीटी रेल' टैब ते लाइव स्थिति हुर्राट।"
                else -> "Your scholarship grant is transferred directly to your Aadhaar-seeded bank account ($maskedBank) via the PFMS-DBT rail. Track live tranches on the 'DBT Rail' tab."
            }

            UserIntent.PENDING_ACTIONS -> when (targetLangCode) {
                "hi" -> if (hasFlaggedApp) {
                    "नमस्ते $firstName! इस प्रोटोटाइप डेमो में नमूना रिकॉर्ड के आधार पर आय विचलन सिमुलेट किया गया है। यह गैर-अवरोधक अपवाद के रूप में Reviewer Desk कतार में भेजा गया है ताकि छात्र का आवेदन रुके बिना सहिष्णुता नियम का प्रदर्शन हो सके।"
                } else {
                    "नमस्ते $firstName! इस प्रोटोटाइप में आपके सभी डेमो आवेदन बिना किसी विचलन के सामान्य रूप से आगे बढ़ रहे हैं।"
                }
                "or" -> if (hasFlaggedApp) {
                    "ନମସ୍କାର $firstName! ଏହି ଡେମୋ ପ୍ରୋଟୋଟାଇପ୍ରେ ନମୁନା ତଥ୍ୟ ଆଧାରରେ ଆୟ ତାରତମ୍ୟ ସିମ୍ୟୁଲେଟ୍ କରାଯାଇଛି। ଛାତ୍ରଙ୍କୁ ନ ଅଟକାଇ Reviewer Desk ରେ ଏହାର ସମାଧାନ ପ୍ରକ୍ରିୟା ପ୍ରଦର୍ଶନ କରାଯାଉଛି।"
                } else {
                    "ନମସ୍କାର $firstName! ଏହି ପ୍ରୋଟୋଟାଇପ୍ରେ ଆପଣଙ୍କ ଡେମୋ ଆବେଦନରେ କୌଣସି ଅସୁବିଧା ନାହିଁ।"
                }
                "gon" -> if (hasFlaggedApp) {
                    "जोहार $firstName! इके डेमो प्रोटोटाइप ते नमूना आमदनी फरक जांच मंता। अर्जी बंद आयो, Reviewer Desk ते नियम जांच डेमो दिसंतोर।"
                } else {
                    "जोहार $firstName! प्रोटोटाइप ते नीवा सप्पो डेमो अर्जी ठीक-ठाक मंता। कूनो रुकावट सिला।"
                }
                else -> if (hasFlaggedApp) {
                    "Hello $firstName! In this demo prototype, an income variance is simulated against sample records. The prototype auto-routes this non-blocking exception to the Reviewer Desk queue under tolerance rules for demonstration without penalizing the applicant."
                } else {
                    "Great news, $firstName! In this prototype, your demo applications are progressing normally with no pending discrepancies flagged."
                }
            }

            UserIntent.DOCUMENTS_DIGILOCKER -> when (targetLangCode) {
                "hi" -> "एक बार डिजिलॉकर कनेक्ट करने पर आपके जाति, आय एवं शैक्षणिक प्रमाण पत्र सीधे डिजिटल मुहर के साथ प्राप्त होते हैं। यह 5 योजनाओं में बिना दोबारा अपलोड किए मान्य हैं।"
                "or" -> "ଡିଜିଲକର୍ ସଂଯୋଗ ହେଲେ ଆପଣଙ୍କ ଜାତି, ଆୟ ଓ ଶିକ୍ଷାଗତ ପ୍ରମାଣପତ୍ର ସ୍ୱତଃ ଯାଞ୍ଚ ହୋଇ ଆସିଥାଏ। ଏହା ସମସ୍ତ ୫ ଯୋଜନାରେ ପୁନଃ ବ୍ୟବହାର୍ଯ୍ୟ।"
                "gon" -> "एक बार डिजिलॉकर जोड़े आतके जाति, आय व मार्कशीट प्रमाण पत्र डिजिटल मुहर संगी वाय। सब 5 योजना ते बिना दोबारा अपलोड के मान्य मंदा।"
                else -> "With DigiLocker linked, your Caste, Income, and Marksheet credentials are cryptographically fetched and verified once, remaining 100% reusable across all 5 MoTA schemes."
            }

            UserIntent.HOW_TO_APPLY -> when (targetLangCode) {
                "hi" -> "डैशबोर्ड पर '1-क्लिक आवेदन' बटन दबाएं। विज़ार्ड आपकी पात्रता का मिलान करेगा, डिजिलॉकर से दस्तावेज़ जोड़ेगा, और डीपीडीपी सहमति के साथ तुरंत आवेदन जमा करेगा।"
                "or" -> "ଡ୍ୟାସବୋର୍ଡରେ '1-କ୍ଲିକ୍ ଆବେଦନ' ବଟନ୍ ଦବାନ୍ତୁ। ୱିଜାର୍ଡ ଆପଣଙ୍କ ଯୋଗ୍ୟତା ମେଳ କରି ଡିଜିଲକରରୁ ଦସ୍ତାବିଜ୍ ଯୋଡ଼ି ତୁରନ୍ତ ଦାଖଲ କରିବ।"
                "gon" -> "डैशबोर्ड ते '1-क्लिक आवेदन' बटन दबा कीम। विज़ार्ड पात्रता मिलान कींदू, डिजिलॉकर से दस्तावेज़ जोड़ कींदू व आवेदन जमा कींदू।"
                else -> "Tap the '1-Click Apply' action on the Dashboard. The wizard matches eligibility, pulls verified DigiLocker credentials, and submits with DPDP consent in seconds."
            }

            UserIntent.ELIGIBILITY_SCHEMES -> when (targetLangCode) {
                "hi" -> if (topUnclaimed != null) {
                    "आपके शैक्षणिक रिकॉर्ड (${student?.institutionName ?: "संस्थान"}) के अनुसार आप '${topUnclaimed.schemeName}' (${topUnclaimed.estimatedGrant}) के लिए पूर्णतः पात्र हैं।"
                } else {
                    "आप जनजातीय कार्य मंत्रालय की सभी 5 प्रमुख छात्रवृत्तियों की पात्रता '5 योजनाएं' टैब पर देख सकते हैं।"
                }
                "or" -> if (topUnclaimed != null) {
                    "ଆପଣଙ୍କ ପ୍ରୋଫାଇଲ୍ ଅନୁସାରେ ଆପଣ '${topUnclaimed.schemeName}' (${topUnclaimed.estimatedGrant}) ପାଇଁ ଯୋଗ୍ୟ ଅଟନ୍ତି।"
                } else {
                    "ଆପଣ '5 ଯୋଜନା' ଟ୍ୟାବରେ ସମସ୍ତ ୫ଟି ଜନଜାତି ଛାତ୍ରବୃତ୍ତିର ଯୋଗ୍ୟତା ଯାଞ୍ଚ କରିପାରିବେ।"
                }
                "gon" -> if (topUnclaimed != null) {
                    "मावा रिकॉर्ड लेका मीर '${topUnclaimed.schemeName}' (${topUnclaimed.estimatedGrant}) साठी पात्र आहात।"
                } else {
                    "मीर '5 योजना' टैब ते सब 5 छात्रवृत्ति पात्रता हुर्रे सक्यात।"
                }
                else -> if (topUnclaimed != null) {
                    "Based on your profile at ${student?.institutionName ?: "your institution"}, you are eligible for '${topUnclaimed.schemeName}' (${topUnclaimed.estimatedGrant})."
                } else {
                    "Explore all 5 Ministry of Tribal Affairs schemes and eligibility rules under the '5 Schemes' tab."
                }
            }

            UserIntent.OFFLINE_MODE -> when (targetLangCode) {
                "hi" -> "एकीकृत पूरी तरह ऑफलाइन-सक्षम है। इंटरनेट न होने पर भी आप आवेदन भर सकते हैं। ऑनलाइन होने पर सभी ड्राफ्ट स्वतः सिंक हो जाएंगे।"
                "or" -> "ଏକୀକୃତ ସମ୍ପୂର୍ଣ୍ଣ ଅଫଲାଇନ୍-ସକ୍ଷମ। ଇଣ୍ଟରନେଟ୍ ନଥିଲେ ମଧ୍ୟ ଆବେଦନ ସାଇତା ରହିବ ଓ ଅନଲାଇନ୍ ହେଲେ ସ୍ୱତଃ ସିଙ୍କ୍ ହେବ।"
                "gon" -> "एकीकृत पूरा ऑफलाइन काम कींदू। नेट सिल्ले आतके भी आवेदन सुरक्षित मंदा, ऑनलाइन आतके अपने आप सिंक आय।"
                else -> "Ekikrit is offline-first. Draft applications queue locally without internet and automatically synchronize with national registries once connectivity resumes."
            }

            UserIntent.HELP_UNKNOWN -> when (targetLangCode) {
                "hi" -> "मैं आपकी छात्रवृत्ति स्थिति, आय सहिष्णुता, डिजिलॉकर दस्तावेज़ या 1-क्लिक आवेदन में मदद कर सकता हूँ। कृपया नीचे दिए गए सुझावों में से चुनें।"
                "or" -> "ମୁଁ ଆପଣଙ୍କ ଛାତ୍ରବୃତ୍ତି ସ୍ଥିତି, ଆୟ ସହନଶୀଳତା, ଡିଜିଲକର୍ ବା 1-କ୍ଲିକ୍ ଆବେଦନରେ ସାହାଯ୍ୟ କରିପାରିବି। ତଳେ ଥିବା ବିକଳ୍ପ ଚୟନ କରନ୍ତୁ।"
                "gon" -> "नना छात्रवृत्ति स्थिति, आय समीक्षा, डिजिलॉकर दस्तावेज़ या 1-क्लिक आवेदन ते मदद की सकना। नीचे देवल सुझाव चुन कीम।"
                else -> "I can assist you with your application status, income discrepancy clearance, DigiLocker wallet, or 1-Click apply. Please pick one of the topics below."
            }
        }

        var finalText = baseResponse
        if (langMatch.offersSwitchFrom != null) {
            val switchOffer = getLanguageSwitchOffer(langMatch.detectedLanguageCode, langMatch.detectedLanguageName)
            finalText = "$finalText\n\n$switchOffer"
        }

        // Safe developer trace to remain state-aware in fallback mode for demo verification
        val dbStateTrace = " [Source of Truth: Applications: ${applications.joinToString { "${it.schemeCode}:${it.currentStage}" }} | Unclaimed: ${unclaimedEvaluations.joinToString { it.schemeName }}]"
        finalText = "$finalText\n\n$dbStateTrace"

        val chips = getQuickChipsForLanguage(targetLangCode, langMatch.offersSwitchFrom != null, langMatch.detectedLanguageName)
        return JagoMessage(
            sender = "JAGO",
            content = finalText,
            quickChips = chips
        )
    }

    private fun getEmptyQueryResponse(langCode: String): JagoMessage {
        val msg = when (langCode) {
            "hi" -> "कृपया अपना प्रश्न लिखें या नीचे दिए गए विकल्पों में से चुनें।"
            "or" -> "ଦୟାକରି ଆପଣଙ୍କ ପ୍ରଶ୍ନ ଲେଖନ୍ତୁ ବା ତଳେ ଥିବା ବିକଳ୍ପ ବାଛନ୍ତୁ।"
            "gon" -> "मावा सवाल लिख कीम या नीचे देवल विकल्प चुन कीम।"
            else -> "Please enter your question or tap one of the suggested topics below."
        }
        return JagoMessage(
            sender = "JAGO",
            content = msg,
            quickChips = getQuickChipsForLanguage(langCode, false, "")
        )
    }

    private fun getQuickChipsForLanguage(langCode: String, includeSwitchChip: Boolean, switchLangName: String): List<String> {
        val list = mutableListOf<String>()
        if (includeSwitchChip && switchLangName.isNotBlank()) {
            when (langCode) {
                "hi" -> list.add("भाषा 'हिन्दी' में बदलें")
                "or" -> list.add("ଭାଷା 'ଓଡ଼ିଆ'କୁ ବଦଳାନ୍ତୁ")
                "gon" -> list.add("भाषा 'गोंडी' ते बदला कीम")
                else -> list.add("Switch language to English")
            }
        }

        when (langCode) {
            "hi" -> {
                list.add("आवेदन की स्थिति")
                list.add("आय में अंतर क्यों है?")
                list.add("डीबीटी भुगतान कब आएगा?")
                list.add("1-क्लिक आवेदन कैसे करें?")
            }
            "or" -> {
                list.add("ଆବେଦନ ସ୍ଥିତି")
                list.add("ଆୟରେ ପାର୍ଥକ୍ୟ କାହିଁକି?")
                list.add("DBT ପେମେଣ୍ଟ କେବେ ଆସିବ?")
                list.add("1-କ୍ଲିକ୍ ଆବେଦନ କିପରି କରିବେ?")
            }
            "gon" -> {
                list.add("आवेदन ना स्थिति")
                list.add("आय ते अंतर क्यों मंदा?")
                list.add("डीबीटी पैसा कब वाय?")
                list.add("1-क्लिक आवेदन कसं कीना?")
            }
            else -> {
                list.add("Application Status")
                list.add("Why was income flagged?")
                list.add("When will amount disburse?")
                list.add("How to 1-Click Apply?")
            }
        }
        return list
    }
}
