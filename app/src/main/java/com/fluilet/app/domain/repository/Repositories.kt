package com.fluilet.app.domain.repository

import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.AdaptationSource
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.ChildDraft
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadingProgress
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.model.RegistrationData
import com.fluilet.app.domain.model.SessionSummary
import com.fluilet.app.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<AuthSession>
    suspend fun register(data: RegistrationData): Result<User>
    suspend fun currentUser(): Result<User>

    /** Cierra sesión: borra tokens y datos sensibles locales. */
    suspend fun logout()

    /** Reautenticación del tutor (para restablecer el PIN). Valida la contraseña contra el backend. */
    suspend fun reauthenticate(password: String): Result<Unit>
}

interface ChildRepository {
    suspend fun list(): Result<List<ChildProfile>>
    suspend fun create(draft: ChildDraft): Result<ChildProfile>
    suspend fun update(id: String, draft: ChildDraft): Result<ChildProfile>
    suspend fun updateNlpLevel(id: String, level: NlpLevel): Result<ChildProfile>
    suspend fun delete(id: String): Result<Unit>
}

/** Adaptación de textos (mT5 + Szigriszt-Pazos). El texto resultante nunca se persiste. */
interface AdaptationRepository {
    suspend fun adapt(
        source: AdaptationSource,
        nlpLevel: NlpLevel,
        childId: String?
    ): Result<AdaptationResult>
}

/**
 * Telemetría numérica de sesiones de lectura. En el servidor una sesión pertenece a un perfil de
 * niño y a un documento asignado: se abre con [start] al empezar a leer y se actualiza con
 * [progress] (monótono). Las métricas Szigriszt-Pazos las calcula el servidor al adaptar.
 */
interface ReadingSessionRepository {
    /** Devuelve el id de la sesión, o `null` si no hay seguimiento (p. ej. adulto sin perfil de niño). */
    suspend fun start(childId: String?, adaptation: AdaptationResult): Result<String?>
    suspend fun progress(readingId: String, progress: ReadingProgress): Result<Unit>
    suspend fun history(childId: String?): Result<List<SessionSummary>>
}

interface ReaderSettingsRepository {
    suspend fun getSettings(): ReadingSettings
    suspend fun saveSettings(settings: ReadingSettings): Result<Unit>
}

/** Muro Parental: PIN del tutor almacenado localmente como sal + hash, con límite de intentos. */
interface TutorPinRepository {
    fun isConfigured(): Boolean
    fun verify(pin: String): com.fluilet.app.domain.model.PinResult

    /** Primer registro del PIN. Falla si ya existe uno (usar cambio o restablecimiento). */
    fun setInitialPin(pin: String): Result<Unit>

    /** Cambio de PIN: exige el PIN actual. */
    fun changePin(currentPin: String, newPin: String): com.fluilet.app.domain.model.PinResult

    /** Restablecimiento tras reautenticar la contraseña del tutor contra el backend. */
    fun resetAfterReauthentication(newPin: String): Result<Unit>
}
