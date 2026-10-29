package com.fluilet.app.core.network

import com.fluilet.app.data.remote.dto.ErrorBodyDto
import com.google.gson.Gson
import com.google.gson.JsonArray
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/** Errores tipados de red/negocio con mensaje apto para mostrar al usuario (sin jerga técnica). */
sealed class AppError(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause) {
    class NoConnection(cause: Throwable? = null) :
        AppError("No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.", cause)

    class Timeout(cause: Throwable? = null) :
        AppError("El servidor tardó demasiado en responder. Inténtalo de nuevo.", cause)

    /** Fallo de certificado/pinning: no se reintenta a ciegas. */
    class InsecureConnection(cause: Throwable? = null) :
        AppError("No se pudo establecer una conexión segura con el servidor.", cause)

    class Unauthorized(message: String = "Credenciales inválidas o sesión vencida.") : AppError(message)
    class Forbidden(message: String = "No tienes permisos para realizar esta acción.") : AppError(message)
    class Locked(message: String = "Cuenta bloqueada temporalmente por múltiples intentos fallidos.") : AppError(message)
    class NotFound(message: String = "No encontramos lo que buscas.") : AppError(message)
    class Conflict(message: String) : AppError(message)
    /** [httpStatus]: 400 o 422 cuando viene del servidor; `null` si la detectó la propia app. */
    class Validation(message: String, val httpStatus: Int? = null) : AppError(message)

    /** Código de recuperación incorrecto, vencido, ya usado o bloqueado (HTTP 400 de la recuperación de contraseña). */
    class InvalidResetCode(message: String = "El código es incorrecto, venció o ya no está disponible. Solicita uno nuevo si es necesario.") :
        AppError(message)
    class Server(cause: Throwable? = null) :
        AppError("El servidor tuvo un problema. Inténtalo de nuevo en unos minutos.", cause)

    class TooManyRequests(message: String = "Demasiadas solicitudes. Espera un momento e inténtalo de nuevo.") : AppError(message)
    class Busy(message: String = "El servidor está procesando otros documentos. Inténtalo de nuevo en unos segundos.") : AppError(message)
    class TooLarge(message: String = "El documento es demasiado grande (máximo 5 MB).") : AppError(message)
    class Unsupported(message: String) : AppError(message)
    class Unknown(cause: Throwable? = null) : AppError("Ocurrió un error inesperado.", cause)
}

private val errorGson = Gson()

/** Ejecuta una llamada de red y la convierte en [Result] con un [AppError] tipado. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e.toAppError())
}

fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is HttpException -> mapHttp(this)
    is SocketTimeoutException -> AppError.Timeout(this)
    is SSLException -> AppError.InsecureConnection(this)
    is IOException -> AppError.NoConnection(this)
    else -> AppError.Unknown(this)
}

private fun mapHttp(e: HttpException): AppError {
    val detail = e.parseDetail()
    return when (e.code()) {
        400, 422 -> AppError.Validation(detail ?: "Revisa los datos ingresados.", e.code())
        401 -> AppError.Unauthorized(detail ?: "Credenciales inválidas o sesión vencida.")
        403 -> AppError.Forbidden(detail ?: "No tienes permisos para realizar esta acción.")
        404 -> AppError.NotFound(detail ?: "No encontramos lo que buscas.")
        409 -> AppError.Conflict(detail ?: "No fue posible completar la operación.")
        413 -> AppError.TooLarge()
        415 -> AppError.Unsupported("Solo se admiten archivos PDF o TXT (UTF-8).")
        429 -> AppError.TooManyRequests()
        503 -> AppError.Busy()
        423 -> AppError.Locked(detail ?: "Cuenta bloqueada temporalmente por múltiples intentos fallidos.")
        in 500..599 -> AppError.Server(e)
        else -> AppError.Unknown(e)
    }
}

private val FIELD_MESSAGES = mapOf(
    "email" to "El correo no es válido o su dominio no está permitido.",
    "password" to "La contraseña no cumple los requisitos de seguridad.",
    "full_name" to "El nombre no es válido.",
    "name" to "El nombre no es válido.",
    "role" to "Ese tipo de cuenta no está permitido.",
    "birth_date" to "La fecha de nacimiento debe corresponder a un niño de 6 a 11 años.",
    "nivel_simplificacion_nlp" to "El nivel de simplificación no es válido.",
    "text" to "El texto no es válido.",
    "pin" to "El PIN no es válido.",
    "code" to "El código debe contener exactamente 6 dígitos numéricos.",
    "new_password" to "La contraseña nueva no cumple los requisitos: 10 a 72 caracteres, mayúscula, minúscula, número y símbolo, sin espacios."
)

/**
 * Con 422 el backend responde `{"detail":"Solicitud inválida.","errors":[{"type","loc"}]}` (sin eco de
 * valores). Se traduce el primer campo señalado en `loc` a un mensaje útil para el usuario.
 */
internal fun fieldMessageFromErrors(errors: com.google.gson.JsonElement?): String? {
    if (errors == null || !errors.isJsonArray) return null
    for (item in errors.asJsonArray) {
        val loc = item.takeIf { it.isJsonObject }?.asJsonObject?.get("loc")?.takeIf { it.isJsonArray } ?: continue
        val field = loc.asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonPrimitive && e.asJsonPrimitive.isString }?.asString }
            .lastOrNull { it !in setOf("body", "query", "path", "header") }
        FIELD_MESSAGES[field]?.let { return it }
    }
    return null
}

/** Extrae `detail` de FastAPI: texto plano o lista de errores de validación (`msg`). */
private fun HttpException.parseDetail(): String? = try {
    val raw = response()?.errorBody()?.string().orEmpty()
    val body = errorGson.fromJson(raw, ErrorBodyDto::class.java)
    val detail = body?.detail
    when {
        fieldMessageFromErrors(body?.errors) != null -> fieldMessageFromErrors(body?.errors)
        detail == null || detail.isJsonNull -> null
        detail.isJsonPrimitive -> detail.asString
        detail is JsonArray -> detail.mapNotNull { item ->
            item.asJsonObject.get("msg")?.asString?.removePrefix("Value error, ")
        }.joinToString(" ").ifBlank { null }
        else -> null
    }
} catch (_: Exception) {
    null
}
