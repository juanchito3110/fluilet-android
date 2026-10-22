package com.fluilet.app.data.repository

import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.apiCall
import com.fluilet.app.data.mapper.toDomain
import com.fluilet.app.data.mapper.toRecord
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.remote.dto.ReadingCreateDto
import com.fluilet.app.data.remote.dto.ReadingDto
import com.fluilet.app.data.remote.dto.ReadingUpdateDto
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.ReadingProgress
import com.fluilet.app.domain.model.ReadingRecord
import com.fluilet.app.domain.progress.EducationalProgressCalculator
import com.fluilet.app.domain.repository.ReadingSessionRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Telemetría y progreso contra el backend. El servidor valida que el tiempo no supere el real
 * transcurrido (+5 s), que el avance no retroceda y que el puntaje de comprensión coincida con los
 * contadores; las métricas Szigriszt-Pazos las fija él.
 * - Niño (perfil del tutor): `POST /children/{id}/readings` y `GET /children/{id}/progress`.
 * - Adulto independiente: `POST /readings` y `GET /readings/progress`, sin perfil de niño.
 * En ambos casos el avance se actualiza con `PATCH /readings/{id}`.
 */
@Singleton
class RemoteReadingSessionRepository @Inject constructor(
    private val api: FluiletApi
) : ReadingSessionRepository {

    override suspend fun start(childId: String?, adaptation: AdaptationResult): Result<String?> {
        val documentId = adaptation.documentId ?: return Result.success(null) // adaptación local: sin seguimiento
        val body = ReadingCreateDto(documentId, adaptation.category, adaptation.simplificationLevel)
        return apiCall {
            if (childId != null) api.startReading(childId, body) else api.startIndependentReading(body)
        }.map { it.id }
    }

    override suspend fun progress(readingId: String, progress: ReadingProgress): Result<Unit> {
        // Sin respuestas no hay puntaje medido: se omiten los tres campos de comprensión.
        val quiz = progress.comprehension?.takeIf { it.answered > 0 }
        return apiCall {
            api.updateReading(
                readingId,
                ReadingUpdateDto(
                    readingTimeSeconds = progress.readingTimeSeconds,
                    completionPercent = progress.completionPercent.toDouble(),
                    completed = progress.completed && progress.completionPercent == 100,
                    category = progress.category,
                    simplificationLevel = progress.simplificationLevel,
                    comprehensionScore = quiz?.score,
                    questionsAnswered = quiz?.answered,
                    questionsCorrect = quiz?.correct,
                    ttsSpeedUsed = progress.ttsSpeed
                )
            )
        }.map { }
    }

    /**
     * Intenta el endpoint de progreso; si falla (servidor anterior, red, error) calcula el resumen a
     * partir de la lista de lecturas, y solo si eso también falla devuelve el error al llamador.
     */
    override suspend fun educationalProgress(childId: String?, baselineLevel: Int): Result<EducationalProgress> {
        val remote = apiCall {
            if (childId != null) api.childEducationalProgress(childId) else api.independentEducationalProgress()
        }.mapCatching { it.toDomain() }
        if (remote.isSuccess) return remote

        return apiCall { if (childId != null) api.readings(childId) else api.independentReadings() }
            .map { readings: List<ReadingDto> ->
                EducationalProgressCalculator.summarize(readings.map { it.toRecord() }, baselineLevel).copy(isEstimated = true)
            }
            .recoverCatching { throw (remote.exceptionOrNull() ?: it) as? AppError ?: AppError.Unknown(it) }
    }
}

/** Respaldo local solo numérico (builds con `USE_REMOTE_ADAPTATION=false`). */
@Singleton
class LocalReadingSessionRepository @Inject constructor() : ReadingSessionRepository {

    private class Entry(val childId: String?, var record: ReadingRecord)

    private val entries = mutableMapOf<String, Entry>()

    override suspend fun start(childId: String?, adaptation: AdaptationResult): Result<String?> = synchronized(entries) {
        val id = UUID.randomUUID().toString()
        val m = adaptation.metrics
        entries[id] = Entry(
            childId,
            ReadingRecord(
                id = id, startedAt = Instant.now(),
                category = adaptation.category ?: "lectura_libre",
                level = adaptation.simplificationLevel ?: 2,
                completed = false, completionPercent = 0.0, readingSeconds = 0,
                comprehensionScore = null, questionsAnswered = 0,
                originalScore = m.originalSzigrisztScore, adaptedScore = m.adaptedSzigrisztScore
            )
        )
        Result.success(id)
    }

    override suspend fun progress(readingId: String, progress: ReadingProgress): Result<Unit> = synchronized(entries) {
        entries[readingId]?.let { entry ->
            val quiz = progress.comprehension?.takeIf { it.answered > 0 }
            entry.record = entry.record.copy(
                readingSeconds = progress.readingTimeSeconds,
                completionPercent = progress.completionPercent.toDouble(),
                completed = progress.completed,
                comprehensionScore = quiz?.score ?: entry.record.comprehensionScore,
                questionsAnswered = quiz?.answered ?: entry.record.questionsAnswered
            )
        }
        Result.success(Unit)
    }

    override suspend fun educationalProgress(childId: String?, baselineLevel: Int): Result<EducationalProgress> = synchronized(entries) {
        val records = entries.values.filter { it.childId == childId }.map { it.record }
        Result.success(EducationalProgressCalculator.summarize(records, baselineLevel).copy(isEstimated = true))
    }
}
