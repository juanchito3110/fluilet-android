package com.fluilet.app.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Motor nativo `android.speech.tts.TextToSpeech` en español, solo para el reproductor del lector.
 *
 * - Primero usa el motor predeterminado del teléfono; si no inicia, no tiene español o no llega a sonar, se reintenta
 *   una vez con el motor de Google ([GOOGLE_TTS]), que viene en casi todos los Android.
 * - Elige una voz instalada que no requiera red y, si no hay, prueba el español en cascada ([SpanishVoices]).
 * - El audio sale como contenido hablado de medios (AudioAttributes y `KEY_PARAM_STREAM`), con un `utteranceId` siempre
 *   presente y el texto limpio de saltos de línea y caracteres invisibles ([SpeechText.clean]).
 * - Un vigilante tolerante: cualquier señal de síntesis (inicio, síntesis, audio, palabra o fin) cuenta como "sonando";
 *   solo una locución sin ninguna señal en [START_TIMEOUT_MS] pasa al motor de Google o se informa como error.
 *
 * Requiere el `<queries>` de TTS_SERVICE y del paquete de Google en el manifiesto (Android 11+).
 */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {

    override var listener: SpeechEngineListener? = null

    private var tts: TextToSpeech? = null
    private var enginePackage: String? = null
    private val main = Handler(Looper.getMainLooper())

    /** Locuciones con alguna señal de síntesis (el vigilante solo actúa sobre las que siguen en silencio). */
    private val started = mutableSetOf<String>()

    /** Última locución pedida, para repetirla tal cual tras cambiar al motor de Google. */
    private var lastRequest: Triple<String, String, Float>? = null

    override fun initialize(onResult: (SpeechEngineInit) -> Unit) = start(null, onResult)

    /**
     * Crea el motor ([pkg] `null` = el predeterminado) y entrega el resultado a [onResult]; si falla sin haber probado
     * Google, reintenta antes con Google.
     */
    private fun start(pkg: String?, onResult: (SpeechEngineInit) -> Unit) {
        enginePackage = pkg
        tts = TextToSpeech(context, { status ->
            val engine = tts
            val result = if (status != TextToSpeech.SUCCESS || engine == null) {
                SpeechEngineInit.Unavailable("No se pudo iniciar el motor de voz del teléfono.")
            } else configure(engine)
            if (result is SpeechEngineInit.Unavailable && canSwitchToGoogle(engine)) {
                Log.i(TAG, "Motor predeterminado sin español utilizable: se reintenta con Google")
                engine?.shutdown()
                start(GOOGLE_TTS, onResult)
            } else {
                onResult(result)
            }
        }, pkg)
    }

    /** Hay motor de Google instalado y el actual no lo es. */
    private fun canSwitchToGoogle(engine: TextToSpeech?): Boolean {
        if (enginePackage == GOOGLE_TTS) return false
        val current = engine?.defaultEngine
        if (current == GOOGLE_TTS) return false
        return try {
            engine?.engines?.any { it.name == GOOGLE_TTS } ?: isInstalled(GOOGLE_TTS)
        } catch (_: Exception) {
            isInstalled(GOOGLE_TTS)
        }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
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
                markStarted(utteranceId)
                listener?.onStart(utteranceId)
            }

            // Algunos motores OEM avisan la síntesis o el audio antes (o en lugar) de onStart.
            override fun onBeginSynthesis(utteranceId: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
                utteranceId?.let(::markStarted)
            }

            override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                utteranceId?.let(::markStarted)
            }

            override fun onDone(utteranceId: String?) {
                utteranceId ?: return
                markStarted(utteranceId)
                listener?.onDone(utteranceId)
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
                utteranceId ?: return
                markStarted(utteranceId)
                listener?.onRangeStart(utteranceId, start, end)
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
            "Falta la voz en español. Activa o instala el motor de voz del teléfono.",
            missingVoiceData = TextToSpeech.LANG_MISSING_DATA in results
        )
    }

    private fun markStarted(utteranceId: String) {
        synchronized(started) { started += utteranceId }
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
        lastRequest = Triple(text, utteranceId, rate)
        engine.setSpeechRate(rate)
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
        }
        val result = engine.speak(SpeechText.clean(text), TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            listener?.onError(utteranceId)
            return
        }
        main.postDelayed({ checkSilent(utteranceId) }, START_TIMEOUT_MS)
    }

    /** La locución no dio ninguna señal: se intenta una vez con el motor de Google; si ya era Google, es un error. */
    private fun checkSilent(utteranceId: String) {
        val silent = synchronized(started) { !started.remove(utteranceId) }
        if (!silent || tts == null || lastRequest?.second != utteranceId) return
        val engine = tts
        if (canSwitchToGoogle(engine)) {
            Log.i(TAG, "Sin audio del motor predeterminado: se cambia al motor de Google")
            val (text, id, rate) = lastRequest ?: return
            engine?.stop()
            engine?.shutdown()
            // El lector sigue "reproduciendo": la misma locución (mismo id) se repite con Google al estar listo.
            start(GOOGLE_TTS) { result -> if (result == SpeechEngineInit.Ready) speak(text, id, rate) else listener?.onError(id) }
        } else {
            listener?.onError(utteranceId)
        }
    }

    override fun stop() {
        main.removeCallbacksAndMessages(null)
        synchronized(started) { started.clear() }
        lastRequest = null
        tts?.stop()
    }

    override fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val TAG = "AndroidSpeechEngine"
        const val GOOGLE_TTS = "com.google.android.tts"

        /** Tiempo máximo sin ninguna señal de síntesis antes de cambiar de motor o informar el error. */
        const val START_TIMEOUT_MS = 10_000L
    }
}

/** Texto apto para el motor de voz sin mover posiciones (el resaltado de palabras usa los mismos índices). */
object SpeechText {
    /**
     * Cambia saltos de línea, tabuladores, espacios raros y caracteres invisibles (de control o de formato, como el
     * espacio de ancho cero) por espacios normales: misma longitud, mismas posiciones de cada palabra.
     */
    fun clean(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when {
                    c == '\n' || c == '\r' || c == '\t' -> ' '
                    Character.isSpaceChar(c) -> ' '
                    Character.getType(c) == Character.CONTROL.toInt() || Character.getType(c) == Character.FORMAT.toInt() -> ' '
                    else -> c
                }
            )
        }
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
