package com.fluilet.app.presentation.home

import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LibraryItem

/**
 * Filtros rápidos de la biblioteca: por banda de nivel ("Inicial", "Intermedio", "Avanzado") o solo las lecturas
 * guardadas en el servidor. Una lectura sin nivel solo aparece en "Todas" y, si está guardada, en "Guardadas".
 */
enum class LibraryQuickFilter(val title: String, val detail: String? = null, val levels: IntRange? = null, val savedOnly: Boolean = false) {
    ALL("Todas"),
    INICIAL("Inicial", "Niv. 1–2", 1..2),
    INTERMEDIO("Intermedio", "Niv. 3", 3..3),
    AVANZADO("Avanzado", "Niv. 4–5", 4..5),
    GUARDADAS("Guardadas", savedOnly = true);

    /** Nombre completo, como "Inicial (Niv. 1–2)". */
    val label: String get() = if (detail != null) "$title ($detail)" else title

    /** Segundo renglón del chip: la banda de nivel y cuántas lecturas deja ver. */
    fun detailLine(count: Int): String = if (detail != null) "$detail · $count" else "$count"

    fun matches(entry: ShelfEntry): Boolean = when {
        savedOnly -> entry.item != null
        levels == null -> true
        else -> entry.level != null && entry.level in levels
    }
}

/** Filtros de la biblioteca: categoría (`null` = todas), nivel 1-5 (`null` = todos) y filtro rápido. */
data class LibraryFilter(
    val category: String? = null,
    val level: Int? = null,
    val quick: LibraryQuickFilter = LibraryQuickFilter.ALL
) {
    fun matches(category: String?, level: Int?): Boolean =
        (this.category == null || this.category == category) && (this.level == null || this.level == level)

    fun matches(entry: ShelfEntry): Boolean = matches(entry.category, entry.level) && quick.matches(entry)
}

/** Una lectura de la biblioteca: guardada en el servidor ([item]) o cargada como demo en el teléfono ([lesson]). */
data class ShelfEntry(
    val key: String,
    val category: String,
    val level: Int?,
    val title: String,
    val originalScore: Double?,
    val adaptedScore: Double?,
    val item: LibraryItem? = null,
    val lesson: AdaptationResult? = null
) {
    val gain: Double? get() = if (originalScore != null && adaptedScore != null) adaptedScore - originalScore else null
}

object LibraryShelf {

    /**
     * Lecciones del lector: primero las del servidor (lecciones oficiales y lecturas adaptadas), luego las demo
     * cargadas en el teléfono que no estén repetidas; todo filtrado por categoría y nivel.
     */
    fun entries(library: List<LibraryItem>, demoLessons: List<AdaptationResult>, filter: LibraryFilter): List<ShelfEntry> {
        val server = library.map { item ->
            ShelfEntry(
                key = "doc:${item.documentId}",
                category = item.category,
                level = item.level,
                title = LessonCategory.displayNameFor(item.category),
                originalScore = item.originalScore,
                adaptedScore = item.adaptedScore,
                item = item
            )
        }
        val serverIds = library.map { it.documentId }.toSet()
        val local = demoLessons.filter { it.documentId == null || it.documentId !in serverIds }.mapIndexed { index, lesson ->
            ShelfEntry(
                key = "demo:$index:${lesson.title}",
                category = lesson.category ?: AdaptationResult.FREE_READING_CATEGORY,
                level = lesson.effectiveLevel,
                title = lesson.title,
                originalScore = lesson.metrics.originalSzigrisztScore,
                adaptedScore = lesson.metrics.adaptedSzigrisztScore,
                lesson = lesson
            )
        }
        return (server + local).filter(filter::matches)
    }

    /** Cuántas lecturas deja ver cada filtro rápido (con la categoría elegida). */
    fun quickCounts(entries: List<ShelfEntry>): Map<LibraryQuickFilter, Int> =
        LibraryQuickFilter.entries.associateWith { quick -> entries.count(quick::matches) }

    /**
     * "Sugeridas para ti": las lecturas del nivel del lector y, si faltan, las de un nivel arriba o abajo, en ese
     * orden y sin repetir. Sin nivel conocido no se sugiere nada.
     */
    fun suggested(entries: List<ShelfEntry>, readerLevel: Int?, limit: Int = MAX_SUGGESTED): List<ShelfEntry> {
        if (readerLevel == null) return emptyList()
        return entries.filter { it.level != null && kotlin.math.abs(it.level - readerLevel) <= 1 }
            .sortedBy { kotlin.math.abs(it.level!! - readerLevel) }
            .take(limit)
    }

    const val MAX_SUGGESTED = 5

    /** Categorías presentes (para mostrar solo filtros útiles), en el orden del catálogo. */
    fun categories(library: List<LibraryItem>, demoLessons: List<AdaptationResult>): List<String> {
        val present = (library.map { it.category } + demoLessons.mapNotNull { it.category }).toSet()
        val catalog = LessonCategory.entries.map { it.code }
        return catalog.filter { it in present } + present.filter { it !in catalog }.sorted()
    }
}
