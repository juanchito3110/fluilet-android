package com.fluilet.app.domain.demo

import com.fluilet.app.domain.gamification.Gamification
import com.fluilet.app.domain.gamification.ServerGamification
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.BadgeCatalog
import com.fluilet.app.domain.model.ComprehensionQuestion
import com.fluilet.app.domain.model.EducationalBadge
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.LessonProgress
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.text.TextAdapter
import com.fluilet.app.domain.text.VocabularyEntry
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Banco local de respaldo: el escenario de práctica de las cuentas de demostración (7 días, 900 XP y gemas) y tres
 * lecturas completas —Real Madrid, Biología Marina y Agujeros Negros— más un cuento original para "Bosque Encantado",
 * con texto original, adaptación (medida con el motor local), dato curioso, glosario y un mini-reto como el del
 * backend `adfba34`: 3 preguntas sobre datos concretos de la lectura, 4 opciones del mismo tipo y una explicación que
 * cita la lectura. La comprensión del historial es SIMULADA, como la del servidor: no es una medición.
 */
object DemoData {

    /** Gemas base del historial demo; las tres misiones de hoy suman 95 más al reclamarse (715 en total). */
    const val BASE_GEMS = 620
    const val XP = 900
    const val STREAK_DAYS = 7

    private class Practice(
        val category: String, val level: Int, val comprehension: Int, val seconds: Long,
        val originalScore: Double, val adaptedScore: Double, val correct: Int?
    )

    // Un día por sesión, de hace seis días a hoy. Los puntajes Szigriszt-Pazos son los del contrato del backend.
    private val practice = listOf(
        Practice("deportes", 1, 65, 420, 66.75, 68.12, null),
        Practice("animales_naturaleza", 2, 75, 480, 47.83, 55.31, null),
        Practice("ciencia_espacio", 3, 80, 510, 56.44, 57.17, null),
        Practice("historia_misterios", 3, 85, 540, 42.42, 44.60, null),
        Practice("tecnologia", 4, 90, 560, 26.20, 36.34, null),
        Practice("cuentos_aventuras", 4, 100, 600, 50.66, 58.97, 2),
        Practice("deportes", 4, 100, 620, 66.75, 68.90, 2)
    )

    fun progress(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): EducationalProgress {
        val lessons = practice.mapIndexed { index, p ->
            LessonProgress(
                lessonNumber = index + 1,
                readingId = "demo-${index + 1}",
                recordedAt = today.minusDays((practice.size - 1 - index).toLong()).atTime(LocalTime.NOON).atZone(zone).toInstant(),
                category = p.category,
                level = p.level,
                completed = true,
                completionPercent = 100,
                comprehensionScore = p.comprehension,
                durationSeconds = p.seconds,
                originalScore = p.originalScore,
                adaptedScore = p.adaptedScore,
                questionsCorrect = p.correct
            )
        }
        val base = EducationalProgress(
            totalLessons = lessons.size,
            completedLessons = lessons.size,
            incompleteLessons = 0,
            completionRatePercent = 100.0,
            totalReadingSeconds = lessons.sumOf { it.durationSeconds },
            averageComprehension = lessons.mapNotNull { it.comprehensionScore }.average(),
            recommendedLevel = 4,
            lessons = lessons,
            badges = BadgeCatalog.all.map { EducationalBadge(it.code, it.title, it.description, true) },
            isEstimated = true,
            demoData = true,
            demoLessons = lessons()
        )
        // Misiones de hoy sin `serverMissions`: se calculan en el dispositivo y se pueden reclamar en vivo.
        return Gamification.enrich(base, ServerGamification(BASE_GEMS, XP, STREAK_DAYS, null, null), today, zone)
    }

