package com.fluilet.app

import android.app.Application
import android.content.Context
import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.core.locale.AppLocale
import com.fluilet.app.core.network.TlsConfig
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FluiletApplication : Application() {

    @Inject lateinit var sessionManager: SessionManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
        // Debe ocurrir antes del primer uso de OkHttp: habilita TLS 1.3 en Android 8-9.
        TlsConfig.installConscryptIfNeeded()
    }

    override fun onCreate() {
        super.onCreate()
        // Primer acceso al Keystore/EncryptedSharedPreferences fuera del hilo principal.
        appScope.launch { sessionManager.restore() }
    }
}
