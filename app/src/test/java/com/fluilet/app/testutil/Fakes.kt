package com.fluilet.app.testutil

import com.fluilet.app.core.security.SecureStore
import com.fluilet.app.core.tts.SpeechEngine
import com.fluilet.app.core.tts.SpeechEngineInit
import com.fluilet.app.core.tts.SpeechEngineListener

class FakeSecureStore : SecureStore {
    val data = mutableMapOf<String, Any>()
    override fun getString(key: String): String? = data[key] as? String
    override fun putString(key: String, value: String) { data[key] = value }
    override fun getLong(key: String, default: Long): Long = data[key] as? Long ?: default
    override fun putLong(key: String, value: Long) { data[key] = value }
    override fun remove(vararg keys: String) { keys.forEach(data::remove) }
    override fun clear() = data.clear()
}

/** Motor de voz de prueba: registra lo que se habla y deja disparar eventos a mano. */
class FakeSpeechEngine(private val init: SpeechEngineInit = SpeechEngineInit.Ready) : SpeechEngine {
    data class Spoken(val text: String, val id: String, val rate: Float)

    override var listener: SpeechEngineListener? = null
    val spoken = mutableListOf<Spoken>()
    var stopCount = 0
    var shutdownCalled = false

    override fun initialize(onResult: (SpeechEngineInit) -> Unit) = onResult(init)
    override fun speak(text: String, utteranceId: String, rate: Float) { spoken += Spoken(text, utteranceId, rate) }
    override fun stop() { stopCount++ }
    override fun shutdown() { shutdownCalled = true }

    val last: Spoken get() = spoken.last()
    fun range(start: Int, end: Int, id: String = last.id) = listener!!.onRangeStart(id, start, end)
    fun done(id: String = last.id) = listener!!.onDone(id)
    fun error(id: String = last.id) = listener!!.onError(id)
}
