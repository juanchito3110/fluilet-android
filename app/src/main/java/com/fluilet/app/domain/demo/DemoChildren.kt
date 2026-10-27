package com.fluilet.app.domain.demo

import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.DifficultyType
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.NlpLevel
import java.time.LocalDate
import java.time.ZoneId

/**
 * Respaldo local, idéntico al del backend (`docs/demo-accounts.md`), de los 4 niños de `tutor@fluilet.com` en sus
 * 4 fases. Solo se usa si el servidor no responde; con conexión mandan siempre los datos reales.
 */
object DemoChildren {
    const val ID_PREFIX = "demo-"

    class Phase(
        val id: String, val name: String, val ageYears: Int, val ageGroup: String, val level: Int,
        val gems: Int, val xp: Int, val streakDays: Int, val lessons: Int, val difficulty: DifficultyType
    )

    val phases = listOf(
        Phase("${ID_PREFIX}sofia", "Sofía", 7, "6-8", 1, 45, 40, 1, 1, DifficultyType.DISLEXIA),
        Phase("${ID_PREFIX}mateo", "Mateo", 10, "9-11", 2, 160, 190, 3, 3, DifficultyType.TDAH),
        Phase("${ID_PREFIX}diego", "Diego", 11, "9-11", 4, 340, 460, 6, 5, DifficultyType.COMBINADO),
        Phase("${ID_PREFIX}valentina", "Valentina", 13, "12+", 5, 900, 980, 12, 6, DifficultyType.DISLEXIA)
    )

    fun isDemoId(id: String?): Boolean = id?.startsWith(ID_PREFIX) == true

    fun profiles(today: LocalDate = LocalDate.now()): List<ChildProfile> = phases.map { p ->
        ChildProfile(
            id = p.id, name = p.name,
            birthDate = today.minusYears(p.ageYears.toLong()).minusMonths(3),
            difficulty = p.difficulty, nlpLevel = NlpLevel.fromValue(p.level), palette = AccessiblePalette.SEPIA,
            fontSizeSp = 20f, lineSpacing = 1.8f, isActive = true, ageGroup = p.ageGroup
        )
    }

    fun progress(id: String, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): EducationalProgress? =
        phases.firstOrNull { it.id == id }?.let { DemoData.phaseProgress(it.lessons, it.gems, it.xp, it.streakDays, it.level, today, zone) }
}
