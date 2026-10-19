package com.fluilet.app.domain.model

/**
 * Rol de la sesión. El backend solo emite `tutor` y `adulto_independiente` en JWT, pero el
 * contrato admite `nino`: se conserva en el enum y se deserializa de forma segura para que
 * un rol inesperado NUNCA provoque un crash (ver [fromWire]).
 */
enum class UserRole(val wireValue: String) {
    TUTOR("tutor"),
    ADULTO_INDEPENDIENTE("adulto_independiente"),
    NINO("nino");

    /** El niño no tiene credenciales: opera como perfil activo protegido por el PIN del tutor. */
    val isChildView: Boolean get() = this == NINO

    /** Solo el tutor administra perfiles de niño (RBAC del backend en `/children`). */
    val canManageChildren: Boolean get() = this == TUTOR

    companion object {
        /** Devuelve `null` para valores desconocidos; el llamador decide el fallback seguro. */
        fun fromWire(raw: String?): UserRole? {
            val normalized = raw?.trim()?.lowercase()?.replace('ñ', 'n') ?: return null
            return when (normalized) {
                "tutor", "padre", "docente", "parent" -> TUTOR
                "adulto_independiente", "adulto", "independent_adult" -> ADULTO_INDEPENDIENTE
                "nino", "child" -> NINO
                else -> null
            }
        }
    }
}
