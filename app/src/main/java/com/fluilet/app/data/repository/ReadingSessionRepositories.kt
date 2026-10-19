package com.fluilet.app.data.repository

import com.fluilet.app.core.network.apiCall
import com.fluilet.app.data.mapper.toSummary
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.remote.dto.ReadingCreateDto
import com.fluilet.app.data.remote.dto.ReadingUpdateDto
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.ReadingProgress
import com.fluilet.app.domain.model.SessionSummary
import com.fluilet.app.domain.repository.ReadingSessionRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Telemetría contra el backend. Una sesión se abre con `POST /children/{id}/readings` (documento
 * ya asignado al adaptar con `child_id`) y se actualiza con `PATCH /readings/{id}`. El servidor
 * valida que el tiempo no supere el real transcurrido (+5 s) y que el avance no retroceda.
 * Los adultos independientes no tienen perfil de niño: no hay seguimiento remoto.
 */
@Singleton
class RemoteReadingSessionRepository @Inject constructor(
    private val api: FluiletApi
) : ReadingSessionRepository {

    override suspend fun start(childId: String?, adaptation: AdaptationResult): Result<String?> {
        val documentId = adaptation.documentId
        if (childId == null || documentId == null) return Result.success(null)
        return apiCall { api.startReading(childId, ReadingCreateDto(documentId)) }.map { it.id }
    }

    override suspend fun progress(readingId: String, progress: ReadingProgress): Result<Unit> =
        apiCall {
            api.updateReading(
                readingId,
                ReadingUpdateDto(
                    readingTimeSeconds = progress.readingTimeSeconds,
                    completionPercent = progress.completionPercent.toDouble(),
                    completed = progress.completed && progress.completionPercent == 100
                )
            )
        }.map { }

    override suspend fun history(childId: String?): Result<List<SessionSummary>> {
        if (childId == null) return Result.success(emptyList())
        return apiCall {
            val readings = api.readings(childId)
            // Las métricas viven en el documento; si no se pueden leer, el historial sigue mostrando tiempo y avance.
            val documents = runCatching { api.documents() }.getOrDefault(emptyList()).associateBy { it.id }
            readings.mapIndexed { index, reading -> reading.toSummary(index, documents[reading.documentId]) }
        }
    }
}

/** Respaldo local solo numérico (builds con `USE_REMOTE_ADAPTATION=false`). */
@Singleton
class LocalReadingSessionRepository @Inject constructor() : ReadingSessionRepository {

    private data class Entry(val childId: String?, var summary: SessionSummary)

    private val entries = mutableMapOf<String, Entry>()

    override suspend fun start(childId: String?, adaptation: AdaptationResult): Result<String?> = synchronized(entries) {
        val id = UUID.randomUUID().toString()
        val position = entries.values.count { it.childId == childId } + 1
        val m = adaptation.metrics
        entries[id] = Entry(
            childId,
            SessionSummary(
                id, "Sesión $position", Instant.now(), 0, 0, false,
                m.originalSzigrisztScore, m.adaptedSzigrisztScore, m.scoreDelta
            )
        )
        Result.success(id)
    }

    override suspend fun progress(readingId: String, progress: ReadingProgress): Result<Unit> = synchronized(entries) {
        entries[readingId]?.let {
            it.summary = it.summary.copy(
                readingTimeSeconds = progress.readingTimeSeconds,
                completionPercent = progress.completionPercent,
                completed = progress.completed
            )
        }
        Result.success(Unit)
    }

    override suspend fun history(childId: String?): Result<List<SessionSummary>> = synchronized(entries) {
        Result.success(entries.values.filter { it.childId == childId }.map { it.summary })
    }
}
