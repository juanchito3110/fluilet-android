package com.fluilet.app.presentation.home

/**
 * Los 5 apartados de la barra inferior. Cada etiqueta es UNA palabra corta para que nunca se corte en pantallas
 * angostas; [title] es el encabezado completo dentro del apartado.
 */
enum class HomeTab(val label: String, val title: String) {
    INICIO("Inicio", "Inicio"),
    LECTURAS("Lecturas", "Biblioteca de lecturas"),
    ADAPTAR("Adaptar", "Estudio de adaptación"),
    AVATAR("Avatar", "Avatar FluiBot"),
    CARTAS("Cartas", "Cartas y estadísticas");

    companion object {
        val DEFAULT = INICIO
    }
}

/** Sub-pestañas del apartado Cartas: el álbum de cartas coleccionables, los logros (insignias) y el progreso. */
enum class CardsView(val emoji: String, val label: String) { ALBUM("🃏", "Álbum"), LOGROS("🏅", "Logros"), PROGRESO("📊", "Progreso") }
