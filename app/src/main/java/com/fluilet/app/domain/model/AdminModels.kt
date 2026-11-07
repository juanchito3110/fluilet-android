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

    /** Tipo de cuenta para la etiqueta del panel (tutor, adulto independiente, niño...). */
    val accountType: AccountType get() = AccountType.of(role, isMasterAdmin)

    /** La cuenta maestra protegida nunca se puede dar de baja (el backend responde 400). */
    val canBeDeleted: Boolean get() = !isMasterAdmin
}

/** Tipo de cuenta tal como se muestra en el Panel de Administración, derivado del rol que envía el backend. */
enum class AccountType(val label: String) {
    TUTOR("Tutor"),
    INDEPENDENT_ADULT("Adulto independiente"),
    CHILD("Cuenta de niño"),
    MASTER("Cuenta maestra"),
    OTHER("Cuenta registrada");

    companion object {
        fun of(role: String, isMasterAdmin: Boolean = false): AccountType {
            if (isMasterAdmin) return MASTER
            val r = role.trim().uppercase()
            return when {
                r == "TUTOR" -> TUTOR
                r.startsWith("ADULTO") || r.contains("INDEPENDIENTE") || r == "ADULT" -> INDEPENDENT_ADULT
                r == "NINO" || r == "NIÑO" || r == "CHILD" -> CHILD
                r.contains("MASTER") -> MASTER
                else -> OTHER
            }
        }
    }
}

/** Perfil lector infantil o independiente, con sus saldos reales incluidos. */
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
    /** Nombre conservado para la pantalla existente; incluye todas las cuentas administrables. */
    val tutors: List<AdminUser> get() = users.filter { !it.isMasterAdmin }

    /** Perfiles lectores que pertenecen a la cuenta [userId] (los de un tutor o el propio de un adulto independiente). */
    fun profilesOf(userId: String): List<AdminProfile> = profiles.filter { it.tutorId == userId }

    /** Cuenta dueña de un perfil lector, si el servidor la incluyó en la lista. */
    fun ownerOf(profile: AdminProfile): AdminUser? = users.firstOrNull { it.userId == profile.tutorId }

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
