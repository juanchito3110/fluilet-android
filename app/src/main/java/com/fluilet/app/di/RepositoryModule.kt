package com.fluilet.app.di

import com.fluilet.app.BuildConfig
import com.fluilet.app.core.security.TutorPinManager
import com.fluilet.app.data.repository.AuthRepositoryImpl
import com.fluilet.app.data.repository.ChildRepositoryImpl
import com.fluilet.app.data.repository.FluiBotInventoryStore
import com.fluilet.app.data.repository.InMemoryReaderSettingsRepository
import com.fluilet.app.data.repository.LocalAdaptationRepository
import com.fluilet.app.data.repository.LocalReadingSessionRepository
import com.fluilet.app.data.repository.RemoteAdaptationRepository
import com.fluilet.app.data.repository.RemoteReadingSessionRepository
import com.fluilet.app.domain.gamification.FluiBotInventoryRepository
import com.fluilet.app.domain.repository.AdaptationRepository
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.repository.ChildRepository
import com.fluilet.app.domain.repository.ReaderSettingsRepository
import com.fluilet.app.domain.repository.ReadingSessionRepository
import com.fluilet.app.domain.repository.TutorPinRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun bindChildRepository(impl: ChildRepositoryImpl): ChildRepository
    @Binds abstract fun bindReaderSettings(impl: InMemoryReaderSettingsRepository): ReaderSettingsRepository
    @Binds abstract fun bindTutorPin(impl: TutorPinManager): TutorPinRepository
    @Binds abstract fun bindFluiBotInventory(impl: FluiBotInventoryStore): FluiBotInventoryRepository

    companion object {
        /**
         * `USE_REMOTE_ADAPTATION` (BuildConfig) alterna entre el motor del servidor (mT5) y la
         * adaptación local de respaldo. Activar cuando el backend publique los endpoints.
         */
        @Provides
        @Singleton
        fun provideAdaptationRepository(
            remote: RemoteAdaptationRepository,
            local: LocalAdaptationRepository
        ): AdaptationRepository = if (BuildConfig.USE_REMOTE_ADAPTATION) remote else local

        @Provides
        @Singleton
        fun provideReadingSessionRepository(
            remote: RemoteReadingSessionRepository,
            local: LocalReadingSessionRepository
        ): ReadingSessionRepository = if (BuildConfig.USE_REMOTE_ADAPTATION) remote else local
    }
}
