package com.fluilet.app.core.tts

import android.content.Context
import androidx.core.content.edit

/** Recuerda en el teléfono que la voz en español ya sonó bien una vez (prueba o lectura). */
interface VoiceCheckStore {
    fun isVerified(): Boolean
    fun markVerified()
}

/** SharedPreferences (dato no sensible): sobrevive al cierre de la app. */
class PrefsVoiceCheckStore(context: Context) : VoiceCheckStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    override fun isVerified(): Boolean = prefs.getBoolean(KEY_VERIFIED, false)
    override fun markVerified() = prefs.edit { putBoolean(KEY_VERIFIED, true) }

    private companion object {
        const val PREFS = "fluilet_voice"
        const val KEY_VERIFIED = "spanish_voice_verified"
    }
}

/** Para pruebas: solo en memoria. */
class InMemoryVoiceCheckStore(private var verified: Boolean = false) : VoiceCheckStore {
    override fun isVerified(): Boolean = verified
    override fun markVerified() {
        verified = true
    }
}