    /**
     * Progreso de una de las 4 fases de la cuenta del tutor (`docs/demo-accounts.md`): [lessonCount] lecturas
     * terminadas en días consecutivos hasta hoy, con los saldos exactos de gemas, XP y racha de esa fase.
     */
    fun phaseProgress(
        lessonCount: Int, gems: Int, xp: Int, streakDays: Int, level: Int,
        today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()
    ): EducationalProgress {
        val rows = practice.take(lessonCount.coerceIn(0, practice.size))
        val lessons = rows.mapIndexed { index, p ->
            LessonProgress(
                lessonNumber = index + 1, readingId = "demo-phase-${index + 1}",
                recordedAt = today.minusDays((rows.size - 1 - index).toLong()).atTime(LocalTime.NOON).atZone(zone).toInstant(),
                category = p.category, level = p.level.coerceAtMost(level), completed = true, completionPercent = 100,
                comprehensionScore = p.comprehension, durationSeconds = p.seconds,
                originalScore = p.originalScore, adaptedScore = p.adaptedScore, questionsCorrect = p.correct
            )
        }
        val unlocked = listOf(
            lessons.isNotEmpty(),
            lessons.any { it.comprehensionScore == 100 },
            lessons.size >= 3,
            lessons.map { it.category }.toSet().size >= 2,
            lessons.any { it.level >= 3 }
        )
        val base = EducationalProgress(
            totalLessons = lessons.size, completedLessons = lessons.size, incompleteLessons = 0,
            completionRatePercent = if (lessons.isEmpty()) 0.0 else 100.0,
            totalReadingSeconds = lessons.sumOf { it.durationSeconds },
            averageComprehension = lessons.mapNotNull { it.comprehensionScore }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            recommendedLevel = level.coerceIn(1, 5), lessons = lessons,
            badges = BadgeCatalog.all.mapIndexed { i, b -> EducationalBadge(b.code, b.title, b.description, unlocked[i]) },
            isEstimated = true, demoData = true
        )
        return Gamification.enrich(base, ServerGamification(gems, xp, streakDays, null, null), today, zone)
    }

    /**
     * [simple]: paráfrasis sencilla revisada de cada oración del original, en el mismo orden (como los planes
     * alineados del catálogo del backend). El motor local mezcla oraciones originales y sencillas para calibrar
     * las 5 variantes de la lectura en su banda sin conexión.
     */
    private class Entry(
        val title: String, val topic: String, val category: String, val level: Int, val original: String,
        val funFact: String, val vocabulary: List<VocabularyEntry>, val questions: List<ComprehensionQuestion>,
        val simple: List<String>
    )

