package com.example.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.example.data.model.AppLanguage
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * CompositionLocal providing current active AppStrings based on selected AppLanguage.
 */
val LocalAppStrings = compositionLocalOf { EnglishStrings }

/**
 * Convenient Composable accessor for localized strings.
 */
val currentStrings: AppStrings
    @Composable
    @ReadOnlyComposable
    get() = LocalAppStrings.current

/**
 * Resolves the typed string catalog for a given language.
 */
fun getAppStrings(language: AppLanguage): AppStrings {
    return when (language) {
        AppLanguage.ENGLISH -> EnglishStrings
        AppLanguage.HINDI -> HindiStrings
        AppLanguage.ODIA -> OdiaStrings
        AppLanguage.GONDI -> GondiStrings
    }
}

// =====================================================================
// DISPLAY-TIME LOCALIZATION MAPPERS (Zero Hardcoded Raw Enums)
// =====================================================================

fun localizeStage(stage: String, strings: AppStrings): String {
    return when (stage.uppercase(Locale.ROOT)) {
        "SUBMITTED", "APPLIED" -> strings.stageSubmitted
        "INSTITUTE_VERIFICATION", "INSTITUTE" -> strings.stageInstituteVerification
        "STATE_VERIFICATION", "STATE" -> strings.stageStateVerification
        "MINISTRY_REVIEW", "MINISTRY" -> strings.stageMinistryReview
        "SANCTIONED" -> strings.stageSanctioned
        "DISBURSED" -> strings.stageDisbursed
        "ACTION_REQUIRED", "NEEDS_ATTENTION" -> strings.stageActionRequired
        "REJECTED" -> strings.stageRejected
        else -> stage.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}
fun localizeStage(strings: AppStrings, stage: String): String = localizeStage(stage, strings)

fun localizeVerificationStatus(status: String, strings: AppStrings): String {
    return when (status.uppercase(Locale.ROOT)) {
        "VERIFIED", "SUCCESS" -> strings.statusVerified
        "MISMATCH", "FLAGGED", "DISCREPANCY" -> strings.statusMismatch
        "PENDING", "UNDER_VERIFICATION" -> strings.statusPending
        "RESOLVED", "RESOLVED_ACCEPTED" -> strings.statusResolved
        else -> strings.statusDiscrepancy
    }
}
fun localizeVerificationStatus(strings: AppStrings, status: String): String = localizeVerificationStatus(status, strings)

fun localizeDocType(type: String, strings: AppStrings): String {
    val normalized = type.uppercase(Locale.ROOT)
    return when {
        normalized.contains("AADHAAR") -> strings.docAadhaar
        normalized.contains("CASTE") || normalized.contains("COMMUNITY") -> strings.docCaste
        normalized.contains("INCOME") -> strings.docIncome
        normalized.contains("MARKSHEET") || normalized.contains("10TH") || normalized.contains("12TH") -> strings.docMarksheet
        normalized.contains("DOMICILE") || normalized.contains("RESIDENCE") -> strings.docDomicile
        normalized.contains("APAAR") || normalized.contains("ABC") -> strings.docApaar
        normalized.contains("ADMISSION") || normalized.contains("OFFER") -> strings.docAdmissionProof
        normalized.contains("FEE") || normalized.contains("RECEIPT") -> strings.docFeeReceipt
        normalized.contains("BANK") || normalized.contains("PASSBOOK") -> strings.docBankPassbook
        else -> type
    }
}
fun localizeDocType(strings: AppStrings, type: String): String = localizeDocType(type, strings)

fun localizeVerificationSource(source: String, strings: AppStrings): String {
    val normalized = source.uppercase(Locale.ROOT)
    return when {
        normalized.contains("UIDAI") || normalized.contains("AADHAAR") -> strings.srcUidai
        normalized.contains("DIGILOCKER") -> strings.srcDigilocker
        normalized.contains("AISHE") -> strings.srcAishe
        normalized.contains("APAAR") || normalized.contains("ABC") -> strings.srcApaar
        normalized.contains("UDISE") -> strings.srcUdisePlus
        normalized.contains("UGC") || normalized.contains("NTA") -> strings.srcUgcNta
        normalized.contains("DISTRICT") || normalized.contains("EDISTRICT") -> strings.srcEdistrict
        else -> source
    }
}
fun localizeVerificationSource(strings: AppStrings, source: String): String = localizeVerificationSource(source, strings)

fun localizeSchemeName(schemeId: String, defaultName: String, strings: AppStrings): String {
    return when {
        schemeId.contains("PRE", ignoreCase = true) -> strings.schemePreName
        schemeId.contains("PMS", ignoreCase = true) || schemeId.contains("POST", ignoreCase = true) -> strings.schemePmsName
        schemeId.contains("TOP", ignoreCase = true) || schemeId.contains("CLASS", ignoreCase = true) -> strings.schemeTopClassName
        schemeId.contains("NFST", ignoreCase = true) || schemeId.contains("FELLOW", ignoreCase = true) -> strings.schemeNfstName
        schemeId.contains("NOS", ignoreCase = true) || schemeId.contains("OVERSEAS", ignoreCase = true) -> strings.schemeNosName
        else -> defaultName
    }
}
fun localizeSchemeName(strings: AppStrings, schemeId: String, defaultName: String): String = localizeSchemeName(schemeId, defaultName, strings)

fun localizeSchemeDesc(schemeId: String, defaultDesc: String, strings: AppStrings): String {
    return when {
        schemeId.contains("PRE", ignoreCase = true) -> strings.schemePreDesc
        schemeId.contains("PMS", ignoreCase = true) || schemeId.contains("POST", ignoreCase = true) -> strings.schemePmsDesc
        schemeId.contains("TOP", ignoreCase = true) || schemeId.contains("CLASS", ignoreCase = true) -> strings.schemeTopClassDesc
        schemeId.contains("NFST", ignoreCase = true) || schemeId.contains("FELLOW", ignoreCase = true) -> strings.schemeNfstDesc
        schemeId.contains("NOS", ignoreCase = true) || schemeId.contains("OVERSEAS", ignoreCase = true) -> strings.schemeNosDesc
        else -> defaultDesc
    }
}
fun localizeSchemeDesc(strings: AppStrings, schemeId: String, defaultDesc: String): String = localizeSchemeDesc(schemeId, defaultDesc, strings)

fun localizeCategory(category: String, strings: AppStrings): String {
    return if (category.contains("PVTG", ignoreCase = true) || category.contains("Birhor", ignoreCase = true)) {
        strings.pvtgBadge
    } else {
        category
    }
}

/**
 * Formats currency in Indian numbering format (e.g. ₹2,50,000) with Western 0-9 digits across all languages.
 */
fun formatRupees(amount: Number): String {
    val doubleVal = amount.toDouble()
    val symbols = DecimalFormatSymbols(Locale.US)
    val formatter = DecimalFormat("#,##,##0", symbols)
    return "₹" + formatter.format(doubleVal)
}

/**
 * Localizes dates cleanly preserving standard Western numerals 0-9.
 */
fun formatDateLocalized(dateStr: String, language: AppLanguage): String {
    if (dateStr.isBlank()) return dateStr
    val monthReplacementsEn = listOf(
        "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
        "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12
    )
    val hindiMonths = listOf("", "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")
    val odiaMonths = listOf("", "ଜାନୁଆରୀ", "ଫେବୃଆରୀ", "ମାର୍ଚ୍ଚ", "ଏପ୍ରିଲ", "ମେ", "ଜୁନ", "ଜୁଲାଇ", "ଅଗଷ୍ଟ", "ସେପ୍ଟେମ୍ବର", "ଅକ୍ଟୋବର", "ନଭେମ୍ବର", "ଡିସେମ୍ବର")

    var result = dateStr
    monthReplacementsEn.forEach { (enMonth, monthNum) ->
        if (result.contains(enMonth, ignoreCase = true)) {
            when (language) {
                AppLanguage.HINDI -> result = result.replace(enMonth, hindiMonths[monthNum], ignoreCase = true)
                AppLanguage.ODIA -> result = result.replace(enMonth, odiaMonths[monthNum], ignoreCase = true)
                AppLanguage.GONDI -> { /* Gondi preserves English month names */ }
                AppLanguage.ENGLISH -> { /* English format */ }
            }
        }
    }
    return result
}
