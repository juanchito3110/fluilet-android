package com.fluilet.app.core.network

import android.os.Build
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import org.conscrypt.Conscrypt
import java.security.Security

/**
 * Endurecimiento de transporte (OWASP MSTG-NETWORK-1/2/3).
 *
 * - Release con https: SOLO TLS 1.3 (sin cleartext, sin TLS 1.2).
 * - Android 8-9 (API 26-28) no trae TLS 1.3 de plataforma: se inserta Conscrypt como proveedor.
 * - Certificate Pinning (SPKI SHA-256) únicamente cuando hay pines configurados (release).
 */
object TlsConfig {

    fun connectionSpecs(enforceTlsOnly: Boolean): List<ConnectionSpec> {
        val tls13 = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3)
            .build()
        // Debug (o un backend http:// de prueba elegido con FLUILET_API_BASE_URL): se admite HTTP y TLS 1.2/1.3, para
        // conectar con un Droplet recién creado. El network_security_config de cada compilación decide a qué hosts.
        return if (enforceTlsOnly) listOf(tls13) else listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT)
    }

    /**
     * @param pins lista separada por comas de pines `sha256/<base64>`; vacía = sin pinning (debug).
     * Mantener al menos 2 pines (activo + respaldo) para poder rotar certificados sin romper la app.
     */
    fun certificatePinner(host: String, pins: String): CertificatePinner? {
        val list = pins.split(',').map(String::trim).filter(String::isNotEmpty)
        if (list.isEmpty()) return null
        return CertificatePinner.Builder().apply { list.forEach { add(host, it) } }.build()
    }

    /** `true` si algún pin sigue siendo el placeholder (no se debe publicar así). */
    fun hasPlaceholderPins(pins: String): Boolean =
        pins.split(',').any { pin -> pin.trim().removePrefix("sha256/").let { it.isNotEmpty() && it.all { c -> c == 'A' || c == 'B' || c == '=' } } }

    /** Debe llamarse antes del primer uso de OkHttp (Application.attachBaseContext). */
    fun installConscryptIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }
    }
}