    private val entries = listOf(
        Entry(
            title = "Real Madrid", topic = "Real Madrid", category = "deportes", level = 1,
            original = "El Real Madrid Club de Fútbol es una entidad deportiva con sede en Madrid, España, que fue fundada en el año 1902 y que, desde entonces, se ha convertido en uno de los clubes más laureados de la historia del fútbol profesional, ya que ha conquistado en numerosas ocasiones la Liga española y la Copa de Europa, actualmente conocida como Liga de Campeones de la UEFA. Su estadio, el Santiago Bernabéu, situado en el centro de la capital española, tiene capacidad para más de ochenta mil espectadores y es visitado por aficionados de todo el mundo, quienes acuden para presenciar partidos en los que han participado futbolistas internacionales de enorme prestigio. El club viste tradicionalmente de blanco, un color que le ha valido el apodo de «los blancos», y su escudo está coronado por una corona real que recuerda el título honorífico concedido por el rey Alfonso XIII.",
            funFact = "El club recibió el título de «Real» del rey Alfonso XIII en 1920.",
            vocabulary = listOf(
                VocabularyEntry("club", "Un grupo de personas que se reúnen para practicar un deporte.", "El club entrena todos los días."),
                VocabularyEntry("estadio", "Un lugar grande donde se juegan los partidos y la gente los mira.", "Fuimos al estadio a ver el partido."),
                VocabularyEntry("campeón", "El equipo o la persona que gana una competición.", "El campeón levantó la copa."),
                VocabularyEntry("afición", "Las personas que animan y apoyan a su equipo.", "La afición cantó durante todo el partido.")
            ),
            questions = listOf(
                ComprehensionQuestion(
                    "q1", "¿En qué año se fundó el Real Madrid según la lectura?",
                    listOf("En el año 1902", "En el año 1912", "En el año 1892", "En el año 1952"), 0,
                    "Busca el año en la primera parte de la lectura.",
                    "La lectura dice que el club nació en el año 1902 y que desde entonces ganó muchos títulos."
                ),
                ComprehensionQuestion(
                    "q2", "¿Cómo se llama el estadio del Real Madrid según la lectura?",
                    listOf("Camp Nou", "Mestalla", "Santiago Bernabéu", "San Mamés"), 2,
                    "Busca la oración que habla del estadio.",
                    "La lectura explica que su estadio se llama Santiago Bernabéu y está en el centro de Madrid."
                ),
                ComprehensionQuestion(
                    "q3", "¿Cuántos espectadores caben en su estadio según la lectura?",
                    listOf("Más de ocho mil", "Más de ochenta mil", "Más de veinte mil", "Más de cuarenta mil"), 1,
                    "Fíjate en la cantidad que aparece junto al estadio.",
                    "Según la lectura, en el Santiago Bernabéu caben más de ochenta mil personas."
                )
            ),
            simple = listOf(
                "El Real Madrid es un club de fútbol. Su casa está en Madrid, en España. Nació en el año 1902. " +
                    "Desde ese año ganó muchos títulos. Ganó muchas veces la Liga de España. También ganó muchas veces " +
                    "la Copa de Europa. Hoy esa copa se llama Liga de Campeones.",
                "Su estadio se llama Santiago Bernabéu. Está en el centro de Madrid. Caben más de ochenta mil personas. " +
                    "Fans de todo el mundo van a ver sus partidos. Allí jugaron estrellas del fútbol de muchos países.",
                "El club viste de blanco. Por eso lo llaman «los blancos». Su escudo tiene una corona real. " +
                    "La corona recuerda un título de honor. Se lo dio el rey Alfonso XIII."
            )
        ),
        Entry(
            title = "Biología Marina", topic = "Biología Marina", category = "animales_naturaleza", level = 2,
            original = "La biología marina es la ciencia que estudia los organismos que viven en los mares y océanos, así como las relaciones que establecen entre sí y con el medio que los rodea. Los océanos cubren más de dos tercios de la superficie del planeta y albergan una diversidad extraordinaria de seres vivos, desde diminutas algas microscópicas conocidas como fitoplancton, que producen una parte muy importante del oxígeno que respiramos, hasta las enormes ballenas azules, que son los animales de mayor tamaño que han existido en la Tierra. Los científicos que se dedican a esta disciplina exploran arrecifes de coral, fosas abisales y costas rocosas para comprender cómo se alimentan, se reproducen y sobreviven los habitantes del mar, y también para proteger a las especies que se encuentran amenazadas por la contaminación y el cambio climático.",
            funFact = "El fitoplancton produce una parte muy importante del oxígeno que respiramos.",
            vocabulary = listOf(
                VocabularyEntry("fitoplancton", "Seres vivos diminutos del mar que flotan y fabrican oxígeno con la luz del sol.", "El fitoplancton alimenta a muchos animales marinos."),
                VocabularyEntry("arrecife", "Una muralla de coral bajo el agua donde viven muchos peces.", "Buceamos junto a un arrecife lleno de colores."),
                VocabularyEntry("océano", "Una inmensa extensión de agua salada.", "Las ballenas viven en el océano."),
                VocabularyEntry("especie", "Un tipo de ser vivo con características iguales.", "La ballena azul es una especie en peligro.")
            ),
            questions = listOf(
                ComprehensionQuestion(
                    "q1", "¿Qué estudia la biología marina según la lectura?",
                    listOf("Los seres vivos del mar", "Las rocas de las montañas", "Los planetas del espacio", "Las plantas del desierto"), 0,
                    "Piensa en la primera oración.",
                    "La lectura dice que la biología marina estudia los seres vivos del mar y cómo viven juntos."
                ),
                ComprehensionQuestion(
                    "q2", "¿Qué parte del planeta cubren los océanos según la lectura?",
                    listOf("Menos de la mitad", "Solo una cuarta parte", "Casi una décima parte", "Más de dos tercios"), 3,
                    "Busca la oración que habla de los océanos.",
                    "Según la lectura, los océanos cubren más de dos tercios de la superficie del planeta."
                ),
                ComprehensionQuestion(
                    "q3", "¿Cuál es el animal más grande que ha existido según la lectura?",
                    listOf("El tiburón ballena", "La ballena azul", "El elefante africano", "El calamar gigante"), 1,
                    "Busca el animal de mayor tamaño.",
                    "La lectura explica que las ballenas azules son los animales más grandes que han vivido en la Tierra."
                )
            ),
            simple = listOf(
                "La biología marina es una ciencia. Estudia los seres vivos del mar. También mira cómo viven juntos " +
                    "y con lo que los rodea.",
                "Los océanos cubren más de dos tercios del planeta. En ellos vive una gran variedad de seres. " +
                    "Hay algas muy pequeñas llamadas fitoplancton. Ellas hacen buena parte del oxígeno que respiramos. " +
                    "También hay ballenas azules. Son los animales más grandes que han vivido en la Tierra.",
                "Los científicos del mar visitan arrecifes de coral. También van a fosas muy hondas y a costas de roca. " +
                    "Quieren saber cómo comen, nacen y viven los seres del mar. También quieren cuidar a las especies " +
                    "en peligro. La contaminación y el cambio del clima las ponen en peligro."
            )
        ),
        Entry(
            title = "Agujeros Negros", topic = "Agujeros Negros", category = "ciencia_espacio", level = 3,
            original = "Un agujero negro es una región del espacio en la que la fuerza de la gravedad es tan intensa que nada, ni siquiera la luz, puede escapar de su interior una vez que ha cruzado una frontera invisible denominada horizonte de sucesos. Estos objetos se forman habitualmente cuando una estrella muy masiva agota su combustible nuclear y colapsa bajo su propio peso, concentrando una enorme cantidad de materia en un espacio extraordinariamente reducido. Aunque no pueden observarse de forma directa, los astrónomos detectan su presencia al estudiar el movimiento de las estrellas cercanas y la radiación emitida por el gas caliente que cae hacia ellos. En el centro de la Vía Láctea se encuentra un agujero negro supermasivo que los científicos llaman Sagitario A*.",
            funFact = "En el centro de nuestra galaxia, la Vía Láctea, hay un agujero negro gigante llamado Sagitario A*.",
            vocabulary = listOf(
                VocabularyEntry("gravedad", "La fuerza invisible que atrae los objetos entre sí.", "La gravedad hace que una pelota caiga al suelo."),
                VocabularyEntry("estrella", "Una bola gigante de gas muy caliente que brilla en el cielo.", "El Sol es una estrella."),
                VocabularyEntry("astrónomo", "Una persona que estudia los astros y el universo.", "El astrónomo miró el cielo con su telescopio."),
                VocabularyEntry("galaxia", "Un grupo enorme de estrellas, planetas y polvo que viajan juntos.", "Nuestra galaxia se llama Vía Láctea.")
            ),
            questions = listOf(
                ComprehensionQuestion(
                    "q1", "¿Cómo se llama el borde invisible de un agujero negro según la lectura?",
                    listOf("Cinturón de asteroides", "Horizonte de sucesos", "Capa de ozono", "Línea del ecuador"), 1,
                    "Lee la primera parte con calma.",
                    "La lectura dice que el agujero negro tiene un borde que no se ve llamado horizonte de sucesos."
                ),
                ComprehensionQuestion(
                    "q2", "¿Cómo se forma casi siempre un agujero negro según la lectura?",
                    listOf("Cuando chocan dos lunas pequeñas", "Cuando un cometa se derrite", "Cuando una estrella muy grande colapsa", "Cuando un planeta pierde su agua"), 2,
                    "Busca qué le pasa a la estrella.",
                    "Según la lectura, una estrella muy grande gasta su combustible y se hunde por su propio peso."
                ),
                ComprehensionQuestion(
                    "q3", "¿Cómo se llama el agujero negro del centro de la Vía Láctea según la lectura?",
                    listOf("Sagitario A*", "Andrómeda B", "Orión C", "Centauro D"), 0,
                    "Mira la última oración de la lectura.",
                    "La lectura cuenta que en el centro de la Vía Láctea hay un agujero negro gigante llamado Sagitario A*."
                )
            ),
            simple = listOf(
                "Un agujero negro es una zona del espacio. Allí la gravedad es muy fuerte. Nada puede salir de él, " +
                    "ni la luz. Tiene un borde que no se ve. Ese borde se llama horizonte de sucesos.",
                "Casi siempre nace de una estrella muy grande. La estrella gasta todo su combustible. Luego se hunde " +
                    "por su propio peso. Así junta mucha materia en un lugar muy pequeño.",
                "No lo podemos ver de forma directa. Pero los astrónomos saben dónde está. Miran cómo se mueven " +
                    "las estrellas de al lado. También estudian el brillo del gas caliente que cae en él.",
                "En el centro de la Vía Láctea hay un agujero negro gigante. Los científicos lo llaman Sagitario A*."
            )
        )
    )

