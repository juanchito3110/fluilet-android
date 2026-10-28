package com.fluilet.app.data.remote

import com.fluilet.app.data.remote.dto.AdminOverviewDto
import com.fluilet.app.data.remote.dto.MasterGrantDto
import com.fluilet.app.data.remote.dto.ResetPinRequestDto
import com.fluilet.app.data.remote.dto.ResetPinResponseDto
import com.fluilet.app.data.remote.dto.ChildCreateDto
import com.fluilet.app.data.remote.dto.ChildDto
import com.fluilet.app.data.remote.dto.ChildUpdateDto
import com.fluilet.app.data.remote.dto.AdaptTextRequestDto
import com.fluilet.app.data.remote.dto.AdaptationReadDto
import com.fluilet.app.data.remote.dto.DocumentDto
import com.fluilet.app.data.remote.dto.EducationalProgressDto
import com.fluilet.app.data.remote.dto.LessonGenerateDto
import com.fluilet.app.data.remote.dto.ModelInfoDto
import com.fluilet.app.data.remote.dto.ReadingCreateDto
import com.fluilet.app.data.remote.dto.ReadingDto
import com.fluilet.app.data.remote.dto.ReadingUpdateDto
import com.fluilet.app.data.remote.dto.UserDto
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoints protegidos (Authorization: Bearer). Contrato de `fluilet-backend` (solo lectura). */
interface FluiletApi {

    @GET("api/v1/users/me")
    suspend fun me(): UserDto

    /** Variante con token explícito: se usa durante el login, antes de persistir la sesión. */
    @GET("api/v1/users/me")
    suspend fun meWithToken(@retrofit2.http.Header("Authorization") bearer: String): UserDto

    // --- Perfiles de niño (solo rol tutor) ---------------------------------------------------

    @GET("api/v1/children")
    suspend fun listChildren(): List<ChildDto>

    @POST("api/v1/children")
    suspend fun createChild(@Body body: ChildCreateDto): ChildDto

    @PATCH("api/v1/children/{id}")
    suspend fun updateChild(@Path("id") id: String, @Body body: ChildUpdateDto): ChildDto

    @DELETE("api/v1/children/{id}")
    suspend fun deleteChild(@Path("id") id: String)

    // --- Adaptación (texto en RAM; el servidor solo conserva metadatos y métricas) -------------

    @Headers("$READ_TIMEOUT_HEADER: $ADAPTATION_READ_TIMEOUT_SECONDS")
    @POST("api/v1/adaptations/text")
    suspend fun adaptText(@Body body: AdaptTextRequestDto): AdaptationReadDto

    /**
     * Archivo PDF/TXT como cuerpo BINARIO crudo (Content-Type `application/pdf` o `text/plain`).
     * El backend rechaza multipart a propósito (evita spool a disco).
     */
    @Headers("$READ_TIMEOUT_HEADER: $ADAPTATION_READ_TIMEOUT_SECONDS")
    @POST("api/v1/adaptations/document")
    suspend fun adaptFile(
        @Body body: RequestBody,
        @Query("nivel_simplificacion_nlp") nlpLevel: Int,
        @Query("level") level: Int?,
        @Query("age_group") ageGroup: String?,
        @Query("child_id") childId: String?
    ): AdaptationReadDto

    /** Lección del banco del servidor por categoría/tema, adaptada al nivel pedido. */
    @Headers("$READ_TIMEOUT_HEADER: $ADAPTATION_READ_TIMEOUT_SECONDS")
    @POST("api/v1/adaptations/generate")
    suspend fun generateLesson(@Body body: LessonGenerateDto): AdaptationReadDto

    /** Recupera una adaptación retenida (por su `id` o por el `document.id`, que devuelve la última versión). */
    @Headers("$READ_TIMEOUT_HEADER: $ADAPTATION_READ_TIMEOUT_SECONDS")
    @GET("api/v1/adaptations/{id}")
    suspend fun adaptation(@Path("id") id: String): AdaptationReadDto

    /** Auditoría del modelo reentrenado (SARI, exactitud léxica, dataset, 5 bandas). Cualquier usuario autenticado. */
    @GET("api/v1/adaptations/model-info")
    suspend fun modelInfo(): ModelInfoDto

