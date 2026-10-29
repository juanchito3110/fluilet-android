package com.fluilet.app.domain.repository

import com.fluilet.app.domain.model.AdminOverview
import com.fluilet.app.domain.model.MasterGrant
import com.fluilet.app.domain.model.PinResetResult
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.AdaptationSource
import com.fluilet.app.domain.model.AgeGroup
import com.fluilet.app.domain.model.LibraryItem
import com.fluilet.app.domain.model.ModelAudit
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.ChildDraft
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadingProgress
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.model.RegistrationData
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.LessonRequest
import com.fluilet.app.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<AuthSession>
    suspend fun register(data: RegistrationData): Result<User>
    suspend fun currentUser(): Result<User>

    /** Cierra sesión: borra tokens y datos sensibles locales. */
    suspend fun logout()

    /** Reautenticación del tutor (para restablecer el PIN). Valida la contraseña contra el backend. */
    suspend fun reauthenticate(password: String): Result<Unit>

    /**
     * Recuperación de contraseña (backend a2dd836). Cada llamada devuelve el mensaje en español del servidor.
     * Pedir el código responde lo mismo exista o no la cuenta; un código incorrecto, vencido o bloqueado falla con
     * [com.fluilet.app.core.network.AppError.InvalidResetCode].
     */
    suspend fun requestPasswordReset(email: String): Result<String>
    suspend fun verifyResetCode(email: String, code: String): Result<String>
    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<String>
}

interface ChildRepository {
    suspend fun list(): Result<List<ChildProfile>>
    suspend fun create(draft: ChildDraft): Result<ChildProfile>
    suspend fun update(id: String, draft: ChildDraft): Result<ChildProfile>
    suspend fun updateNlpLevel(id: String, level: NlpLevel): Result<ChildProfile>
    suspend fun delete(id: String): Result<Unit>
}

/**
 * Adaptación de textos (modelo reentrenado del servidor + Szigriszt-Pazos). El texto resultante nunca se persiste.
 * [ageGroup] es la edad didáctica del perfil infantil activo (`6-8`, `9-11`, `12+`); `null` para adultos.
 */
interface AdaptationRepository {
    suspend fun adapt(
        source: AdaptationSource,
        nlpLevel: NlpLevel,
        childId: String?,
        ageGroup: AgeGroup? = null
    ): Result<AdaptationResult>

    /** Genera una lección por categoría/tema (banco del servidor) ya adaptada al nivel pedido. */
    suspend fun generate(request: LessonRequest, childId: String?): Result<AdaptationResult>

    /** Recupera una adaptación retenida (incluido su original) por id de adaptación o de documento. */
    suspend fun fetch(id: String): Result<AdaptationResult> =
        Result.failure(com.fluilet.app.core.network.AppError.Unsupported("Esta versión no recupera adaptaciones guardadas."))

    /** Lecturas asignadas a un niño (`GET /children/{id}/library`), sin texto: se abren con [fetch]. */
    suspend fun library(childId: String): Result<List<LibraryItem>> =
        Result.failure(com.fluilet.app.core.network.AppError.Unsupported("La biblioteca requiere el servidor."))
}

/**
 * Auditoría del modelo reentrenado (`GET /adaptations/model-info`). Nunca falla: sin servidor devuelve los valores
 * verificados del commit 42482e0 marcados con `fromServer = false`.
 */
interface ModelInfoRepository {
    suspend fun audit(): ModelAudit
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

    /**
     * Resumen educativo (cumplimiento, tiempo, comprensión, nivel recomendado, evolución e insignias).
     * Si el endpoint de progreso falla se calcula a partir de las lecturas; [baselineLevel] es el nivel
     * del perfil (o 2 para un adulto) cuando aún no hay lecciones completadas.
     */
    suspend fun educationalProgress(childId: String?, baselineLevel: Int): Result<EducationalProgress>

    /** `POST /profiles/{id}/seed-demo`: historial de 7 días y seis lecturas demo (solo tutor). */
    suspend fun seedDemo(childId: String): Result<EducationalProgress> =
        Result.failure(com.fluilet.app.core.network.AppError.Unsupported("Los datos demo requieren el servidor."))

    /** `POST /profiles/{id}/daily-missions/{id}/claim`: devuelve el resumen con las gemas ya abonadas. */
    suspend fun claimMission(childId: String, missionId: String): Result<EducationalProgress> =
        Result.failure(com.fluilet.app.core.network.AppError.Unsupported("Las misiones se confirman en el servidor."))
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

    /**
     * PIN público de la cuenta de demostración del tutor (1234): se guarda aunque sea una secuencia que
     * [setInitialPin] rechazaría. Solo lo usa el login de `tutor@fluilet.com`.
     */
    fun configureDemoPin(pin: String): Result<Unit> = resetAfterReauthentication(pin)

    /** Cambio de PIN: exige el PIN actual. */
    fun changePin(currentPin: String, newPin: String): com.fluilet.app.domain.model.PinResult

    /** Restablecimiento tras reautenticar la contraseña del tutor contra el backend. */
    fun resetAfterReauthentication(newPin: String): Result<Unit>
}

/** Sistema de Control Maestro (`/api/v1/admin/...`): solo la cuenta `master_admin` (el resto recibe 403). */
interface AdminRepository {
    suspend fun overview(): Result<AdminOverview>

    /** Recrea `tutor@fluilet.com` y sus 4 niños si faltan (idempotente) y devuelve el overview. */
    suspend fun ensureDemoAccounts(): Result<AdminOverview>

    suspend fun grant(profileId: String, grant: MasterGrant): Result<EducationalProgress>
    suspend fun deleteProfile(profileId: String): Result<Unit>
    suspend fun deleteTutor(userId: String): Result<Unit>

    /** Restablece el PIN parental del tutor (1234 por omisión). */
    suspend fun resetPin(userId: String, pin: String = "1234"): Result<PinResetResult>
}
