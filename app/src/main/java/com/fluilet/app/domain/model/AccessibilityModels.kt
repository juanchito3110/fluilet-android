package com.fluilet.app.domain.model

/**
 * Paletas clínicas de alto confort (WCAG 2.1 AA, contraste >= 4.5:1; validado en pruebas).
 * Nunca se usa blanco puro (#FFFFFF) sobre negro puro (#000000): provocan fatiga visual y el
 * efecto de "vibración" del texto en usuarios con dislexia.
 *
 * El backend solo conoce `estandar`, `sepia` y `alto_contraste` ([backendValue]); las paletas
 * pastel son preferencias locales y se sincronizan como `estandar`.
 */
enum class AccessiblePalette(
    val displayName: String,
    val backendValue: String,
    val isDark: Boolean,
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val primary: Long,
    val onPrimary: Long
) {
    CREMA(
        displayName = "Crema",
        backendValue = "estandar",
        isDark = false,
        background = 0xFFFDFBF7,
        onBackground = 0xFF1E1E1E,
        surface = 0xFFF3EEE3,
        primary = 0xFF5B3E8C,
        onPrimary = 0xFFFFFFFF
    ),
    SEPIA(
        displayName = "Sepia",
        backendValue = "sepia",
        isDark = false,
        background = 0xFFF4ECD8,
        onBackground = 0xFF1E1E1E,
        surface = 0xFFE9DEC2,
        primary = 0xFF7A4B21,
        onPrimary = 0xFFFFFFFF
    ),
    MENTA(
        displayName = "Menta",
        backendValue = "estandar",
        isDark = false,
        background = 0xFFE6F2E9,
        onBackground = 0xFF1E1E1E,
        surface = 0xFFD5E8DB,
        primary = 0xFF1F6B45,
        onPrimary = 0xFFFFFFFF
    ),
    CIELO(
        displayName = "Cielo",
        backendValue = "estandar",
        isDark = false,
        background = 0xFFE6EFF8,
        onBackground = 0xFF1E1E1E,
        surface = 0xFFD3E2F1,
        primary = 0xFF1F4E8C,
        onPrimary = 0xFFFFFFFF
    ),
    DURAZNO(
        displayName = "Durazno",
        backendValue = "estandar",
        isDark = false,
        background = 0xFFFBEADD,
        onBackground = 0xFF1E1E1E,
        surface = 0xFFF3D9C6,
        primary = 0xFF9A4A1A,
        onPrimary = 0xFFFFFFFF
    ),
    ALTO_CONTRASTE(
        displayName = "Alto contraste",
        backendValue = "alto_contraste",
        isDark = true,
        background = 0xFF161616,
        onBackground = 0xFFF0F0F0,
        surface = 0xFF242424,
        primary = 0xFFFFD84D,
        onPrimary = 0xFF1E1E1E
    );

    companion object {
        val DEFAULT = CREMA

        fun fromBackend(raw: String?): AccessiblePalette = when (raw?.lowercase()) {
            "sepia" -> SEPIA
            "alto_contraste" -> ALTO_CONTRASTE
            else -> CREMA
        }
    }
}

/**
 * Preferencias de lectura. Los rangos de tamaño e interlineado coinciden con las restricciones
 * del backend (14–28 pt, 1.5x–2.5x). El espaciado entre caracteres y la velocidad de voz son
 * preferencias locales del dispositivo.
 */
data class ReadingSettings(
    val fontSizeSp: Float = DEFAULT_FONT_SIZE,
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    val letterSpacingEm: Float = DEFAULT_LETTER_SPACING,
    val speechRate: Float = DEFAULT_SPEECH_RATE,
    val palette: AccessiblePalette = AccessiblePalette.DEFAULT
) {
    init {
        require(fontSizeSp in MIN_FONT_SIZE..MAX_FONT_SIZE) { "fontSizeSp fuera de rango" }
        require(lineSpacing in MIN_LINE_SPACING..MAX_LINE_SPACING) { "lineSpacing fuera de rango" }
        require(letterSpacingEm in MIN_LETTER_SPACING..MAX_LETTER_SPACING) { "letterSpacingEm fuera de rango" }
        require(speechRate in MIN_SPEECH_RATE..MAX_SPEECH_RATE) { "speechRate fuera de rango" }
    }

    companion object {
        const val MIN_FONT_SIZE = 14f
        const val MAX_FONT_SIZE = 28f
        const val DEFAULT_FONT_SIZE = 20f
        const val MIN_LINE_SPACING = 1.5f
        const val MAX_LINE_SPACING = 2.5f
        const val DEFAULT_LINE_SPACING = 1.8f
        const val MIN_LETTER_SPACING = 0f
        const val MAX_LETTER_SPACING = 0.2f
        const val DEFAULT_LETTER_SPACING = 0.05f
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 1.5f
        /** Un poco más lento que lo normal: ritmo adecuado para lectores en formación. */
        const val DEFAULT_SPEECH_RATE = 0.85f

        /** Construye ajustando cada valor a su rango: nunca confiamos en el valor crudo de la UI. */
        fun coerced(
            fontSizeSp: Float = DEFAULT_FONT_SIZE,
            lineSpacing: Float = DEFAULT_LINE_SPACING,
            letterSpacingEm: Float = DEFAULT_LETTER_SPACING,
            speechRate: Float = DEFAULT_SPEECH_RATE,
            palette: AccessiblePalette = AccessiblePalette.DEFAULT
        ) = ReadingSettings(
            fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE),
            lineSpacing = lineSpacing.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING),
            letterSpacingEm = letterSpacingEm.coerceIn(MIN_LETTER_SPACING, MAX_LETTER_SPACING),
            speechRate = speechRate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE),
            palette = palette
        )
    }
}
