package com.fluilet.app.presentation.home

/** Los 4 apartados de la navegación principal, cada uno en su propia pantalla. */
enum class HomeTab(val emoji: String, val label: String) {
    LECTURAS("📚", "Lecturas"),
    FLUIBOT("🤖", "Mi FluiBot"),
    COLECCION("🃏", "Colección"),
    ESTADISTICAS("📊", "Estadísticas");

    /** Título completo del apartado (la barra inferior usa la etiqueta corta). */
    val title: String
        get() = when (this) {
            LECTURAS -> "Lecturas"
            FLUIBOT -> "Mi FluiBot"
            COLECCION -> "Colección y Misiones"
            ESTADISTICAS -> "Estadísticas"
        }

    companion object {
        val DEFAULT = LECTURAS
    }
}
