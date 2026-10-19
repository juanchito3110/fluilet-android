package com.fluilet.app.core.security

/**
 * Almacén clave-valor cifrado en reposo (OWASP MSTG-STORAGE-1). Las capas superiores dependen
 * de esta interfaz, no de `EncryptedSharedPreferences`, para poder sustituir la implementación
 * (p. ej. Keystore + Tink) y para probar la lógica sin el framework Android.
 */
interface SecureStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getLong(key: String, default: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun remove(vararg keys: String)
    fun clear()
}
