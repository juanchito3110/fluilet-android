package com.fluilet.app.domain.text

import java.text.Normalizer

/** Palabra clave con su definición sencilla y un ejemplo, para el Glosario Mágico. */
data class VocabularyEntry(val word: String, val definition: String, val example: String)

/**
 * Glosario local de respaldo (cuando el servidor no envía `key_vocabulary`): busca en el texto adaptado
 * palabras conocidas y devuelve su definición para niños. Tolera tildes y plurales sencillos.
 */
object LocalGlossary {

    private val entries = listOf(
        VocabularyEntry("planeta", "Un mundo enorme y redondo que da vueltas alrededor de una estrella.", "La Tierra es el planeta donde vivimos."),
        VocabularyEntry("estrella", "Una bola gigante de gas muy caliente que brilla en el cielo.", "El Sol es una estrella."),
        VocabularyEntry("galaxia", "Un grupo enorme de estrellas, planetas y polvo que viajan juntos.", "Nuestra galaxia se llama Vía Láctea."),
        VocabularyEntry("órbita", "El camino que sigue un astro al dar vueltas alrededor de otro.", "La Luna sigue su órbita alrededor de la Tierra."),
        VocabularyEntry("gravedad", "La fuerza invisible que atrae los objetos hacia el suelo.", "La gravedad hace que una pelota caiga."),
        VocabularyEntry("telescopio", "Un aparato para ver cosas muy lejanas, como las estrellas.", "Con un telescopio vimos los anillos de Saturno."),
        VocabularyEntry("volcán", "Una montaña con un agujero por donde puede salir lava.", "El volcán lanzó humo y ceniza."),
        VocabularyEntry("lava", "Roca derretida y muy caliente que sale de un volcán.", "La lava es roja y brillante."),
        VocabularyEntry("océano", "Una inmensa extensión de agua salada.", "Las ballenas viven en el océano."),
        VocabularyEntry("tiburón", "Un pez grande y veloz que vive en el mar.", "El tiburón nada con su aleta alta."),
        VocabularyEntry("dinosaurio", "Un animal enorme que vivió hace millones de años.", "El dinosaurio dejó huellas en la roca."),
        VocabularyEntry("fósil", "Los restos muy antiguos de un ser vivo que se quedaron en la roca.", "Encontraron un fósil de helecho."),
        VocabularyEntry("selva", "Un bosque muy grande, caluroso y lleno de lluvia y de vida.", "En la selva viven monos y loros."),
        VocabularyEntry("especie", "Un tipo de ser vivo con características iguales.", "El jaguar es una especie de felino."),
        VocabularyEntry("hábitat", "El lugar donde vive un animal y encuentra lo que necesita.", "El hábitat de los peces es el agua."),
        VocabularyEntry("ecosistema", "Todos los seres vivos de un lugar y cómo se ayudan entre sí.", "Un charco es un pequeño ecosistema."),
        VocabularyEntry("oxígeno", "El gas del aire que necesitamos para respirar.", "Las plantas producen oxígeno."),
        VocabularyEntry("pirámide", "Una construcción con base cuadrada y lados en punta.", "Los egipcios construyeron grandes pirámides."),
        VocabularyEntry("faraón", "El gobernante del antiguo Egipto.", "El faraón vivía en un palacio."),
        VocabularyEntry("imperio", "Un país muy grande que gobierna sobre muchos territorios.", "El imperio romano era enorme."),
        VocabularyEntry("castillo", "Un edificio fuerte con muros altos donde vivían reyes y caballeros.", "El castillo tenía una torre muy alta."),
        VocabularyEntry("pirata", "Una persona que robaba barcos en el mar.", "El pirata escondió su tesoro."),
        VocabularyEntry("tesoro", "Cosas de mucho valor, como monedas y joyas.", "Encontraron un cofre con un tesoro."),
        VocabularyEntry("arqueología", "La ciencia que estudia objetos muy antiguos para conocer el pasado.", "La arqueología descubrió una vasija."),
        VocabularyEntry("estadio", "Un lugar grande con gradas para ver partidos y competencias.", "El estadio estaba lleno de gente."),
        VocabularyEntry("atleta", "Una persona que entrena para practicar un deporte.", "La atleta corrió muy rápido."),
        VocabularyEntry("entrenamiento", "Practicar mucho para mejorar en algo.", "El entrenamiento la hizo más fuerte."),
        VocabularyEntry("torneo", "Una competencia donde varios equipos juegan para ganar.", "Nuestro equipo ganó el torneo."),
        VocabularyEntry("equipo", "Un grupo de personas que trabajan o juegan juntas.", "Todo el equipo celebró el gol."),
        VocabularyEntry("robot", "Una máquina que puede hacer tareas, a veces sola.", "El robot movió la caja."),
        VocabularyEntry("computadora", "Una máquina que guarda información y sigue instrucciones.", "Escribí mi tarea en la computadora."),
        VocabularyEntry("internet", "Una red que conecta computadoras de todo el mundo.", "Busqué el dato en internet."),
        VocabularyEntry("algoritmo", "Una lista de pasos para resolver un problema.", "Una receta es un algoritmo."),
        VocabularyEntry("sensor", "Una pieza que detecta cosas como la luz o el calor.", "El sensor encendió la luz."),
        VocabularyEntry("energía", "La fuerza que hace que las cosas se muevan o funcionen.", "El Sol nos da energía."),
        VocabularyEntry("batería", "Algo que guarda energía para que un aparato funcione.", "La batería del juguete se acabó."),
        VocabularyEntry("dragón", "Una criatura de los cuentos, grande, con alas y a veces fuego.", "El dragón cuidaba una cueva."),
        VocabularyEntry("aventura", "Una experiencia emocionante y diferente.", "Vivieron una aventura en el bosque."),
        VocabularyEntry("héroe", "Alguien valiente que ayuda a los demás.", "El héroe salvó al pueblo."),
        VocabularyEntry("misterio", "Algo que no se entiende todavía y hay que descubrir.", "El misterio se resolvió al final."),
        VocabularyEntry("científico", "Una persona que estudia y hace preguntas para entender el mundo.", "La científica observó una muestra."),
        VocabularyEntry("experimento", "Una prueba que se hace para aprender algo nuevo.", "Hicimos un experimento con agua.")
    )

    internal fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Hasta [max] palabras clave del texto, en orden de aparición y sin repetir. */
    fun find(text: String, max: Int = 6): List<VocabularyEntry> {
        val byKey = entries.associateBy { normalize(it.word) }
        val found = linkedMapOf<String, VocabularyEntry>()
        for (token in SpanishText.words(text)) {
            val n = normalize(token)
            val entry = byKey[n] ?: byKey[n.removeSuffix("s")] ?: byKey[n.removeSuffix("es")] ?: continue
            found.putIfAbsent(entry.word, entry)
            if (found.size >= max) break
        }
        return found.values.toList()
    }

    val size: Int get() = entries.size
}
