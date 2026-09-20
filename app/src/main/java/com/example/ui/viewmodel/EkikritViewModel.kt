package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.EkikritDatabase
import com.example.data.model.*
import com.example.data.repository.EkikritRepository
import com.example.domain.EligibilityEngine
import com.example.domain.EligibilityEvaluation
import com.example.ui.util.AppStrings
import com.example.ui.util.TtsPlayState
import com.example.ui.util.VoiceAssistHelper
import com.example.ui.util.getAppStrings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppTab(val title: String) {
    DASHBOARD("Dashboard"),
    SCHEMES("5 Schemes"),
    DOCUMENTS("DigiLocker Wallet"),
    DISBURSEMENT("DBT Payments"),
    REVIEWER_QUEUE("Reviewer Desk")
}

enum class UserMode {
    STUDENT,
    OFFICER
}

class EkikritViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("ekikrit_prefs", Context.MODE_PRIVATE)

    val repository: EkikritRepository
    val voiceAssistHelper: VoiceAssistHelper = VoiceAssistHelper(application)

    init {
        val db = EkikritDatabase.getDatabase(application, viewModelScope)
        repository = EkikritRepository(db, viewModelScope)
    }

    val activeStudentId = repository.activeStudentId

    val student = repository.studentFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val allStudents = repository.allStudentsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val schemes = repository.schemesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val applications = repository.applicationsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val documents = repository.documentsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val disbursements = repository.disbursementsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val notifications = repository.notificationsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val reviewQueue = repository.reviewQueueFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val auditLogs = repository.auditLogsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val scholarshipMatch = repository.scholarshipMatchFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val isOfflineMode = repository.isOfflineMode

    private val _userMode = MutableStateFlow(UserMode.STUDENT)
    val userMode: StateFlow<UserMode> = _userMode.asStateFlow()

    private val _currentTab = MutableStateFlow(AppTab.DASHBOARD)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _selectedApplicationId = MutableStateFlow<String?>(null)
    val selectedApplicationId: StateFlow<String?> = _selectedApplicationId.asStateFlow()

    private val _selectedSchemeDetailId = MutableStateFlow<String?>(null)
    val selectedSchemeDetailId: StateFlow<String?> = _selectedSchemeDetailId.asStateFlow()

    // Load saved language or default to English
    private val _selectedLanguage = MutableStateFlow(
        run {
            val savedCode = prefs.getString("selected_language_code", "en") ?: "en"
            AppLanguage.values().firstOrNull { it.code == savedCode } ?: AppLanguage.ENGLISH
        }
    )
    val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage.asStateFlow()

    private val _isJagoChatOpen = MutableStateFlow(false)
    val isJagoChatOpen: StateFlow<Boolean> = _isJagoChatOpen.asStateFlow()

    private val _isJagoTyping = MutableStateFlow(false)
    val isJagoTyping: StateFlow<Boolean> = _isJagoTyping.asStateFlow()

    private val _showIntroTour = MutableStateFlow(prefs.getBoolean("show_intro_tour_v1", true))
    val showIntroTour: StateFlow<Boolean> = _showIntroTour.asStateFlow()

    private val _showLanguageDialog = MutableStateFlow(false)
    val showLanguageDialog: StateFlow<Boolean> = _showLanguageDialog.asStateFlow()

    private val _showSecurityDialog = MutableStateFlow(false)
    val showSecurityDialog: StateFlow<Boolean> = _showSecurityDialog.asStateFlow()

    private val _showAuditDialog = MutableStateFlow(false)
    val showAuditDialog: StateFlow<Boolean> = _showAuditDialog.asStateFlow()

    private val _showShareDialog = MutableStateFlow(false)
    val showShareDialog: StateFlow<Boolean> = _showShareDialog.asStateFlow()

    private val _showConsentDialog = MutableStateFlow(false)
    val showConsentDialog: StateFlow<Boolean> = _showConsentDialog.asStateFlow()

    private val _showLoginSheet = MutableStateFlow(false)
    val showLoginSheet: StateFlow<Boolean> = _showLoginSheet.asStateFlow()

    private val _showNotificationsSheet = MutableStateFlow(false)
    val showNotificationsSheet: StateFlow<Boolean> = _showNotificationsSheet.asStateFlow()

    private val _showScholarshipWizard = MutableStateFlow(false)
    val showScholarshipWizard: StateFlow<Boolean> = _showScholarshipWizard.asStateFlow()

    private val _isSimulatingVerification = MutableStateFlow(false)
    val isSimulatingVerification: StateFlow<Boolean> = _isSimulatingVerification.asStateFlow()

    private val _userNotice = MutableStateFlow<String?>(null)
    val userNotice: StateFlow<String?> = _userNotice.asStateFlow()

    private val _jagoMessages = MutableStateFlow<List<JagoMessage>>(
        listOf(
            JagoMessage(
                sender = "JAGO",
                content = "Johar! I am JAGO, your unified tribal scholarship assistant. How can I assist you with your 5 tribal schemes, multi-system verification, or DigiLocker credentials today?",
                quickChips = listOf("Application Status", "Why was income flagged?", "Am I eligible for Top Class?", "When will amount disburse?")
            )
        )
    )
    val jagoMessages: StateFlow<List<JagoMessage>> = _jagoMessages.asStateFlow()

    val ttsPlayState: StateFlow<TtsPlayState> = voiceAssistHelper.playState

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun setUserMode(mode: UserMode) {
        _userMode.value = mode
        if (mode == UserMode.OFFICER) {
            _currentTab.value = AppTab.REVIEWER_QUEUE
            if (activeStudentId.value != "REV_OFFICER_01") {
                switchStudent("REV_OFFICER_01")
            }
        } else {
            if (_currentTab.value == AppTab.REVIEWER_QUEUE) {
                _currentTab.value = AppTab.DASHBOARD
            }
            if (activeStudentId.value == "REV_OFFICER_01") {
                switchStudent("STU_2026_01")
            }
        }
    }

    fun openApplicationDetail(appId: String) {
        _selectedApplicationId.value = appId
    }

    fun closeApplicationDetail() {
        _selectedApplicationId.value = null
    }

    fun openSchemeDetail(schemeId: String) {
        _selectedSchemeDetailId.value = schemeId
    }

    fun closeSchemeDetail() {
        _selectedSchemeDetailId.value = null
    }

    fun toggleJagoChat(open: Boolean? = null) {
        _isJagoChatOpen.value = open ?: !_isJagoChatOpen.value
    }

    fun toggleConsentDialog(show: Boolean) {
        _showConsentDialog.value = show
    }

    fun toggleLoginSheet(show: Boolean) {
        _showLoginSheet.value = show
    }

    fun toggleNotificationsSheet(show: Boolean) {
        _showNotificationsSheet.value = show
    }

    fun toggleLanguageDialog(show: Boolean) {
        _showLanguageDialog.value = show
    }

    fun toggleSecurityDialog(show: Boolean) {
        _showSecurityDialog.value = show
    }

    fun toggleAuditDialog(show: Boolean) {
        _showAuditDialog.value = show
    }

    fun toggleShareDialog(show: Boolean) {
        _showShareDialog.value = show
    }

    fun toggleScholarshipWizard(show: Boolean) {
        _showScholarshipWizard.value = show
    }

    fun dismissIntroTour() {
        _showIntroTour.value = false
        prefs.edit().putBoolean("show_intro_tour_v1", false).apply()
    }

    fun replayIntroTour() {
        _showIntroTour.value = true
    }

    fun toggleOfflineMode(offline: Boolean) {
        repository.setOfflineMode(offline)
        val strings = getAppStrings(_selectedLanguage.value)
        _userNotice.value = if (offline) strings.offlineBannerTitle else "Back online! Auto-sync complete."
    }

    fun switchStudent(studentId: String) {
        viewModelScope.launch {
            try {
                repository.switchStudent(studentId)
                if (studentId == "REV_OFFICER_01") {
                    _userMode.value = UserMode.OFFICER
                    _currentTab.value = AppTab.REVIEWER_QUEUE
                    _userNotice.value = "Reviewer Desk active (Dr. Anita Hansda)."
                } else {
                    _userMode.value = UserMode.STUDENT
                    _userNotice.value = "Beneficiary profile active."
                }
                _showLoginSheet.value = false
            } catch (e: Exception) {
                _userNotice.value = e.message ?: "Failed to switch persona."
            }
        }
    }

    fun switchToStudentRole() {
        setUserMode(UserMode.STUDENT)
        switchStudent("STU_2026_01")
    }

    fun switchToReviewerRole() {
        setUserMode(UserMode.OFFICER)
        switchStudent("REV_OFFICER_01")
    }

    fun loginWithMobileOrAadhaar(identifier: String, name: String? = null) {
        viewModelScope.launch {
            val loggedIn = repository.authenticateWithPhoneOrAadhaar(identifier, name)
            _showLoginSheet.value = false
            _userNotice.value = "Authenticated as ${loggedIn.name}."
        }
    }

    fun setLanguage(lang: AppLanguage) {
        _selectedLanguage.value = lang
        prefs.edit().putString("selected_language_code", lang.code).apply()
        _showLanguageDialog.value = false
    }

    fun clearNotice() {
        _userNotice.value = null
    }

    fun triggerVerification(appId: String) {
        viewModelScope.launch {
            _isSimulatingVerification.value = true
            repository.runSevenSourceVerification(appId)
            _isSimulatingVerification.value = false
            _userNotice.value = "Multi-source verification executed across 7 national registries."
        }
    }

    fun resolveReviewItem(itemId: String, isApproved: Boolean, notes: String = "") {
        viewModelScope.launch {
            repository.resolveReviewItem(itemId, isApproved, notes)
            _userNotice.value = if (isApproved) {
                "Verification issue resolved. Application moved to State Verification."
            } else {
                "Clarification requested from student."
            }
        }
    }

    fun applyForScheme(schemeId: String, declaredIncome: Double? = null) {
        viewModelScope.launch {
            val (success, msg) = repository.applyForScheme(schemeId, declaredIncome)
            _userNotice.value = msg
            _showScholarshipWizard.value = false
        }
    }

    fun updateStudentConsent(granted: Boolean) {
        viewModelScope.launch {
            repository.updateStudentConsent(repository.activeStudentId.value, granted)
            _userNotice.value = if (granted) "DPDP Act 2023 consent granted." else "DPDP Act 2023 consent revoked."
        }
    }

    fun pullDigiLockerDocument(type: String, title: String, docNumber: String, issuer: String) {
        viewModelScope.launch {
            try {
                repository.pullDocumentFromDigiLocker(type, title, docNumber, issuer)
                _userNotice.value = "Successfully pulled '$title' from DigiLocker wallet."
            } catch (e: Exception) {
                _userNotice.value = e.message ?: "Failed to pull document."
            }
        }
    }

    fun markNotificationAsRead(notifId: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(notifId)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            voiceAssistHelper.stop()
            repository.resetAllDemoData()
            _userNotice.value = "Demo database reset to clean default state."
        }
    }

    fun sendJagoQuery(query: String) {
        val userMsg = JagoMessage(
            sender = "USER",
            content = query
        )
        _jagoMessages.value = _jagoMessages.value + userMsg
        _isJagoTyping.value = true

        viewModelScope.launch {
            try {
                val jagoResponse = repository.generateJagoResponse(query, _selectedLanguage.value.code)
                _jagoMessages.value = _jagoMessages.value + jagoResponse
            } catch (e: Exception) {
                val errStrings = getAppStrings(_selectedLanguage.value)
                val errMessage = JagoMessage(
                    sender = "JAGO",
                    content = "Unable to process query at this time. Please retry.",
                    quickChips = listOf(errStrings.jagoChipStatus, errStrings.jagoChipEligible, errStrings.jagoChipPayment)
                )
                _jagoMessages.value = _jagoMessages.value + errMessage
            } finally {
                _isJagoTyping.value = false
            }
        }
    }

    fun speakScreenSummary(summaryText: String? = null) {
        val strings = getAppStrings(_selectedLanguage.value)
        val textToSpeak = summaryText ?: when (_currentTab.value) {
            AppTab.DASHBOARD -> "${strings.welcomePrefix}, ${student.value?.name ?: "Student"}. ${strings.nspPfmsActive}. ${strings.activeApplicationsTitle}."
            AppTab.SCHEMES -> "${strings.schemesHeaderTitle}. ${strings.schemesHeaderSubtitle}."
            AppTab.DOCUMENTS -> "${strings.digiLockerWalletTitle}. ${strings.attachedCredentialsTitle}."
            AppTab.DISBURSEMENT -> "${strings.dbtTrackerTitle}. ${strings.dbtCurrentTrancheTitle}."
            AppTab.REVIEWER_QUEUE -> "${strings.reviewerTitle}. ${strings.reviewerSubtitle}."
        }

        val success = voiceAssistHelper.speak(textToSpeak, _selectedLanguage.value)
        if (!success && voiceAssistHelper.playState.value == TtsPlayState.UNAVAILABLE) {
            _userNotice.value = strings.voiceAssistUnavailable
        }
    }

    fun stopVoice() {
        voiceAssistHelper.stop()
    }

    fun speakNarration(summaryText: String? = null) {
        speakScreenSummary(summaryText)
    }

    fun stopNarration() {
        stopVoice()
    }

    fun getVerificationRecordsForAppFlow(appId: String): Flow<List<VerificationRecordEntity>> {
        return repository.getVerificationRecordsForAppFlow(appId)
    }

    override fun onCleared() {
        super.onCleared()
        voiceAssistHelper.shutdown()
    }
}
