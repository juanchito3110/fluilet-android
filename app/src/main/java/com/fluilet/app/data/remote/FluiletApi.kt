package com.fluilet.app.data.remote

import com.fluilet.app.data.remote.dto.ChildCreateDto
import com.fluilet.app.data.remote.dto.ChildDto
import com.fluilet.app.data.remote.dto.ChildUpdateDto
import com.fluilet.app.data.remote.dto.AdaptTextRequestDto
import com.fluilet.app.data.remote.dto.AdaptationReadDto
import com.fluilet.app.data.remote.dto.DocumentDto
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
    @POST("api/v1/adaptations/file")
    suspend fun adaptFile(
        @Body body: RequestBody,
        @Query("nivel_simplificacion_nlp") nlpLevel: Int,
        @Query("child_id") childId: String?
    ): AdaptationReadDto

    @GET("api/v1/documents")
    suspend fun documents(@Query("offset") offset: Int = 0): List<DocumentDto>

    // --- Sesiones de lectura (telemetría numérica) --------------------------------------------

    @POST("api/v1/children/{id}/readings")
    suspend fun startReading(@Path("id") childId: String, @Body body: ReadingCreateDto): ReadingDto

    @PATCH("api/v1/readings/{id}")
    suspend fun updateReading(@Path("id") readingId: String, @Body body: ReadingUpdateDto): ReadingDto

    @GET("api/v1/children/{id}/readings")
    suspend fun readings(@Path("id") childId: String): List<ReadingDto>

    // Adulto independiente: sesiones propias, sin perfil de niño (backend >= dbd5901).

    @POST("api/v1/readings")
    suspend fun startIndependentReading(@Body body: ReadingCreateDto): ReadingDto

    @GET("api/v1/readings")
    suspend fun independentReadings(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100
    ): List<ReadingDto>

    // --- Sesión ---------------------------------------------------------------------------------

    /** Revoca todas las sesiones del usuario en el servidor. */
    @POST("api/v1/auth/logout")
    suspend fun logout()
}

/** Cabecera interna que [com.fluilet.app.core.network.TimeoutOverrideInterceptor] consume y elimina. */
const val READ_TIMEOUT_HEADER = "X-Read-Timeout-Seconds"

/** Adaptar puede tardar: el servidor admite hasta 180 s de procesamiento (PROCESSING_TIMEOUT_SECONDS). */
const val ADAPTATION_READ_TIMEOUT_SECONDS = 190