    /**
     * Cuentos originales del banco local (ficción identificada como tal, como en el catálogo `topic_library` del
     * backend `adfba34`): "Bosque Encantado" es una historia infantil coherente, nunca la reseña de una novela.
     */
    private val stories = listOf(
        Entry(
            title = "El Bosque Encantado y el puente de raíces", topic = "Bosque Encantado", category = "cuentos_aventuras", level = 1,
            original = "En este cuento original de ficción, la exploradora Inés descubre un puente de raíces dentro de un bosque encantado. " +
                "Un pequeño zorro llamado Bruno permanece junto al arroyo porque la corriente le impide cruzar hacia su madriguera. " +
                "Inés solicita ayuda a la guardiana del bosque y entre ambas colocan una tabla sobre el puente para facilitar el paso del zorro. " +
                "Después de alcanzar la otra orilla, Bruno agradece la colaboración y los tres regresan por el sendero señalado con piedras blancas. " +
                "La aventura muestra que pedir ayuda y trabajar en equipo permite resolver dificultades sin ponerse en peligro.",
            funFact = "Los zorros viven en madrigueras: túneles bajo la tierra donde descansan y cuidan a sus crías.",
            vocabulary = listOf(
                VocabularyEntry("arroyo", "Un río pequeño de agua que corre.", "El agua del arroyo estaba fría."),
                VocabularyEntry("madriguera", "La casa de algunos animales, hecha como un túnel bajo la tierra.", "El conejo entró en su madriguera."),
                VocabularyEntry("guardiana", "La persona que cuida y protege un lugar.", "La guardiana cuida los árboles del bosque."),
                VocabularyEntry("sendero", "Un camino estrecho para ir a pie.", "Caminamos por el sendero del parque.")
            ),
            questions = listOf(
                ComprehensionQuestion(
                    "q1", "¿Qué descubre Inés dentro del bosque encantado según la lectura?",
                    listOf("Un puente hecho de raíces", "Un puente hecho de hielo", "Un puente hecho de cristal", "Un puente hecho de metal"), 0,
                    "Lee la primera oración del cuento.",
                    "El cuento dice que Inés explora un bosque encantado y allí descubre un puente de raíces."
                ),
                ComprehensionQuestion(
                    "q2", "¿Por qué Bruno no puede cruzar el arroyo según la lectura?",
                    listOf("Porque el camino está lleno de nieve", "Porque la corriente del arroyo es fuerte", "Porque el puente está demasiado caliente", "Porque el bosque se ha quedado seco"), 1,
                    "Busca la oración que habla del zorro Bruno.",
                    "La lectura explica que la corriente del arroyo le impide cruzar: el agua corre con fuerza."
                ),
                ComprehensionQuestion(
                    "q3", "¿Qué colocan Inés y la guardiana para ayudar a Bruno según la lectura?",
                    listOf("Una cuerda entre las dos orillas", "Una piedra al borde del arroyo", "Una rama junto a la madriguera", "Una tabla sobre el puente"), 3,
                    "Mira qué hacen Inés y la guardiana juntas.",
                    "Según el cuento, Inés y la guardiana ponen una tabla sobre el puente para que Bruno pueda pasar."
                )
            ),
            simple = listOf(
                "Este cuento es inventado. Inés explora un bosque encantado. Allí descubre un puente de raíces.",
                "El zorro Bruno está junto al arroyo. No puede cruzar porque el agua corre con fuerza. Su madriguera está al otro lado.",
                "Inés pide ayuda a la guardiana del bosque. Las dos ponen una tabla sobre el puente. Así Bruno puede pasar.",
                "Bruno llega a la otra orilla y da las gracias. Los tres vuelven por el sendero de piedras blancas.",
                "La aventura enseña a pedir ayuda. El trabajo en equipo sirve para resolver problemas sin riesgos."
            )
        )
    )

