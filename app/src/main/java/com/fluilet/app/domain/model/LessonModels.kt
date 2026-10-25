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
    val correctOptionIndex: Int,
    /** Pista pedagógica (no penaliza al verla). Sin ella se usa una pista general. */
    val hint: String? = null,
    /** Explicación positiva que se muestra al responder. Sin ella se genera una local. */
    val explanation: String? = null
) {
    init {
        require(options.size >= 2) { "Una pregunta necesita al menos 2 opciones" }
        require(correctOptionIndex in options.indices) { "correctOptionIndex fuera de rango" }
    }
}

/** Pista de respaldo cuando el servidor no la envía. */
val ComprehensionQuestion.effectiveHint: String
    get() = hint?.takeIf { it.isNotBlank() } ?: "Relee con calma el texto y busca las palabras clave de la pregunta."

/** Explicación positiva: la del servidor o una local que nunca desanima. */
fun ComprehensionQuestion.explanationFor(wasCorrect: Boolean): String {
    explanation?.takeIf { it.isNotBlank() }?.let { return it }
    val answer = options[correctOptionIndex]
    return if (wasCorrect) "¡Bien hecho! Encontraste la respuesta correcta: $answer."
    else "No pasa nada, así se aprende: la respuesta era «$answer». ¡Tú puedes con la siguiente!"
}

/** Grupo de edad del mini-reto (`6-8`, `9-11`, `12+`). */
enum class AgeGroup(val code: String) {
    SIX_TO_EIGHT("6-8"), NINE_TO_ELEVEN("9-11"), TWELVE_PLUS("12+");

    /** Los más pequeños usan botones más grandes, bordes más redondeados y tipografía amplia. */
    val isYoungReader: Boolean get() = this == SIX_TO_EIGHT

    companion object {
        fun fromCode(code: String?): AgeGroup? = entries.firstOrNull { it.code == code?.trim() }

        fun fromAge(age: Int?): AgeGroup = when {
            age == null -> TWELVE_PLUS
            age <= 8 -> SIX_TO_EIGHT
            age <= 11 -> NINE_TO_ELEVEN
            else -> TWELVE_PLUS
        }
    }
}
