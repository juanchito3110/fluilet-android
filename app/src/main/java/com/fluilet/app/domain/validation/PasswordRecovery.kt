package com.fluilet.app.domain.validation

/**
 * Código de recuperación de 6 dígitos que llega por correo. Se puede teclear casilla por casilla o pegar tal cual
 * desde el correo ("Tu código es 048 213", "Código: 048213. Vence en 10 minutos."): aquí se extraen solo los dígitos.
 */
object ResetCode {

    const val LENGTH = 6
    private val ISOLATED = Regex("(?<![0-9])[0-9]{6}(?![0-9])")
    // "048 213" o "0-4-8-2-1-3", pero no un trozo de un número más largo como un teléfono ("300 123 4567").
    private val SPACED = Regex("(?<![0-9][ \\u00A0\\-·.]?)[0-9](?:[ \\u00A0\\-·.]?[0-9]){5}(?![ \\u00A0\\-·.]?[0-9])")

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in '0'..'9' }

    private fun digits(text: String): String = text.filter { it in '0'..'9' }

    /**
     * El código contenido en un texto pegado: un grupo aislado de 6 dígitos, o 6 dígitos separados por espacios o
     * guiones ("048 213", "0-4-8-2-1-3"). `null` si el texto no contiene ninguno (p. ej. un enlace o un teléfono).
     */
    fun find(text: String): String? =
        ISOLATED.find(text)?.value ?: SPACED.find(text)?.value?.let(::digits)?.takeIf(::isValid)

    /**
     * Nuevo valor de las 6 casillas tras un cambio del campo: [previous] es lo que había y [incoming] lo que propone el
     * teclado. Si lo insertado trae un código completo (pegado sobre casillas vacías o a medio llenar) se usa ese código;
     * si no, se conservan los dígitos tecleados hasta llenar las 6 casillas.
     */
    fun merge(previous: String, incoming: String): String {
        val prefix = previous.commonPrefixWith(incoming).length
        val suffix = previous.drop(prefix).commonSuffixWith(incoming.drop(prefix)).length
        val inserted = incoming.substring(prefix, incoming.length - suffix)
        if (inserted.length > 1) find(inserted)?.let { return it }
        return digits(incoming).take(LENGTH)
    }
}

/** Requisitos de la contraseña nueva, idénticos a `validar_password_fuerte` del backend, para la lista visual. */
enum class PasswordRequirement {
    /** 10–72 caracteres y como máximo 72 bytes UTF-8 (límite de bcrypt). */
    LENGTH,
    UPPERCASE,
    LOWERCASE,
    DIGIT,
    SYMBOL,
    NO_SPACES;

    fun isMet(password: String): Boolean = when (this) {
        LENGTH -> password.length in 10..72 && password.toByteArray(Charsets.UTF_8).size <= 72
        UPPERCASE -> password.any { it in 'A'..'Z' }
        LOWERCASE -> password.any { it in 'a'..'z' }
        DIGIT -> password.any(Char::isDigit)
        // `[^\w\s]` de Python: ni letra, ni dígito, ni guion bajo, ni espacio.
        SYMBOL -> password.any { !it.isLetterOrDigit() && it != '_' && !it.isWhitespace() }
        NO_SPACES -> password.isNotEmpty() && password.none(Char::isWhitespace)
    }

    companion object {
        /** Fracción de requisitos cumplidos (para la barra de seguridad). */
        fun progress(password: String): Float = entries.count { it.isMet(password) }.toFloat() / entries.size
    }
}
