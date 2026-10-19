package com.fluilet.app.domain.validation

import java.time.LocalDate
import java.time.Period

/** Validaciones de entrada espejo de las reglas del backend (schemas.py), con mensajes claros. */
object Validators {

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val COMMON_PASSWORDS = setOf("12345678", "password", "qwerty123", "contrasena", "fluilet123")

    const val MIN_AGE = 6
    const val MAX_AGE = 11
    const val PIN_MIN = 4
    const val PIN_MAX = 6

    fun email(value: String): String? =
        if (EMAIL.matches(value.trim())) null else "Ingresa un correo válido."

    fun fullName(value: String): String? =
        if (value.trim().length in 2..150) null else "El nombre debe tener entre 2 y 150 caracteres."

    /** Misma política que el backend: 10–72 caracteres, mayúscula, minúscula, dígito y símbolo, sin espacios. */
    fun strongPassword(value: String): String? = when {
        value.length !in 10..72 -> "La contraseña debe tener entre 10 y 72 caracteres."
        value.contains(' ') -> "La contraseña no puede contener espacios."
        value.none(Char::isUpperCase) -> "Incluye al menos una letra mayúscula."
        value.none(Char::isLowerCase) -> "Incluye al menos una letra minúscula."
        value.none(Char::isDigit) -> "Incluye al menos un dígito."
        value.all { it.isLetterOrDigit() || it == '_' } -> "Incluye al menos un carácter especial."
        value.lowercase() in COMMON_PASSWORDS -> "Esta contraseña es demasiado común."
        else -> null
    }

    fun childName(value: String): String? =
        if (value.trim().length in 2..100) null else "El nombre debe tener entre 2 y 100 caracteres."

    /** Población objetivo del backend: niños de 6 a 11 años. */
    fun childBirthDate(value: LocalDate, today: LocalDate = LocalDate.now()): String? {
        if (value.isAfter(today)) return "La fecha de nacimiento no puede ser futura."
        val age = Period.between(value, today).years
        return if (age in MIN_AGE..MAX_AGE) null else "Fluilet está diseñado para niños de $MIN_AGE a $MAX_AGE años."
    }

    /** PIN del tutor: 4–6 dígitos, sin repetir un mismo dígito ni secuencias triviales. */
    fun tutorPin(value: String): String? {
        if (value.length !in PIN_MIN..PIN_MAX || !value.all(Char::isDigit)) return "El PIN debe tener de $PIN_MIN a $PIN_MAX dígitos."
        if (value.toSet().size == 1) return "Evita repetir el mismo dígito."
        val asc = "0123456789"
        val desc = "9876543210"
        if (asc.contains(value) || desc.contains(value)) return "Evita secuencias como 1234."
        return null
    }
}
