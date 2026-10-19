package com.fluilet.app.di

import android.content.Context
import com.fluilet.app.core.security.EncryptedPrefsSecureStore
import com.fluilet.app.core.security.SecureStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecureStore(@ApplicationContext context: Context): SecureStore =
        EncryptedPrefsSecureStore(context)
}
