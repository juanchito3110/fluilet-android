package com.fluilet.app.presentation

import com.fluilet.app.domain.model.LibraryItem
import com.fluilet.app.presentation.home.LibraryFilter
import com.fluilet.app.presentation.home.LibraryShelf
import com.fluilet.app.testutil.sampleAdaptation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Biblioteca del apartado Lecturas: lecciones del servidor y demo, sin repetir, con filtros por categoría y nivel. */
class LibraryShelfTest {

    private val library = listOf(
        LibraryItem("doc-1", "deportes", 1, 45.0, 82.0),
        LibraryItem("doc-2", "ciencia_espacio", 3, 50.0, 70.0),
        LibraryItem("doc-3", "deportes", 3, 40.0, 68.0)
    )
    private val demo = listOf(
        sampleAdaptation(documentId = "doc-2", category = "ciencia_espacio", level = 3), // ya está en el servidor
        sampleAdaptation(documentId = null, category = "animales_naturaleza", level = 2)
    )

    @Test fun serverLessonsComeFirstAndDemoDuplicatesAreSkipped() {
        val entries = LibraryShelf.entries(library, demo, LibraryFilter())
        assertEquals(listOf("doc:doc-1", "doc:doc-2", "doc:doc-3"), entries.take(3).map { it.key })
        assertEquals(4, entries.size)
        assertNotNull(entries.last().lesson)
        assertEquals(37.0, entries.first().gain!!, 1e-9)
    }

    @Test fun filtersCombineCategoryAndLevel() {
        assertEquals(listOf("doc-1", "doc-3"), LibraryShelf.entries(library, demo, LibraryFilter(category = "deportes")).map { it.item!!.documentId })
        assertEquals(listOf("doc-3"), LibraryShelf.entries(library, demo, LibraryFilter("deportes", 3)).map { it.item!!.documentId })
        assertEquals(2, LibraryShelf.entries(library, demo, LibraryFilter(level = 3)).size)
        assertEquals(0, LibraryShelf.entries(library, demo, LibraryFilter("tecnologia", null)).size)
    }

    @Test fun onlyPresentCategoriesAreOfferedInCatalogOrder() {
        assertEquals(listOf("ciencia_espacio", "animales_naturaleza", "deportes"), LibraryShelf.categories(library, demo))
    }
}
