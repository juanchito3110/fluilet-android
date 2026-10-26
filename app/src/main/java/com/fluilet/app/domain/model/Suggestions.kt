package com.fluilet.app.domain.model

/** Sugerencias rápidas de tema por categoría para "Generar lección" (el niño también puede escribir libremente). */
object TopicSuggestions {

    fun forCategory(category: LessonCategory): List<String> = when (category) {
        LessonCategory.CIENCIA_ESPACIO -> listOf("Agujeros Negros", "Sistema Solar", "Volcanes", "Estrellas")
        LessonCategory.ANIMALES_NATURALEZA -> listOf("Biología Marina", "Tiburón Blanco", "Dinosaurios", "Selva Amazónica")
        LessonCategory.HISTORIA_MISTERIOS -> listOf("Pirámides de Egipto", "Piratas del Caribe", "Los Vikingos", "Castillos Medievales")
        LessonCategory.DEPORTES -> listOf("Real Madrid", "Juegos Olímpicos", "Fútbol", "Natación")
        LessonCategory.TECNOLOGIA -> listOf("Robots", "Inteligencia Artificial", "Internet", "Videojuegos")
        LessonCategory.CUENTOS_AVENTURAS -> listOf("Dragones", "Tesoros Piratas", "Viaje al Espacio", "Bosque Encantado")
    }
}
