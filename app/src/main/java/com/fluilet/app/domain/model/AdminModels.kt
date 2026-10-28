package com.fluilet.app.domain.model

/** Cuenta registrada (tutores, adultos, maestro) en `GET /admin/overview`. Sin contraseñas, PIN ni tokens. */
data class AdminUser(
    val userId: String,
    val email: String,
    /** Rol tal como lo envía el backend (`TUTOR`, `ADULTO_INDEPENDIENTE`, `NINO`, `master_admin`). */
    val role: String,
    val isMasterAdmin: Boolean,
    val isActive: Boolean,
    val profilesCount: Int
) {
    val isTutor: Boolean get() = !isMasterAdmin && role.equals("TUTOR", ignoreCase = true)

    /** La cuenta maestra protegida nunca se puede dar de baja (el backend responde 400). */
    val canBeDeleted: Boolean get() = !isMasterAdmin
}

/** Perfil de niño del sistema, con sus saldos reales (`profile_controls` incluidos). */
data class AdminProfile(
    val profileId: String,
    val name: String,
    val tutorId: String,
    val tutorEmail: String,
    val isActive: Boolean,
    /** `6-8`, `9-11` o `12+`. */
    val ageGroup: String,
    val currentLevel: Int,
    val totalGems: Int,
    val totalXp: Int,
    val evolutionStage: Int,
    val evolutionTitle: String,
    val streakDays: Int,
    val completedLessons: Int,
    val unlockedBadges: Int
)

/** KPIs globales y listas de `GET /admin/overview` (y de `POST /admin/ensure-demo-accounts`). */
data class AdminOverview(
    val totalTutors: Int,
    val totalProfiles: Int,
    val totalAdaptations: Int,
    val totalReadingSessions: Int,
    val averageComprehension: Double,
    val users: List<AdminUser>,
    val profiles: List<AdminProfile>,
    /** Auditoría del modelo reentrenado (`model_info`); `null` si el servidor no la envía. */
    val modelAudit: ModelAudit? = null
) {
    val tutors: List<AdminUser> get() = users.filter { it.isTutor }

    companion object {
        val EMPTY = AdminOverview(0, 0, 0, 0, 0.0, emptyList(), emptyList())
    }
}

/**
 * Cuerpo de `POST /admin/profiles/{id}/grant`. Solo se envían los campos con valor (el backend rechaza `null`).
 * Reglas del servidor: `set_gems` no se combina con `add_gems`; `set_evolution_stage` no se combina con `add_xp`.
 */
data class MasterGrant(
    val addGems: Int? = null,
    val setGems: Int? = null,
    val addXp: Int? = null,
    val setEvolutionStage: Int? = null,
    val setLevel: Int? = null,
    val setAgeGroup: String? = null,
    val setStreakDays: Int? = null,
    val unlockAllRewards: Boolean = false
) {
    companion object {
        val AGE_GROUPS = listOf("6-8", "9-11", "12+")
        const val QUICK_XP = 100
        val QUICK_GEMS = listOf(50, 200, 500)

        fun gems(amount: Int) = MasterGrant(addGems = amount)
        fun exactGems(amount: Int) = MasterGrant(setGems = amount)
        fun xp(amount: Int = QUICK_XP) = MasterGrant(addXp = amount)
        fun stage(stage: Int) = MasterGrant(setEvolutionStage = stage)
        fun level(level: Int) = MasterGrant(setLevel = level)
        fun ageGroup(group: String) = MasterGrant(setAgeGroup = group)
        fun unlockAll() = MasterGrant(unlockAllRewards = true)
    }
}

/** Respuesta de `POST /admin/tutors/{id}/reset-pin`. */
data class PinResetResult(val userId: String, val pinReset: Boolean)
