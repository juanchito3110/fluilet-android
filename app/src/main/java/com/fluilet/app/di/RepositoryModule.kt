package com.fluilet.app.di

import com.fluilet.app.data.repository.InMemoryReaderSettingsRepository
import com.fluilet.app.data.repository.InMemoryTutorRepository
import com.fluilet.app.data.repository.RetrofitAuthRepository
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.repository.ReaderSettingsRepository
import com.fluilet.app.domain.repository.TutorRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindReaderSettingsRepository(
        impl: InMemoryReaderSettingsRepository
    ): ReaderSettingsRepository

    @Binds
    abstract fun bindTutorRepository(
        impl: InMemoryTutorRepository
    ): TutorRepository

    @Binds
    abstract fun bindAuthRepository(
        impl: RetrofitAuthRepository
    ): AuthRepository
}