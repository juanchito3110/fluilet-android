package com.fluilet.app.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Implementación sobre [EncryptedSharedPreferences] con [MasterKey] AES-256-GCM
 * (claves: AES256_SIV, valores: AES256_GCM). Guarda JWT, rol activo, hash+sal del PIN del tutor
 * y contadores de intentos.
 *
 * Si el Keystore queda inconsistente (restauración de dispositivo, borrado de credenciales),
 * el archivo cifrado ya no es legible: se descarta y se recrea, lo que equivale a cerrar sesión.
 */
@Suppress("DEPRECATION") // security-crypto está deprecada; el uso queda aislado tras [SecureStore] para poder migrar a Keystore + Tink.
class EncryptedPrefsSecureStore(private val context: Context) : SecureStore {

    private val prefs: SharedPreferences by lazy { openOrReset() }

    private fun openOrReset(): SharedPreferences = try {
        create()
    } catch (e: GeneralSecurityException) {
        resetAndCreate(e)
    } catch (e: IOException) {
        resetAndCreate(e)
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun resetAndCreate(cause: Exception): SharedPreferences {
        Log.w(TAG, "Almacén cifrado ilegible; se recrea (la sesión se pierde).", cause)
        context.deleteSharedPreferences(FILE_NAME)
        return create()
    }

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun remove(vararg keys: String) {
        prefs.edit().apply { keys.forEach(::remove) }.apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "fluilet_secure_prefs"
        const val TAG = "SecureStore"
    }
}
