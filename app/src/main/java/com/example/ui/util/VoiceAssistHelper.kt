package com.example.ui.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class TtsPlayState {
    IDLE,
    PLAYING,
    PAUSED,
    UNAVAILABLE
}

/**
 * Text-to-Speech Accessibility & Voice Assist Manager for Ekikrit.
 * Reads out screen summaries and status updates in English, Hindi, Odia, or Gondi.
 */
class VoiceAssistHelper(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceAssistHelper"
        private const val UTTERANCE_ID = "EKIKRIT_VOICE_ASSIST"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _playState = MutableStateFlow(TtsPlayState.IDLE)
    val playState: StateFlow<TtsPlayState> = _playState.asStateFlow()

    private val _currentSpokenText = MutableStateFlow<String?>(null)
    val currentSpokenText: StateFlow<String?> = _currentSpokenText.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to instantiate TextToSpeech: ${e.message}")
            _playState.value = TtsPlayState.UNAVAILABLE
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _playState.value = TtsPlayState.PLAYING
                }

                override fun onDone(utteranceId: String?) {
                    _playState.value = TtsPlayState.IDLE
                    _currentSpokenText.value = null
                }

                override fun onError(utteranceId: String?) {
                    _playState.value = TtsPlayState.IDLE
                    _currentSpokenText.value = null
                }
            })
        } else {
            isInitialized = false
            _playState.value = TtsPlayState.UNAVAILABLE
        }
    }

    fun speak(text: String, language: AppLanguage): Boolean {
        if (text.isBlank()) return false
        val engine = tts ?: return false
        if (!isInitialized) {
            _playState.value = TtsPlayState.UNAVAILABLE
            return false
        }

        val locale = when (language) {
            AppLanguage.ENGLISH -> Locale.US
            AppLanguage.HINDI -> Locale("hi", "IN")
            AppLanguage.ODIA -> Locale("or", "IN")
            AppLanguage.GONDI -> Locale("hi", "IN") // Gracefully speak in standard Hindi voice for Gondi
        }

        val availability = engine.isLanguageAvailable(locale)
        if (availability == TextToSpeech.LANG_NOT_SUPPORTED || availability == TextToSpeech.LANG_MISSING_DATA) {
            // Try general Indian English or Hindi fallback
            val fallbackLocale = if (language == AppLanguage.ODIA) Locale("hi", "IN") else Locale.US
            if (engine.isLanguageAvailable(fallbackLocale) >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = fallbackLocale
            } else {
                _playState.value = TtsPlayState.UNAVAILABLE
                return false
            }
        } else {
            engine.language = locale
        }

        engine.stop()
        _currentSpokenText.value = text
        _playState.value = TtsPlayState.PLAYING

        val params = Bundle()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
        return result == TextToSpeech.SUCCESS
    }

    fun stop() {
        tts?.stop()
        _playState.value = TtsPlayState.IDLE
        _currentSpokenText.value = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
