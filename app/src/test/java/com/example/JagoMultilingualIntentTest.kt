package com.example

import com.example.data.ai.JagoAiService
import com.example.data.ai.JagoAiService.UserIntent
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Table-Driven Multilingual Intent Test Matrix for JAGO AI Assistant.
 * Verifies keyword and phrase classification across English, Hindi, Odia, and Gondi.
 */
class JagoMultilingualIntentTest {

    private lateinit var aiService: JagoAiService

    @Before
    fun setup() {
        aiService = JagoAiService()
    }

    data class IntentTestCase(
        val phrase: String,
        val language: String,
        val expectedIntent: UserIntent
    )

    @Test
    fun testMultilingualIntents() {
        val testCases = listOf(
            // 1. GREETING (EN, HI, OR, GON, Romanized)
            IntentTestCase("Hello JAGO", "EN", UserIntent.GREETING),
            IntentTestCase("Hi there, how are you?", "EN", UserIntent.GREETING),
            IntentTestCase("Good morning assistant", "EN", UserIntent.GREETING),
            IntentTestCase("Hey friend", "EN", UserIntent.GREETING),
            IntentTestCase("Greetings JAGO", "EN", UserIntent.GREETING),

            IntentTestCase("नमस्ते जागो", "HI", UserIntent.GREETING),
            IntentTestCase("नमस्कार", "HI", UserIntent.GREETING),
            IntentTestCase("प्रणाम जागो जी", "HI", UserIntent.GREETING),
            IntentTestCase("जोहार भैया", "HI", UserIntent.GREETING),
            IntentTestCase("हेलो", "HI", UserIntent.GREETING),

            IntentTestCase("ନମସ୍କାର ଜାଗୋ", "OR", UserIntent.GREETING),
            IntentTestCase("ଜୁହାର", "OR", UserIntent.GREETING),
            IntentTestCase("ପ୍ରଣାମ ଭାଇ", "OR", UserIntent.GREETING),
            IntentTestCase("ହେଲୋ", "OR", UserIntent.GREETING),
            IntentTestCase("ହାଏ ଜାଗୋ", "OR", UserIntent.GREETING),

            IntentTestCase("सेवा जोहार जागो", "GON", UserIntent.GREETING),
            IntentTestCase("जोहार", "GON", UserIntent.GREETING),
            IntentTestCase("राम राम", "GON", UserIntent.GREETING),
            IntentTestCase("जय सेवा जोहार", "GON", UserIntent.GREETING),
            IntentTestCase("नमस्ते", "GON", UserIntent.GREETING),

            // 2. PENDING ACTIONS (EN, HI, OR, GON, Romanized)
            IntentTestCase("What is pending on my application?", "EN", UserIntent.PENDING_ACTIONS),
            IntentTestCase("Check application status", "EN", UserIntent.PENDING_ACTIONS),
            IntentTestCase("Is any action required from my side?", "EN", UserIntent.PENDING_ACTIONS),
            IntentTestCase("What is my current stage?", "EN", UserIntent.PENDING_ACTIONS),
            IntentTestCase("Needs attention status update", "EN", UserIntent.PENDING_ACTIONS),

            IntentTestCase("मेरे आवेदन में क्या बाकी है?", "HI", UserIntent.PENDING_ACTIONS),
            IntentTestCase("आवेदन की स्थिति क्या है?", "HI", UserIntent.PENDING_ACTIONS),
            IntentTestCase("क्या कोई लंबित कार्य है?", "HI", UserIntent.PENDING_ACTIONS),
            IntentTestCase("आवेदन की प्रगति बताएं", "HI", UserIntent.PENDING_ACTIONS),
            IntentTestCase("कार्रवाई जरूरी का क्या मतलब है?", "HI", UserIntent.PENDING_ACTIONS),

            IntentTestCase("ମୋ ଆବେଦନରେ କଣ ବାକି ଅଛି?", "OR", UserIntent.PENDING_ACTIONS),
            IntentTestCase("ଆବେଦନର ସ୍ଥିତି କଣ?", "OR", UserIntent.PENDING_ACTIONS),
            IntentTestCase("କୌଣସି ପେଣ୍ଡିଂ କାମ ଅଛି କି?", "OR", UserIntent.PENDING_ACTIONS),
            IntentTestCase("ଆବେଦନ ସ୍ଥିତି ଯାଞ୍ଚ କରନ୍ତୁ", "OR", UserIntent.PENDING_ACTIONS),
            IntentTestCase("କାର୍ଯ୍ୟାନୁଷ୍ଠାନ ଆବଶ୍ୟକ କାହିଁକି?", "OR", UserIntent.PENDING_ACTIONS),

            IntentTestCase("मावा आवेदन ते का बाकी मंदा?", "GON", UserIntent.PENDING_ACTIONS),
            IntentTestCase("आवेदन ना स्थिति का मंदा?", "GON", UserIntent.PENDING_ACTIONS),
            IntentTestCase("का कीना मंदा?", "GON", UserIntent.PENDING_ACTIONS),
            IntentTestCase("पेंडिंग का मंदा?", "GON", UserIntent.PENDING_ACTIONS),
            IntentTestCase("कार्रवाई जरूरी क्यों मंदा?", "GON", UserIntent.PENDING_ACTIONS),

            // 3. ELIGIBILITY / SCHEMES (EN, HI, OR, GON, Romanized)
            IntentTestCase("Which schemes am I eligible for?", "EN", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("Am I eligible for Top Class scholarship?", "EN", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("Check my fellowship eligibility", "EN", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("Tell me about overseas scholarship", "EN", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("What is my scholarship match score?", "EN", UserIntent.ELIGIBILITY_SCHEMES),

            IntentTestCase("मैं किन योजनाओं के लिए पात्र हूँ?", "HI", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("क्या मैं टॉप क्लास छात्रवृत्ति ले सकता हूँ?", "HI", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("फेलोशिप योजना की पात्रता क्या है?", "HI", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("विदेश अध्ययन छात्रवृत्ति नियम बताएं", "HI", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("मेरे लिए कौन सी योजना उपयुक्त है?", "HI", UserIntent.ELIGIBILITY_SCHEMES),

            IntentTestCase("ମୁଁ କେଉଁ ଯୋଜନା ପାଇଁ ଯୋଗ୍ୟ?", "OR", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("ଟପ୍ କ୍ଲାସ୍ ଛାତ୍ରବୃତ୍ତି ପାଇଁ ମୁଁ ଯୋଗ୍ୟ କି?", "OR", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("ଫେଲୋସିପ୍ ଯୋଜନା ବିଷୟରେ କୁହନ୍ତୁ", "OR", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("ବିଦେଶ ଅଧ୍ୟୟନ ଯୋଜନା ନିୟମ", "OR", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("ମୋ ପାଇଁ ସର୍ବୋତ୍ତମ ଯୋଜନା କଣ?", "OR", UserIntent.ELIGIBILITY_SCHEMES),

            IntentTestCase("नना कोनो योजना साठी पात्र आन?", "GON", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("टॉप क्लास योजना पात्रता हुर्राट", "GON", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("फेलोशिप योजना ते पात्र आन का?", "GON", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("विदेश अध्ययन योजना नियम का मंदा?", "GON", UserIntent.ELIGIBILITY_SCHEMES),
            IntentTestCase("मावा साठी कोनो योजना ठीक मंदा?", "GON", UserIntent.ELIGIBILITY_SCHEMES),

            // 4. DOCUMENTS / DIGILOCKER (EN, HI, OR, GON, Romanized)
            IntentTestCase("How does DigiLocker wallet work?", "EN", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("Which documents are attached?", "EN", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("Do I need to upload caste certificate?", "EN", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("Is income certificate verified?", "EN", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("Can credentials be reused across schemes?", "EN", UserIntent.DOCUMENTS_DIGILOCKER),

            IntentTestCase("डिजिलॉकर से दस्तावेज़ कैसे जुड़ते हैं?", "HI", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("जाति प्रमाण पत्र की स्थिति क्या है?", "HI", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("आय प्रमाण पत्र कैसे सिंक करें?", "HI", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("दस्तावेज़ वॉलेट कैसे देखें?", "HI", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("क्या दोबारा कागजात अपलोड करने पड़ेंगे?", "HI", UserIntent.DOCUMENTS_DIGILOCKER),

            IntentTestCase("ଡିଜିଲକରରୁ ଦସ୍ତାବିଜ୍ କିପରି ଆସିବ?", "OR", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("ଜାତି ପ୍ରମାଣପତ୍ର ସଂଲଗ୍ନ ଅଛି କି?", "OR", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("ଆୟ ପ୍ରମାଣପତ୍ର ସିଙ୍କ୍ କରନ୍ତୁ", "OR", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("ୱାଲେଟରେ କେଉଁ ଦସ୍ତାବିଜ ଅଛି?", "OR", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("କାଗଜପତ୍ର ପୁନର୍ବାର ଅପଲୋଡ୍ କରିବି କି?", "OR", UserIntent.DOCUMENTS_DIGILOCKER),

            IntentTestCase("डिजिलॉकर से दस्तावेज़ कसं वाय?", "GON", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("जाति प्रमाण पत्र जुड़ल मंदा का?", "GON", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("आय प्रमाण पत्र सिंक कीम", "GON", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("वॉलेट ते कोनो कागजात मंदा?", "GON", UserIntent.DOCUMENTS_DIGILOCKER),
            IntentTestCase("दस्तावेज़ दोबारा अपलोड कीना पड़ेगा का?", "GON", UserIntent.DOCUMENTS_DIGILOCKER),

            // 5. DBT / PAYMENT STATUS (EN, HI, OR, GON, Romanized)
            IntentTestCase("When will DBT payment come?", "EN", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("Track my scholarship disbursement", "EN", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("Which bank account is money sent to?", "EN", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("Check PFMS payment status", "EN", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("What is my payment UTR reference?", "EN", UserIntent.DBT_PAYMENT_STATUS),

            IntentTestCase("डीबीटी भुगतान कब आएगा?", "HI", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("छात्रवृत्ति के पैसे कब मिलेंगे?", "HI", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("किस बैंक खाते में राशि भेजी जाएगी?", "HI", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("पीएफएमएस भुगतान की स्थिति देखें", "HI", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("बैंक ट्रांसफर की जानकारी दें", "HI", UserIntent.DBT_PAYMENT_STATUS),

            IntentTestCase("DBT ପେମେଣ୍ଟ କେବେ ଆସିବ?", "OR", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("ଛାତ୍ରବୃତ୍ତି ଟଙ୍କା କେବେ ମିଳିବ?", "OR", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("କେଉଁ ବ୍ୟାଙ୍କ୍ ଖାତାରେ ଟଙ୍କା ଜମା ହେବ?", "OR", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("PFMS ପେମେଣ୍ଟ ସ୍ଥିତି ଯାଞ୍ଚ", "OR", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("ବ୍ୟାଙ୍କ୍ ଟ୍ରାନ୍ସଫର ବିବରଣୀ ଦିଅନ୍ତୁ", "OR", UserIntent.DBT_PAYMENT_STATUS),

            IntentTestCase("डीबीटी पैसा कब वाय?", "GON", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("छात्रवृत्ति पैसा कब वत्ता?", "GON", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("कोनो बैंक खाता ते पैसा वाय?", "GON", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("पीएफएमएस भुगतान स्थिति हुर्राट", "GON", UserIntent.DBT_PAYMENT_STATUS),
            IntentTestCase("बैंक खाता ते पैसा ट्रांसफर", "GON", UserIntent.DBT_PAYMENT_STATUS),

            // 6. INCOME FLAG / DISCREPANCY REASON (EN, HI, OR, GON, Romanized)
            IntentTestCase("Why was income flagged?", "EN", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("What is this income discrepancy?", "EN", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("Explain statutory tolerance review", "EN", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("Why is there an income difference?", "EN", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("Why is my income mismatch shown?", "EN", UserIntent.INCOME_FLAG_REASON),

            IntentTestCase("आय में अंतर क्यों है?", "HI", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("आय विसंगति का क्या कारण है?", "HI", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("सहिष्णुता समीक्षा क्या होती है?", "HI", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("घोषित आय में अंतर क्यों दिखाया?", "HI", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("आय फ्लैग क्यों हुई?", "HI", UserIntent.INCOME_FLAG_REASON),

            IntentTestCase("ଆୟରେ ପାର୍ଥକ୍ୟ କାହିଁକି ଅଛି?", "OR", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("ଆୟ ବ୍ୟତିକ୍ରମର କାରଣ କଣ?", "OR", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("ସହନଶୀଳତା ସମୀକ୍ଷା କଣ?", "OR", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("ଘୋଷିତ ଆୟରେ ଭିନ୍ନତା କାହିଁକି?", "OR", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("ଆୟ ତ୍ରୁଟି କାହିଁକି ଦେଖାଗଲା?", "OR", UserIntent.INCOME_FLAG_REASON),

            IntentTestCase("आय ते अंतर क्यों दिखाय कीता?", "GON", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("आय विसंगति कारण का मंदा?", "GON", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("समीक्षा नियम का मंदा?", "GON", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("आय अंतर क्यों आता?", "GON", UserIntent.INCOME_FLAG_REASON),
            IntentTestCase("आय फ्लैग क्यों आता?", "GON", UserIntent.INCOME_FLAG_REASON),

            // 7. HOW TO APPLY (EN, HI, OR, GON, Romanized)
            IntentTestCase("How to apply for scholarship?", "EN", UserIntent.HOW_TO_APPLY),
            IntentTestCase("How does 1-click apply work?", "EN", UserIntent.HOW_TO_APPLY),
            IntentTestCase("Where is the application form?", "EN", UserIntent.HOW_TO_APPLY),
            IntentTestCase("Explain the scholarship wizard steps", "EN", UserIntent.HOW_TO_APPLY),
            IntentTestCase("How to submit an application?", "EN", UserIntent.HOW_TO_APPLY),

            IntentTestCase("आवेदन कैसे करें?", "HI", UserIntent.HOW_TO_APPLY),
            IntentTestCase("1-क्लिक आवेदन कैसे काम करता है?", "HI", UserIntent.HOW_TO_APPLY),
            IntentTestCase("छात्रवृत्ति फॉर्म कैसे भरें?", "HI", UserIntent.HOW_TO_APPLY),
            IntentTestCase("विज़ार्ड से आवेदन की प्रक्रिया बताएं", "HI", UserIntent.HOW_TO_APPLY),
            IntentTestCase("आवेदन जमा कैसे करें?", "HI", UserIntent.HOW_TO_APPLY),

            IntentTestCase("ଆବେଦନ କିପରି କରିବେ?", "OR", UserIntent.HOW_TO_APPLY),
            IntentTestCase("1-କ୍ଲିକ୍ ଆବେଦନ କିପରି କାମ କରେ?", "OR", UserIntent.HOW_TO_APPLY),
            IntentTestCase("ଫର୍ମ କିପରି ପୂରଣ କରିବି?", "OR", UserIntent.HOW_TO_APPLY),
            IntentTestCase("ୱିଜାର୍ଡ ଆବେଦନ ପ୍ରକ୍ରିୟା ବୁଝାନ୍ତୁ", "OR", UserIntent.HOW_TO_APPLY),
            IntentTestCase("ଆବେଦନ ଦାଖଲ କିପରି କରିବା?", "OR", UserIntent.HOW_TO_APPLY),

            IntentTestCase("आवेदन कसं कीना?", "GON", UserIntent.HOW_TO_APPLY),
            IntentTestCase("1-क्लिक आवेदन कसं काम कींदू?", "GON", UserIntent.HOW_TO_APPLY),
            IntentTestCase("फॉर्म कसं भरना?", "GON", UserIntent.HOW_TO_APPLY),
            IntentTestCase("विज़ार्ड से आवेदन तरीका बताओ", "GON", UserIntent.HOW_TO_APPLY),
            IntentTestCase("आवेदन जमा कसं कीना?", "GON", UserIntent.HOW_TO_APPLY),

            // 8. OFFLINE MODE (EN, HI, OR, GON, Romanized)
            IntentTestCase("Does the app work in offline mode?", "EN", UserIntent.OFFLINE_MODE),
            IntentTestCase("What happens when there is no internet?", "EN", UserIntent.OFFLINE_MODE),
            IntentTestCase("Will my offline drafts sync automatically?", "EN", UserIntent.OFFLINE_MODE),
            IntentTestCase("Can I use this in airplane mode?", "EN", UserIntent.OFFLINE_MODE),
            IntentTestCase("How does offline queue work?", "EN", UserIntent.OFFLINE_MODE),

            IntentTestCase("क्या यह ऐप ऑफलाइन मोड में काम करता है?", "HI", UserIntent.OFFLINE_MODE),
            IntentTestCase("इंटरनेट नहीं होने पर क्या होगा?", "HI", UserIntent.OFFLINE_MODE),
            IntentTestCase("ऑफलाइन ड्राफ्ट कैसे सिंक होंगे?", "HI", UserIntent.OFFLINE_MODE),
            IntentTestCase("बिना इंटरनेट के डेटा सेव होगा क्या?", "HI", UserIntent.OFFLINE_MODE),
            IntentTestCase("ऑफलाइन सिंक के बारे में बताएं", "HI", UserIntent.OFFLINE_MODE),

            IntentTestCase("ଏହି ଆପ୍ ଅଫଲାଇନ୍ ମୋଡରେ କାମ କରେ କି?", "OR", UserIntent.OFFLINE_MODE),
            IntentTestCase("ଇଣ୍ଟରନେଟ୍ ନଥିଲେ କଣ ହେବ?", "OR", UserIntent.OFFLINE_MODE),
            IntentTestCase("ଅଫଲାଇନ୍ ଡ୍ରାଫ୍ଟ ସ୍ୱତଃ ସିଙ୍କ୍ ହେବ କି?", "OR", UserIntent.OFFLINE_MODE),
            IntentTestCase("ବିନା ନେଟରେ ତଥ୍ୟ ସାଇତା ହେବ ତ?", "OR", UserIntent.OFFLINE_MODE),
            IntentTestCase("ଅଫଲାଇନ୍ ସିଙ୍କ୍ କିପରି କାମ କରେ?", "OR", UserIntent.OFFLINE_MODE),

            IntentTestCase("ऐप ऑफलाइन मोड ते काम कींदू का?", "GON", UserIntent.OFFLINE_MODE),
            IntentTestCase("नेटवर्क सिल्ले आतके का आय?", "GON", UserIntent.OFFLINE_MODE),
            IntentTestCase("ऑफलाइन ड्राफ्ट अपने आप सिंक आय का?", "GON", UserIntent.OFFLINE_MODE),
            IntentTestCase("नेट सिल्ले भी डेटा सुरक्षित मंदा का?", "GON", UserIntent.OFFLINE_MODE),
            IntentTestCase("ऑफलाइन सिंक बारे ते बताओ", "GON", UserIntent.OFFLINE_MODE)
        )

        for (tc in testCases) {
            val actual = aiService.classifyIntent(tc.phrase)
            assertEquals("Failed intent classification for [${tc.language}] '${tc.phrase}'", tc.expectedIntent, actual)
        }
    }
}
