package com.fluilet.app.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Motor nativo `android.speech.tts.TextToSpeech` configurado en español y 100 % offline:
 * solo se elige una voz que NO requiere red ([Voice.isNetworkConnectionRequired] == false) y que
 * esté instalada. El resaltado palabra por palabra llega por `onRangeStart` (API 26+).
 */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {

    override var listener: SpeechEngineListener? = null

    private var tts: TextToSpeech? = null

    override fun initialize(onResult: (SpeechEngineInit) -> Unit) {
        tts = TextToSpeech(context) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                onResult(SpeechEngineInit.Unavailable("No se pudo iniciar el motor de voz del dispositivo."))
            } else {
                onResult(configure(engine))
            }
        }
    }

    private fun configure(engine: TextToSpeech): SpeechEngineInit {
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setPitch(1.0f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { listener?.onStart(it) }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { listener?.onDone(it) }
            }

            @Deprecated("Requerido por la interfaz; se usa la variante con código de error.")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { listener?.onError(it) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "Error TTS ($errorCode)")
                utteranceId?.let { listener?.onError(it) }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                utteranceId?.let { listener?.onRangeStart(it, start, end) }
            }
        })

        val voice = pickOfflineSpanishVoice(engine)
        if (voice != null) {
            engine.voice = voice
            return SpeechEngineInit.Ready
        }

        // Sin lista de voces utilizable: intentar por idioma, descartando falta de datos.
        return when (engine.setLanguage(Locale.forLanguageTag("es-MX"))) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED ->
                SpeechEngineInit.Unavailable(
                    "Falta la voz en español sin conexión. Instálala desde los ajustes de síntesis de voz.",
                    missingVoiceData = true
                )
            else -> SpeechEngineInit.Ready
        }
    }

    private fun pickOfflineSpanishVoice(engine: TextToSpeech): Voice? {
        val voices = try {
            engine.voices.orEmpty()
        } catch (_: Exception) {
            emptySet()
        }
        val candidates = voices.filter {
            it.locale.language == "es" &&
                !it.isNetworkConnectionRequired &&
                !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }
        return PREFERRED_COUNTRIES.firstNotNullOfOrNull { country ->
            candidates.firstOrNull { it.locale.country == country }
        } ?: candidates.firstOrNull()
    }

    override fun speak(text: String, utteranceId: String, rate: Float) {
        val engine = tts ?: return
        engine.setSpeechRate(rate)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, utteranceId)
        if (result == TextToSpeech.ERROR) listener?.onError(utteranceId)
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private companion object {
        const val TAG = "AndroidSpeechEngine"
        val PREFERRED_COUNTRIES = listOf("MX", "US", "419", "CO", "ES")
    }
}
