package com.fluilet.app.data.repository

import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.repository.ReaderSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Preferencias de lectura en RAM (no contienen datos personales ni texto). */
@Singleton
class InMemoryReaderSettingsRepository @Inject constructor() : ReaderSettingsRepository {

    @Volatile private var current = ReadingSettings()

    override suspend fun getSettings(): ReadingSettings = current

    override suspend fun saveSettings(settings: ReadingSettings): Result<Unit> {
        current = settings
        return Result.success(Unit)
    }
}
