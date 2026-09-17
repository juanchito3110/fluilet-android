package com.fluilet.app.domain.model

/** Paletas de color accesibles — cumplen WCAG 2.1 AA (ratio de contraste >= 4.5:1). */
enum class AccessiblePalette(
    val displayName: String,
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val primary: Long,
    val onPrimary: Long
) {
    ESTANDAR(
        displayName = "Estándar",
        background = 0xFFFFFFFF,
        onBackground = 0xFF1A1A1A, // ~16.1:1 sobre fondo blanco
        surface = 0xFFF5F5F7,
        primary = 0xFF6A3FA0,      // ~5.1:1 sobre fondo blanco
        onPrimary = 0xFFFFFFFF
    ),
    ALTO_CONTRASTE(
        displayName = "Alto contraste",
        background = 0xFF000000,
        onBackground = 0xFFFFFFFF, // 21:1
        surface = 0xFF121212,
        primary = 0xFFFFD400,      // ~17.9:1 sobre negro
        onPrimary = 0xFF000000
    ),
    SEPIA(
        displayName = "Sepia (baja estimulación)",
        background = 0xFFF4ECD8,
        onBackground = 0xFF3B2F1E, // ~9.4:1
        surface = 0xFFEDE0C4,
        primary = 0xFF8B5E34,
        onPrimary = 0xFFFFFFFF
    )
}

enum class NlpLevel(val displayName: String, val description: String) {
    BASICO("Básico", "Simplificación léxica máxima y oraciones cortas."),
    INTERMEDIO("Intermedio", "Controla el nivel de profundidad de la simplificación semántica."),
    AVANZADO("Avanzado", "Conserva mayor complejidad semántica del texto original.")
}

enum class UserRole { TUTOR, NINO, ADULTO_INDEPENDIENTE }

/**
 * Estado de lectura validado en el propio modelo de dominio: cualquier instancia
 * fuera de rango es imposible de construir (invariante RF05).
 */
data class ReadingSettings(
    val lineSpacing: Float = MIN_LINE_SPACING,
    val fontSizeSp: Float = MIN_FONT_SIZE,
    val palette: AccessiblePalette = AccessiblePalette.ESTANDAR
) {
    init {
        require(lineSpacing in MIN_LINE_SPACING..MAX_LINE_SPACING) {
            "lineSpacing debe estar entre $MIN_LINE_SPACING y $MAX_LINE_SPACING"
        }
        require(fontSizeSp in MIN_FONT_SIZE..MAX_FONT_SIZE) {
            "fontSizeSp debe estar entre $MIN_FONT_SIZE y $MAX_FONT_SIZE"
        }
    }

    companion object {
        const val MIN_LINE_SPACING = 1.5f
        const val MAX_LINE_SPACING = 2.5f
        const val MIN_FONT_SIZE = 14f
        const val MAX_FONT_SIZE = 28f
    }
}

data class SessionProgress(
    val sessionLabel: String,
    val accumulatedMinutes: Float,
    val completionPercent: Int
) {
    init {
        require(sessionLabel.isNotBlank()) { "sessionLabel no puede estar vacío" }
        require(completionPercent in 0..100) { "completionPercent fuera de rango 0..100" }
        require(accumulatedMinutes >= 0f) { "accumulatedMinutes no puede ser negativo" }
    }
}

data class RbacRoleState(
    val role: UserRole,
    val label: String,
    val enabled: Boolean
)