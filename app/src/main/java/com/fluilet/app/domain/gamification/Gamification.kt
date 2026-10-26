package com.fluilet.app.domain.gamification

import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LessonProgress
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reglas del juego educativo, idénticas a las del backend (`app/gamification.py`): el servidor es la
 * fuente de verdad y la app las replica en el dispositivo como respaldo cuando el endpoint de progreso
 * no responde (gemas, XP, etapa de FluiBot, cartas y racha, sin texto de lectura).
 */
object GamificationRules {
    const val WELCOME_GEMS = 30
    const val GEMS_PER_COMPLETED_LESSON = 50
    const val GEMS_PER_CORRECT_ANSWER = 25
    const val XP_PER_COMPLETED_LESSON = 100
    const val XP_PER_CORRECT_ANSWER = 50

    /** Cada lección trae 2 preguntas: sirve para estimar aciertos cuando solo se conoce el puntaje. */
    const val QUESTIONS_PER_LESSON = 2

    /** Lecciones completadas de una categoría para subir de rareza de la carta: Bronce 1, Plata 2, Oro 3. */
    const val SILVER_AT = 2
    const val GOLD_AT = 3

    fun gemsForLesson(completed: Boolean, correct: Int) =
        (if (completed) GEMS_PER_COMPLETED_LESSON else 0) + correct.coerceAtLeast(0) * GEMS_PER_CORRECT_ANSWER

    fun xpForLesson(completed: Boolean, correct: Int) =
        (if (completed) XP_PER_COMPLETED_LESSON else 0) + correct.coerceAtLeast(0) * XP_PER_CORRECT_ANSWER

    /** Aciertos de una lección: el dato real si existe; si no, se estima con el puntaje (0/50/100 de 2 preguntas). */
    fun correctAnswers(lesson: LessonProgress): Int =
        lesson.questionsCorrect ?: lesson.comprehensionScore
            ?.let { Math.round(it / 100.0 * QUESTIONS_PER_LESSON).toInt() }
            ?: 0
}

/** Etapas evolutivas de FluiBot según el XP acumulado (umbrales del backend). */
enum class FluiBotStage(val number: Int, val displayName: String, val rankTitle: String, val minXp: Int, val upperXp: Int) {
    NUCLEO(1, "Núcleo de Energía", "Explorador Inicial", 0, 150),
    EXPLORADOR(2, "Bot Explorador", "Bot Explorador", 150, 350),
    GUARDIAN(3, "Guardián Cyber-Lector", "Guardián Cyber-Lector", 350, 700),
    SABIO(4, "Sabio Cuántico", "Sabio Cuántico", 700, 1200);

    val next: FluiBotStage? get() = entries.getOrNull(ordinal + 1)
    val isLast: Boolean get() = next == null

    companion object {
        fun forXp(xp: Int): FluiBotStage = entries.last { xp >= it.minXp }
    }
}

/** Progreso de FluiBot: etapa, XP actual y XP donde termina la etapa (en la última, el tope de 1200). */
data class FluiBotProgress(val stage: FluiBotStage, val currentXp: Int, val nextStageXp: Int) {
    /** Avance dentro de la etapa actual (0..1). */
    val fraction: Float
        get() = ((currentXp - stage.minXp).toFloat() / (nextStageXp - stage.minXp)).coerceIn(0f, 1f)

    val label: String get() = "$currentXp / $nextStageXp XP"

    companion object {
        fun of(xp: Int): FluiBotProgress {
            val safe = xp.coerceAtLeast(0)
            val stage = FluiBotStage.forXp(safe)
            return FluiBotProgress(stage, safe, stage.upperXp)
        }
    }
}

enum class CardRarity(val displayName: String) { BRONCE("Bronce"), PLATA("Plata"), ORO("Oro");
    companion object {
        fun forLessons(completed: Int): CardRarity? = when {
            completed >= GamificationRules.GOLD_AT -> ORO
            completed >= GamificationRules.SILVER_AT -> PLATA
            completed >= 1 -> BRONCE
            else -> null
        }

        fun fromWire(raw: String?): CardRarity? = when (raw?.trim()?.lowercase()) {
            "bronce", "bronze" -> BRONCE
            "plata", "silver" -> PLATA
            "oro", "gold" -> ORO
            else -> null // "Bloqueada" u otro valor: sin rareza
        }
    }
}

