package com.fluilet.app.domain.gamification

import com.fluilet.app.domain.model.LessonCategory
import java.text.Normalizer

/**
 * Tarjeta "¿Sabías que...?": el `fun_fact` del servidor si lo envía y, si no, un dato curioso local
 * según el tema (cuando se conoce) o la categoría de la lección.
 */
object LessonFacts {

    private val byTopic = mapOf(
        "agujeros negros" to "Un agujero negro atrapa hasta la luz: por eso no se puede ver directamente.",
        "sistema solar" to "El Sol contiene casi toda la masa del Sistema Solar.",
        "volcanes" to "La lava puede superar los 1000 grados, ¡más caliente que un horno!",
        "estrellas" to "El Sol es una estrella; las demás se ven pequeñas porque están muy lejos.",
        "biologia marina" to "Los océanos cubren más de dos tercios de la superficie de la Tierra.",
        "tiburon blanco" to "Los tiburones llevan millones de años en el mar: son más antiguos que los dinosaurios.",
        "dinosaurios" to "Las aves de hoy descienden de unos dinosaurios pequeños con plumas.",
        "selva amazonica" to "En la selva amazónica vive una de cada diez especies conocidas del planeta.",
        "piramides de egipto" to "La Gran Pirámide fue el edificio más alto del mundo durante miles de años.",
        "juegos olimpicos" to "Los Juegos Olímpicos modernos se celebran desde 1896.",
        "real madrid" to "El fútbol se juega con un balón: ¡en equipo gana quien mejor se pasa la pelota!",
        "natacion" to "Nadar trabaja casi todos los músculos del cuerpo a la vez.",
        "robots" to "La palabra «robot» nació en una obra de teatro hace más de cien años.",
        "internet" to "El primer sitio web de la historia se publicó en 1991.",
        "videojuegos" to "Los videojuegos usan matemáticas para mover personajes y crear mundos."
    )

    private val byCategory = mapOf(
        LessonCategory.CIENCIA_ESPACIO to "La luz del Sol tarda unos ocho minutos en llegar a la Tierra.",
        LessonCategory.ANIMALES_NATURALEZA to "Las abejas de la miel comunican con un baile dónde pueden encontrar alimento.",
        LessonCategory.HISTORIA_MISTERIOS to "La piedra de Rosetta conserva un mismo mensaje en tres escrituras diferentes.",
        LessonCategory.DEPORTES to "Las primeras canastas de baloncesto fueron cestas para melocotones.",
        LessonCategory.TECNOLOGIA to "Un bit puede representar uno de dos valores: cero o uno.",
        LessonCategory.CUENTOS_AVENTURAS to "Un cuento puede empezar por el final y revelar después cómo comenzó la aventura."
    )

    fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun forTopic(topic: String?): String? = topic?.let { byTopic[normalize(it)] }

    fun forCategory(categoryCode: String?): String? = LessonCategory.fromCode(categoryCode)?.let { byCategory[it] }

    /** El dato del servidor, o el del tema, o el de la categoría; `null` para lecturas libres sin tema. */
    fun forLesson(serverFact: String?, topic: String?, categoryCode: String?): String? =
        serverFact?.takeIf { it.isNotBlank() } ?: forTopic(topic) ?: forCategory(categoryCode)
}
