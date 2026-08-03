package com.vedx.vedxsuper.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceAlertHelper(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady = false
    private var pendingLanguage: Locale? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            pendingLanguage?.let { tts?.language = it } ?: run { tts?.language = Locale.US }
        }
    }

    fun setLanguage(language: String) {
        val locale = when (language) {
            "Hindi" -> Locale("hi", "IN")
            "Marathi" -> Locale("mr", "IN")
            else -> Locale.US
        }
        if (isReady) {
            tts?.language = locale
        } else {
            pendingLanguage = locale
        }
    }

    fun speak(text: String) {
        val settings = SettingsManager(context)
        if (!settings.isVoiceAlertEnabled()) return

        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
