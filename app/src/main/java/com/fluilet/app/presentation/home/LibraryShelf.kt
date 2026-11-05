package com.fluilet.app.presentation.home

import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LibraryItem

/** Filtros de la biblioteca: categoría (`null` = todas) y nivel 1-5 (`null` = todos). */
data class LibraryFilter(val category: String? = null, val level: Int? = null) {
    fun matches(category: String?, level: Int?): Boolean =
        (this.category == null || this.category == category) && (this.level == null || this.level == level)
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
        return (server + local).filter { filter.matches(it.category, it.level) }
    }

    /** Categorías presentes (para mostrar solo filtros útiles), en el orden del catálogo. */
    fun categories(library: List<LibraryItem>, demoLessons: List<AdaptationResult>): List<String> {
        val present = (library.map { it.category } + demoLessons.mapNotNull { it.category }).toSet()
        val catalog = LessonCategory.entries.map { it.code }
        return catalog.filter { it in present } + present.filter { it !in catalog }.sorted()
    }
}
