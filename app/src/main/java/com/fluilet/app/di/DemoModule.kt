package com.fluilet.app.di

import com.fluilet.app.BuildConfig
import com.fluilet.app.domain.usecase.DemoConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DemoModule {
    @Provides
    @Singleton
    fun provideDemoConfig(): DemoConfig = DemoConfig(BuildConfig.DEMO_ACCOUNTS_ENABLED)
}
