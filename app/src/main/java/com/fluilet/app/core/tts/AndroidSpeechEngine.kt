package com.fluilet.app.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Motor nativo `android.speech.tts.TextToSpeech` en español, solo para el reproductor del lector. Primero busca
 * una voz instalada que NO requiera red ([Voice.isNetworkConnectionRequired] == false); si no hay, prueba el
 * español en cascada ([SpanishVoices]). El audio sale como contenido hablado de medios (AudioAttributes) y el
 * resaltado palabra por palabra llega por `onRangeStart` (API 26+). Requiere el `<queries>` de TTS_SERVICE del
 * manifiesto en Android 11+.
 */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {

    override var listener: SpeechEngineListener? = null

    private var tts: TextToSpeech? = null
    private val watchdog = Handler(Looper.getMainLooper())
    /** Locuciones que ya empezaron a sonar (el vigilante solo avisa de las que siguen en silencio). */
    private val started = mutableSetOf<String>()

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
                utteranceId ?: return
                synchronized(started) { started += utteranceId }
                listener?.onStart(utteranceId)
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

        // Sin voz instalada utilizable: español en cascada (México, EE. UU., Latinoamérica, España y genérico).
        val results = mutableListOf<Int>()
        val chosen = SpanishVoices.firstSupported { locale -> engine.setLanguage(locale).also(results::add) }
        return if (chosen != null) SpeechEngineInit.Ready
        else SpeechEngineInit.Unavailable(
            "Falta la voz en español. Instálala desde los ajustes de síntesis de voz del teléfono.",
            missingVoiceData = TextToSpeech.LANG_MISSING_DATA in results
        )
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
        return SpanishVoices.COUNTRIES.firstNotNullOfOrNull { country ->
            candidates.firstOrNull { it.locale.country == country }
        } ?: candidates.firstOrNull()
    }

    override fun speak(text: String, utteranceId: String, rate: Float) {
        val engine = tts ?: return
        engine.setSpeechRate(rate)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, utteranceId)
        if (result == TextToSpeech.ERROR) {
            listener?.onError(utteranceId)
            return
        }
        // Algunos teléfonos aceptan la locución y nunca la reproducen (motor sin enlazar o sin audio): si no empieza
        // a sonar a tiempo se informa como error en lugar de mostrar "reproduciendo" en silencio.
        watchdog.postDelayed({
            val silent = synchronized(started) { !started.remove(utteranceId) }
            if (silent && tts != null) listener?.onError(utteranceId)
        }, START_TIMEOUT_MS)
    }

    override fun stop() {
        watchdog.removeCallbacksAndMessages(null)
        synchronized(started) { started.clear() }
        tts?.stop()
    }

    override fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private companion object {
        const val TAG = "AndroidSpeechEngine"
        /** Tiempo máximo para que una locución empiece a sonar. */
        const val START_TIMEOUT_MS = 6_000L
    }
}

/** Español en cascada para el motor de voz: México, EE. UU., Latinoamérica, España y, al final, español genérico. */
object SpanishVoices {

    val LOCALES: List<Locale> = listOf(
        Locale.forLanguageTag("es-MX"),
        Locale.forLanguageTag("es-US"),
        Locale.forLanguageTag("es-419"),
        Locale.forLanguageTag("es-ES"),
        Locale.forLanguageTag("es")
    )

    /** Países preferidos al elegir entre las voces instaladas. */
    val COUNTRIES = listOf("MX", "US", "419", "CO", "ES")

    /**
     * Primer idioma que el motor acepta. [setLanguage] devuelve el código de `TextToSpeech.setLanguage`: 0, 1 o 2
     * significan disponible (idioma, país o variante); los negativos, sin datos o no admitido.
     */
    fun firstSupported(setLanguage: (Locale) -> Int): Locale? = LOCALES.firstOrNull { setLanguage(it) >= 0 }
}
