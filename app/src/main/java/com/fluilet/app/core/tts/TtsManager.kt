package com.fluilet.app.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var engine: TextToSpeech? = null
    private var isReady = false
    private var onRangeCallback: ((start: Int, end: Int) -> Unit)? = null
    private var onCompletionCallback: (() -> Unit)? = null

    init {
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = applyBestAvailableSpanishLocale()
                Log.d(TAG, "Motor TTS inicializado. isReady=$isReady")
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS: comenzó a hablar ($utteranceId)")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS: terminó de hablar ($utteranceId)")
                        onCompletionCallback?.invoke()
                    }

                    @Deprecated("Deprecated en la API de Android, requerido por la interfaz")
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS: error al hablar ($utteranceId)")
                        onCompletionCallback?.invoke()
                    }

                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        onRangeCallback?.invoke(start, end)
                    }
                })
            } else {
                isReady = false
                Log.e(TAG, "Falló la inicialización del motor TTS. Status: $status")
            }
        }
    }

    private fun applyBestAvailableSpanishLocale(): Boolean {
        val candidates = listOf(Locale("es", "MX"), Locale("es", "US"), Locale("es"), Locale.getDefault())
        for (locale in candidates) {
            val result = engine?.setLanguage(locale)
            Log.d(TAG, "Probando locale $locale -> $result")
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.d(TAG, "Locale aplicado: $locale")
                return true
            }
        }
        return false
    }

    /**
     * @param onRangeStart rango [start, end) del texto que el motor pronuncia en este instante —
     * usado para resaltar la palabra actual en pantalla (RF: Lectura Guiada con Resaltado).
     */
    fun speak(text: String, onRangeStart: (start: Int, end: Int) -> Unit, onFinished: () -> Unit) {
        if (!isReady) {
            Log.w(TAG, "speak() llamado pero el motor no está listo. Se omite la lectura.")
            onFinished()
            return
        }
        if (text.isBlank()) {
            onFinished()
            return
        }
        onRangeCallback = onRangeStart
        onCompletionCallback = onFinished
        val result = engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        Log.d(TAG, "engine.speak() devolvió: $result")
    }

    fun stop() {
        engine?.stop()
    }

    fun release() {
        engine?.shutdown()
        engine = null
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}