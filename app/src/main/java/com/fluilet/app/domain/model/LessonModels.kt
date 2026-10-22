package com.fluilet.app.domain.model

/** Categorías del generador de lecciones (códigos del backend en `POST /adaptations/generate`). */
enum class LessonCategory(val code: String, val displayName: String) {
    CIENCIA_ESPACIO("ciencia_espacio", "Ciencia y Espacio"),
    ANIMALES_NATURALEZA("animales_naturaleza", "Animales y Naturaleza"),
    HISTORIA_MISTERIOS("historia_misterios", "Historia y Misterios"),
    DEPORTES("deportes", "Deportes"),
    TECNOLOGIA("tecnologia", "Tecnología"),
    CUENTOS_AVENTURAS("cuentos_aventuras", "Cuentos y Aventuras");

    companion object {
        fun fromCode(code: String?): LessonCategory? = entries.firstOrNull { it.code == code?.trim()?.lowercase() }

        /** Nombre legible de cualquier código de categoría, incluida la lectura libre (texto propio). */
        fun displayNameFor(code: String?): String = when {
            code.isNullOrBlank() || code == "lectura_libre" -> "Lectura libre"
            else -> fromCode(code)?.displayName ?: code.replace('_', ' ').replaceFirstChar(Char::uppercaseChar)
        }
    }
}

/** Petición del generador de lecciones. El tema es opcional (hasta 120 caracteres). */
data class LessonRequest(
    val category: LessonCategory,
    val topic: String?,
    val level: NlpLevel
) {
    /** Tema normalizado: espacios colapsados, sin caracteres no imprimibles, máximo 120; `null` si queda vacío. */
    val cleanTopic: String?
        get() = topic
            ?.filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,;:¿?¡!-()'\"" }
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.take(MAX_TOPIC_LENGTH)
            ?.takeIf { it.isNotEmpty() }

    companion object {
        const val MAX_TOPIC_LENGTH = 120
    }
}

/** Pregunta de opción múltiple del mini-reto (3 opciones; una correcta). */
data class ComprehensionQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctOptionIndex: Int
) {
    init {
        require(options.size >= 2) { "Una pregunta necesita al menos 2 opciones" }
        require(correctOptionIndex in options.indices) { "correctOptionIndex fuera de rango" }
    }
}