    /** Lecturas asignadas a un niño (metadatos); cada una se abre con [adaptation] por su `document.id`. */
    @GET("api/v1/children/{id}/library")
    suspend fun childLibrary(@Path("id") childId: String): List<DocumentDto>

    @GET("api/v1/documents")
    suspend fun documents(@Query("offset") offset: Int = 0): List<DocumentDto>

    // --- Sesiones de lectura (telemetría numérica) --------------------------------------------

    @POST("api/v1/children/{id}/readings")
    suspend fun startReading(@Path("id") childId: String, @Body body: ReadingCreateDto): ReadingDto

    @PATCH("api/v1/readings/{id}")
    suspend fun updateReading(@Path("id") readingId: String, @Body body: ReadingUpdateDto): ReadingDto

    @GET("api/v1/children/{id}/readings")
    suspend fun readings(@Path("id") childId: String): List<ReadingDto>

    /** Resumen educativo del perfil (tutor). Un servidor anterior devuelve una lista: el cliente cae al cálculo local. */
    @GET("api/v1/children/{id}/progress")
    suspend fun childEducationalProgress(@Path("id") childId: String): EducationalProgressDto

    /**
     * Datos de demostración (solo el tutor propietario): 7 días de práctica y seis lecturas reales retenidas.
     * Es idempotente; el servidor mide las seis adaptaciones, así que puede tardar.
     */
    @Headers("$READ_TIMEOUT_HEADER: $ADAPTATION_READ_TIMEOUT_SECONDS")
    @POST("api/v1/profiles/{id}/seed-demo")
    suspend fun seedDemo(@Path("id") profileId: String): EducationalProgressDto

    /** Confirma una misión diaria (el servidor ya la abonó al completarla: no hay segundo pago). */
    @POST("api/v1/profiles/{id}/daily-missions/{missionId}/claim")
    suspend fun claimDailyMission(@Path("id") profileId: String, @Path("missionId") missionId: String): EducationalProgressDto

    /** Resumen educativo del adulto independiente. */
    @GET("api/v1/readings/progress")
    suspend fun independentEducationalProgress(): EducationalProgressDto

    // Adulto independiente: sesiones propias, sin perfil de niño (backend >= dbd5901).

    @POST("api/v1/readings")
    suspend fun startIndependentReading(@Body body: ReadingCreateDto): ReadingDto

    @GET("api/v1/readings")
    suspend fun independentReadings(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100
    ): List<ReadingDto>

    // --- Sistema de Control Maestro (solo `master_admin`; el resto recibe 403) ---------------------

    @GET("api/v1/admin/overview")
    suspend fun adminOverview(): AdminOverviewDto

    /** Idempotente; el servidor lo serializa con un lock, así que puede tardar un poco. */
    @Headers("$READ_TIMEOUT_HEADER: $ADAPTATION_READ_TIMEOUT_SECONDS")
    @POST("api/v1/admin/ensure-demo-accounts")
    suspend fun adminEnsureDemoAccounts(): AdminOverviewDto

    @POST("api/v1/admin/profiles/{id}/grant")
    suspend fun adminGrant(@Path("id") profileId: String, @Body body: MasterGrantDto): EducationalProgressDto

    @DELETE("api/v1/admin/profiles/{id}")
    suspend fun adminDeleteProfile(@Path("id") profileId: String)

    @DELETE("api/v1/admin/tutors/{id}")
    suspend fun adminDeleteTutor(@Path("id") userId: String)

    @POST("api/v1/admin/tutors/{id}/reset-pin")
    suspend fun adminResetPin(@Path("id") userId: String, @Body body: ResetPinRequestDto): ResetPinResponseDto

    // --- Sesión ---------------------------------------------------------------------------------

    /** Revoca todas las sesiones del usuario en el servidor. */
    @POST("api/v1/auth/logout")
    suspend fun logout()
}

/** Cabecera interna que [com.fluilet.app.core.network.TimeoutOverrideInterceptor] consume y elimina. */
const val READ_TIMEOUT_HEADER = "X-Read-Timeout-Seconds"

/** Adaptar puede tardar: el servidor admite hasta 180 s de procesamiento (PROCESSING_TIMEOUT_SECONDS). */
const val ADAPTATION_READ_TIMEOUT_SECONDS = 190