    /** Las tres lecturas demo del respaldo local (el servidor entrega seis). */
    fun lessons(): List<AdaptationResult> = entries.map { e -> lesson(e, NlpLevel.fromValue(e.level)) }

    /**
     * Lección por tema SIN conexión: la lectura del banco local de esa categoría (la del tema pedido si existe),
     * adaptada en el dispositivo al nivel pedido (con sus 5 variantes). `null` si el banco local no tiene esa categoría.
     */
    fun offlineLesson(category: String, level: NlpLevel, topic: String? = null): AdaptationResult? {
        val bank = (entries + stories).filter { it.category == category }
        val wanted = topic?.trim()?.takeIf { it.isNotEmpty() }
        val entry = bank.firstOrNull { wanted != null && it.topic.equals(wanted, ignoreCase = true) } ?: bank.firstOrNull()
        return entry?.let { lesson(it, level) }
    }

    private fun lesson(e: Entry, level: NlpLevel): AdaptationResult =
        TextAdapter.adapt(e.title, e.original, level, glossary = e.vocabulary, aligned = e.simple).copy(
            category = e.category,
            simplificationLevel = level.value,
            targetLevel = level.value,
            questions = e.questions,
            funFact = e.funFact,
            keyVocabulary = e.vocabulary,
            topic = e.topic,
            originalText = e.original
        )
}
