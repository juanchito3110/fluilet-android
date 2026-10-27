package com.fluilet.app.domain.gamification

import com.fluilet.app.domain.model.LessonProgress
import java.time.LocalDate
import java.time.ZoneId

/** Misión diaria con su avance y su recompensa en gemas. */
data class DailyMission(
    val id: String,
    val title: String,
    val description: String,
    val current: Int,
    val target: Int,
    val rewardGems: Int,
    /** Misión calculada por el servidor: sus gemas ya se abonan en `total_gems` (no se pagan dos veces en el cliente). */
    val fromServer: Boolean = false,
    /** `claimed` del servidor: el backend abona cada misión completada una sola vez. */
    val claimed: Boolean = false
) {
    val completed: Boolean get() = current >= target
    val fraction: Float get() = if (target <= 0) 1f else (current.toFloat() / target).coerceIn(0f, 1f)
}

/** Lo que aporta la sesión que se está leyendo (aún no registrada en el servidor). */
data class SessionContribution(val completed: Boolean, val correctAnswers: Int, val seconds: Long)

/**
 * Las 3 misiones del día (`daily_missions` del backend cuando exista; hoy se calculan en el
 * dispositivo con las lecciones de HOY): leer una lección completa (+25 💎), acertar 2 respuestas del
 * mini-reto (+30 💎) y practicar 5 minutos (+40 💎).
 */
object DailyMissions {
    const val READ_ID = "leer_leccion"
    const val ANSWERS_ID = "respuestas_correctas"
    const val TIME_ID = "practicar_tiempo"

    const val READ_REWARD = 25
    const val ANSWERS_REWARD = 30
    const val TIME_REWARD = 40
    const val TIME_TARGET_SECONDS = 300

    fun compute(
        lessons: List<LessonProgress>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        session: SessionContribution? = null
    ): List<DailyMission> {
        val todays = lessons.filter { it.recordedAt?.atZone(zone)?.toLocalDate() == today }
        val completed = todays.count { it.completed } + if (session?.completed == true) 1 else 0
        val correct = todays.sumOf { GamificationRules.correctAnswers(it) } + (session?.correctAnswers ?: 0)
        val seconds = todays.sumOf { it.durationSeconds } + (session?.seconds ?: 0L)
        return listOf(
            DailyMission(READ_ID, "Lee una lección completa", "Termina una lectura hoy.", completed.coerceAtMost(1), 1, READ_REWARD),
            DailyMission(ANSWERS_ID, "Acierta en el mini-reto", "Responde bien 2 preguntas hoy.", correct.coerceAtMost(2), 2, ANSWERS_REWARD),
            DailyMission(TIME_ID, "Practica 5 minutos", "Suma 5 minutos de lectura hoy.", seconds.coerceAtMost(TIME_TARGET_SECONDS.toLong()).toInt(), TIME_TARGET_SECONDS, TIME_REWARD)
        )
    }

    fun claimKey(today: LocalDate, missionId: String) = "$today:$missionId"
}