/** Carta coleccionable por categoría. Bloqueada (`rarity == null`) hasta completar una lección de esa categoría. */
data class CollectibleCard(
    val categoryCode: String,
    val title: String,
    val rarity: CardRarity?,
    val lessonsCompleted: Int,
    val funFact: String
) {
    val unlocked: Boolean get() = rarity != null
}

object CardCatalog {
    private data class Entry(val title: String, val funFact: String)

    private val entries = mapOf(
        LessonCategory.CIENCIA_ESPACIO to Entry("Explorador del Cosmos", "La luz del Sol tarda unos ocho minutos en llegar a la Tierra."),
        LessonCategory.ANIMALES_NATURALEZA to Entry("Guardián de la Naturaleza", "Las abejas de la miel comunican con un baile dónde pueden encontrar alimento."),
        LessonCategory.HISTORIA_MISTERIOS to Entry("Detective del Pasado", "La piedra de Rosetta conserva un mismo mensaje en tres escrituras diferentes."),
        LessonCategory.DEPORTES to Entry("Campeón del Juego Limpio", "Las primeras canastas de baloncesto fueron cestas para melocotones."),
        LessonCategory.TECNOLOGIA to Entry("Creador de Tecnología", "Un bit puede representar uno de dos valores: cero o uno."),
        LessonCategory.CUENTOS_AVENTURAS to Entry("Viajero de Aventuras", "Un cuento puede empezar por el final y revelar después cómo comenzó la aventura.")
    )

    fun titleFor(category: LessonCategory) = entries.getValue(category).title
    fun funFactFor(category: LessonCategory) = entries.getValue(category).funFact
}

/** Recomendación pedagógica del periodo: lo que va bien, en qué enfocarse y qué hacer a continuación. */
data class PedagogicalSummary(
    val headline: String,
    val strengthArea: String,
    val focusArea: String,
    val recommendation: String
)

/** Botín de una lectura (lo que revela el cofre). */
data class SessionLoot(
    val completed: Boolean,
    val correctAnswers: Int,
    val categoryCode: String?,
    val cardBefore: CollectibleCard?,
    val cardAfter: CollectibleCard?
) {
    val gemsForCompleting: Int get() = if (completed) GamificationRules.GEMS_PER_COMPLETED_LESSON else 0
    val gemsForAnswers: Int get() = correctAnswers * GamificationRules.GEMS_PER_CORRECT_ANSWER
    val gems: Int get() = gemsForCompleting + gemsForAnswers
    val xp: Int get() = GamificationRules.xpForLesson(completed, correctAnswers)

    val cardChange: CardChange
        get() = when {
            cardAfter == null || cardAfter.rarity == null -> CardChange.NONE
            cardBefore?.rarity == null -> CardChange.UNLOCKED
            cardAfter.rarity.ordinal > cardBefore.rarity.ordinal -> CardChange.UPGRADED
            else -> CardChange.NONE
        }
}

enum class CardChange { NONE, UNLOCKED, UPGRADED }

object Gamification {

    /** Las 6 cartas (una por categoría) a partir de las lecciones completadas. */
    fun cardsFor(lessons: List<LessonProgress>): List<CollectibleCard> =
        LessonCategory.entries.map { category ->
            val completed = lessons.count { it.completed && it.category == category.code }
            CollectibleCard(category.code, CardCatalog.titleFor(category), CardRarity.forLessons(completed), completed, CardCatalog.funFactFor(category))
        }

    fun totalGems(lessons: List<LessonProgress>): Int =
        GamificationRules.WELCOME_GEMS + lessons.sumOf { GamificationRules.gemsForLesson(it.completed, GamificationRules.correctAnswers(it)) }

    fun totalXp(lessons: List<LessonProgress>): Int =
        lessons.sumOf { GamificationRules.xpForLesson(it.completed, GamificationRules.correctAnswers(it)) }

