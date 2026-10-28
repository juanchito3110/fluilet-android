package com.fluilet.app.di

import com.fluilet.app.BuildConfig
import com.fluilet.app.core.security.TutorPinManager
import com.fluilet.app.data.repository.AuthRepositoryImpl
import com.fluilet.app.data.repository.ChildRepositoryImpl
import com.fluilet.app.data.repository.FluiBotInventoryStore
import com.fluilet.app.data.repository.InMemoryReaderSettingsRepository
import com.fluilet.app.data.repository.LocalAdaptationRepository
import com.fluilet.app.data.repository.LocalReadingSessionRepository
import com.fluilet.app.data.repository.RemoteModelInfoRepository
import com.fluilet.app.data.repository.ResilientAdaptationRepository
import com.fluilet.app.data.repository.RemoteReadingSessionRepository
import com.fluilet.app.domain.gamification.FluiBotInventoryRepository
import com.fluilet.app.domain.repository.AdaptationRepository
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.repository.ChildRepository
import com.fluilet.app.domain.repository.ModelInfoRepository
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
    @Binds abstract fun bindAdminRepository(impl: com.fluilet.app.data.repository.AdminRepositoryImpl): com.fluilet.app.domain.repository.AdminRepository
    @Binds abstract fun bindChildRepository(impl: ChildRepositoryImpl): ChildRepository
    @Binds abstract fun bindReaderSettings(impl: InMemoryReaderSettingsRepository): ReaderSettingsRepository
    @Binds abstract fun bindTutorPin(impl: TutorPinManager): TutorPinRepository
    @Binds abstract fun bindFluiBotInventory(impl: FluiBotInventoryStore): FluiBotInventoryRepository
    @Binds abstract fun bindModelInfo(impl: RemoteModelInfoRepository): ModelInfoRepository

    companion object {
        /**
         * `USE_REMOTE_ADAPTATION` (BuildConfig): el modelo reentrenado del servidor con respaldo local cuando no hay
         * red, o solo el motor local (builds de desarrollo sin backend).
         */
        @Provides
        @Singleton
        fun provideAdaptationRepository(
            resilient: ResilientAdaptationRepository,
            local: LocalAdaptationRepository
        ): AdaptationRepository = if (BuildConfig.USE_REMOTE_ADAPTATION) resilient else local

        @Provides
        @Singleton
        fun provideReadingSessionRepository(
            remote: RemoteReadingSessionRepository,
            local: LocalReadingSessionRepository
        ): ReadingSessionRepository = if (BuildConfig.USE_REMOTE_ADAPTATION) remote else local
    }
}
