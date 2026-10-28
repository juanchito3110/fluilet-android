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
 * Respaldo local de "🎓 Cargar Datos Demo": el mismo escenario que siembra `POST /profiles/{id}/seed-demo`
 * (7 días consecutivos de práctica, 900 XP y gemas de sobra) más tres lecturas completas —Real Madrid,
 * Biología Marina y Agujeros Negros— con su texto original, adaptación (medida con el motor local),
 * dato curioso, glosario y mini-reto. La comprensión es SIMULADA, como la del servidor: no es una medición.
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

    private class Entry(
        val title: String, val topic: String, val category: String, val level: Int, val original: String,
        val funFact: String, val vocabulary: List<VocabularyEntry>, val questions: List<ComprehensionQuestion>
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
                ComprehensionQuestion("q1", "¿En qué ciudad tiene su sede el Real Madrid?", listOf("Barcelona", "Madrid", "Sevilla"), 1, "Mira la primera oración.", "El club tiene su sede en Madrid, España."),
                ComprehensionQuestion("q2", "¿De qué color viste tradicionalmente el club?", listOf("Rojo", "Azul", "Blanco"), 2, "Piensa en el apodo «los blancos».", "El club viste de blanco y por eso lo llaman «los blancos».")
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
                ComprehensionQuestion("q1", "¿Qué estudia la biología marina?", listOf("Los seres vivos del mar", "Las rocas de la montaña", "Las nubes del cielo"), 0, "Piensa en la primera oración.", "La biología marina estudia los organismos que viven en los mares y océanos."),
                ComprehensionQuestion("q2", "¿Qué animal es el más grande que ha existido?", listOf("El tiburón blanco", "La ballena azul", "El delfín"), 1, "Busca el animal de mayor tamaño.", "Las ballenas azules son los animales más grandes que han existido en la Tierra.")
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
                ComprehensionQuestion("q1", "¿Qué no puede escapar de un agujero negro?", listOf("El sonido", "La luz", "El agua"), 1, "Lee la primera oración con calma.", "Ni siquiera la luz puede escapar de un agujero negro."),
                ComprehensionQuestion("q2", "¿Cómo se forma normalmente un agujero negro?", listOf("Cuando una estrella muy grande colapsa", "Cuando la Luna se enfría", "Cuando llueve en el espacio"), 0, "Busca qué le pasa a la estrella masiva.", "Se forma cuando una estrella muy masiva agota su combustible y colapsa.")
            )
        )
    )

    /** Las tres lecturas demo del respaldo local (el servidor entrega seis). */
    fun lessons(): List<AdaptationResult> = entries.map { e -> lesson(e, NlpLevel.fromValue(e.level)) }

    /**
     * Lección por tema SIN conexión: la lectura del banco local de esa categoría, adaptada en el dispositivo al nivel
     * pedido (con sus 5 variantes). `null` si el banco local no tiene esa categoría.
     */
    fun offlineLesson(category: String, level: NlpLevel): AdaptationResult? =
        entries.firstOrNull { it.category == category }?.let { lesson(it, level) }

    private fun lesson(e: Entry, level: NlpLevel): AdaptationResult =
        TextAdapter.adapt(e.title, e.original, level).copy(
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
