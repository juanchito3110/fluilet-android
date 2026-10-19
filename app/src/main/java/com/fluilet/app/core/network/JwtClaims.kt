package com.fluilet.app.core.network

import com.fluilet.app.domain.model.UserRole
import com.google.gson.JsonParser
import java.util.Base64

/**
 * Lectura NO verificada del payload del JWT, solo para decidir la UI (rol). La autorización real
 * siempre la impone el backend en cada petición.
 */
object JwtClaims {

    fun role(token: String): UserRole? = claim(token, "role")?.let(UserRole::fromWire)

    fun claim(token: String, name: String): String? = try {
        val payload = token.split('.').getOrNull(1) ?: return null
        val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        val element = JsonParser.parseString(json).asJsonObject.get(name)
        if (element == null || element.isJsonNull) null else element.asString
    } catch (_: Exception) {
        null
    }
}
