package com.fluilet.app.core.tts

/** Eventos del motor de voz (se invocan desde hilos del motor, no del principal). */
interface SpeechEngineListener {
    fun onStart(utteranceId: String)
    /** Rango [start, end) de la palabra que el motor pronuncia ahora, relativo al texto enviado. */
    fun onRangeStart(utteranceId: String, start: Int, end: Int)
    fun onDone(utteranceId: String)
    fun onError(utteranceId: String)
}

sealed interface SpeechEngineInit {
    data object Ready : SpeechEngineInit
    /** El motor no puede hablar en español sin conexión. */
    data class Unavailable(val reason: String, val missingVoiceData: Boolean = false) : SpeechEngineInit
}

/** Abstracción mínima del motor TTS: permite probar la lógica de pausa/resaltado sin Android. */
interface SpeechEngine {
    var listener: SpeechEngineListener?
    fun initialize(onResult: (SpeechEngineInit) -> Unit)
    /** Habla [text] reemplazando cualquier locución en curso. */
    fun speak(text: String, utteranceId: String, rate: Float)
    fun stop()
    fun shutdown()
}
