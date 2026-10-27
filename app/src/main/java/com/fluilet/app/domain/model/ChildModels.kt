package com.fluilet.app.domain.model

import java.time.LocalDate

enum class DifficultyType(val wireValue: String, val label: String) {
    DISLEXIA("dislexia", "Dislexia"),
    TDAH("tdah", "TDAH"),
    COMBINADO("combinado", "Dislexia + TDAH");

    companion object {
        fun fromWire(raw: String?): DifficultyType =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: COMBINADO
    }
}

/**
 * Nivel de simplificación NLP, alineado a 1..5 del backend. El backend también acepta los alias
 * BASICO (1), INTERMEDIO (3) y AVANZADO (5); la app siempre envía el entero.
 */
enum class NlpLevel(val value: Int, val label: String, val description: String) {
    NIVEL_1(1, "1 · Muy simple", "Oraciones muy cortas y vocabulario cotidiano."),
    NIVEL_2(2, "2 · Simple", "Oraciones cortas, términos difíciles explicados."),
    NIVEL_3(3, "3 · Intermedio", "Equilibrio entre claridad y contenido original."),
    NIVEL_4(4, "4 · Cercano al original", "Mantiene casi todo el vocabulario original."),
    NIVEL_5(5, "5 · Avanzado", "Conserva la complejidad; solo reorganiza oraciones largas.");

    companion object {
        val DEFAULT = NIVEL_3

        fun fromValue(value: Int?): NlpLevel = entries.firstOrNull { it.value == value } ?: DEFAULT

        /** Acepta enteros ("3") y los alias del contrato (BASICO/INTERMEDIO/AVANZADO). */
        fun fromWire(raw: String?): NlpLevel = when (raw?.trim()?.uppercase()) {
            "BASICO", "BÁSICO" -> NIVEL_1
            "INTERMEDIO" -> NIVEL_3
            "AVANZADO" -> NIVEL_5
            else -> fromValue(raw?.trim()?.toIntOrNull())
        }
    }
}

data class ChildProfile(
    val id: String,
    val name: String,
    val birthDate: LocalDate,
    val difficulty: DifficultyType,
    val nlpLevel: NlpLevel,
    val palette: AccessiblePalette,
    val fontSizeSp: Float,
    val lineSpacing: Float,
    val isActive: Boolean,
    /** Edad didáctica (`6-8`, `9-11`, `12+`) que envía el backend; `null` en servidores anteriores. */
    val ageGroup: String? = null
) {
    /** Edad en años cumplidos. */
    fun ageYears(today: LocalDate = LocalDate.now()): Int = java.time.Period.between(birthDate, today).years

    /** Preferencias de lectura iniciales derivadas del perfil. */
    fun toReadingSettings(): ReadingSettings = ReadingSettings.coerced(
        fontSizeSp = fontSizeSp,
        lineSpacing = lineSpacing,
        palette = palette
    )
}

/** Datos para crear o editar un perfil. `null` en edición = sin cambios. */
data class ChildDraft(
    val name: String,
    val birthDate: LocalDate,
    val difficulty: DifficultyType,
    val nlpLevel: NlpLevel,
    val palette: AccessiblePalette,
    val fontSizeSp: Float,
    val lineSpacing: Float
)