    /**
     * Racha de días: días consecutivos con alguna práctica, contados hacia atrás desde hoy; si hoy aún
     * no se practicó, desde ayer (la racha sigue viva hasta que termina el día).
     */
    fun streakDays(practiceDays: Collection<LocalDate>, today: LocalDate): Int {
        val days = practiceDays.toSet()
        var cursor = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (cursor in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun lootFor(completed: Boolean, correctAnswers: Int, categoryCode: String?, before: EducationalProgress?): SessionLoot {
        val cardsBefore = before?.cards ?: cardsFor(emptyList())
        val cardBefore = cardsBefore.firstOrNull { it.categoryCode == categoryCode }
        val cardAfter = cardBefore?.let {
            val total = it.lessonsCompleted + if (completed) 1 else 0
            it.copy(lessonsCompleted = total, rarity = CardRarity.forLessons(total))
        }
        return SessionLoot(completed, correctAnswers, categoryCode, cardBefore, cardAfter)
    }

    /** Resumen pedagógico local (el servidor, si lo envía, tiene prioridad). */
    fun pedagogicalSummary(lessons: List<LessonProgress>, completionRate: Double, recommendedLevel: Int, average: Double): PedagogicalSummary {
        if (lessons.isEmpty()) {
            return PedagogicalSummary(
                headline = "¡Empieza tu primera lección!",
                strengthArea = "Aún por descubrir",
                focusArea = "Elige una categoría que te guste",
                recommendation = "Genera una lección corta y responde el mini-reto para empezar a ganar gemas y XP."
            )
        }
        val byCategory = lessons.groupBy { it.category }
        fun name(code: String) = LessonCategory.displayNameFor(code)
        fun avgScore(list: List<LessonProgress>) = list.mapNotNull { it.comprehensionScore }.takeIf { it.isNotEmpty() }?.average()

        val strength = byCategory.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<LessonProgress>>> { avgScore(it.value) ?: -1.0 }
                .thenByDescending { e -> e.value.count { it.completed } })
            .first().key

        val incomplete = byCategory.entries.filter { e -> e.value.any { !it.completed } }
            .maxByOrNull { e -> e.value.count { !it.completed } }?.key
        val weakest = byCategory.entries.filter { it.key != strength && avgScore(it.value) != null }
            .minByOrNull { avgScore(it.value)!! }?.key
        val focus = incomplete ?: weakest

        val headline = when {
            completionRate >= 80 && average >= 75 -> "¡Excelente ritmo lector!"
            completionRate >= 50 -> "Vas por muy buen camino"
            else -> "Cada lectura cuenta"
        }
        val focusText = focus?.let { name(it) } ?: "Probar nuevas categorías"
        val recommendation = when {
            incomplete != null -> "Termina las lecciones pendientes de ${name(incomplete)} y repasa con el mini-reto; luego prueba el nivel $recommendedLevel."
            average in 0.1..49.9 -> "Relee con calma y usa las pistas del mini-reto antes de subir de nivel."
            else -> "Estás listo para el nivel $recommendedLevel: elige una categoría nueva y sigue sumando gemas."
        }
        return PedagogicalSummary(headline, name(strength), focusText, recommendation)
    }

    /**
     * Completa un resumen educativo con la parte de juego. Los valores que el servidor ya calculó
     * (si los hay) tienen prioridad; el resto se deriva de las lecciones.
     */
    fun enrich(base: EducationalProgress, server: ServerGamification? = null, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): EducationalProgress {
        val days = base.lessons.filter { it.durationSeconds > 0 || it.completionPercent > 0 }
            .mapNotNull { it.recordedAt }.map { it.atZone(zone).toLocalDate() }
        val xp = server?.currentXp ?: totalXp(base.lessons)
        return base.copy(
            totalGems = server?.totalGems ?: totalGems(base.lessons),
            currentXp = xp,
            streakDays = server?.streakDays ?: streakDays(days, today),
            cards = server?.cards?.takeIf { it.isNotEmpty() }?.let { mergeCards(it, base.lessons) } ?: cardsFor(base.lessons),
            pedagogicalSummary = server?.summary
                ?: pedagogicalSummary(base.lessons, base.completionRatePercent, base.recommendedLevel, base.averageComprehension),
            gamificationFromServer = server != null,
            serverMissions = server?.missions?.takeIf { it.isNotEmpty() }
        )
    }

    /** Siempre las 6 categorías: las que el servidor no envía se completan con el cálculo local. */
    private fun mergeCards(server: List<CollectibleCard>, lessons: List<LessonProgress>): List<CollectibleCard> {
        val local = cardsFor(lessons).associateBy { it.categoryCode }
        val remote = server.associateBy { it.categoryCode }
        return LessonCategory.entries.map { remote[it.code] ?: local.getValue(it.code) }
    }
}

/** Valores de juego que el backend pueda enviar (todos opcionales). */
data class ServerGamification(
    val totalGems: Int?,
    val currentXp: Int?,
    val streakDays: Int?,
    val summary: PedagogicalSummary?,
    val cards: List<CollectibleCard>?,
    val missions: List<DailyMission>? = null
)
