package com.fluilet.app.domain.text

/**
 * Limpieza de las "Palabras clave de esta lectura" en el cliente: descarta las defectuosas (como una "v" suelta, menos
 * de [MIN_LENGTH] letras o sin ninguna letra), quita espacios sobrantes y repetidas (sin importar mayúsculas ni tildes).
 */
object Keywords {

    const val MIN_LENGTH = 2

    fun isUsable(word: String): Boolean {
        val clean = word.trim()
        return clean.count { it.isLetterOrDigit() } >= MIN_LENGTH && clean.any { it.isLetter() }
    }

    fun clean(entries: List<VocabularyEntry>): List<VocabularyEntry> = entries
        .map { it.copy(word = it.word.trim().replace(Regex("\\s+"), " ")) }
        .filter { isUsable(it.word) && it.definition.isNotBlank() }
        .distinctBy { LocalGlossary.normalize(it.word) }
}
