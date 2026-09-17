package com.fluilet.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.compose.rememberNavController
import com.fluilet.app.core.focusmode.FocusModeManager
import com.fluilet.app.core.focusmode.FocusModeResult
import com.fluilet.app.presentation.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var focusModeManager: FocusModeManager

    private val notificationPolicyAccessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (focusModeManager.isNotificationPolicyAccessGranted()) {
                runActivateFocusMode()
            } else {
                Log.w(TAG, "El permiso de Acceso a No Molestar no fue otorgado.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            AppNavigation(
                navController = navController,
                onToggleFocusModeHardware = { activating ->
                    if (activating) requestFocusMode() else releaseFocusMode()
                }
            )
        }
    }

    private fun requestFocusMode() {
        if (!focusModeManager.isNotificationPolicyAccessGranted()) {
            notificationPolicyAccessLauncher.launch(focusModeManager.buildNotificationPolicyAccessIntent())
            return
        }
        runActivateFocusMode()
    }

    private fun runActivateFocusMode() {
        when (val result = focusModeManager.activateFocusMode(this)) {
            is FocusModeResult.Success -> Log.i(TAG, "Modo Enfoque activado: DND + Screen Pinning.")
            is FocusModeResult.PermissionRequired ->
                notificationPolicyAccessLauncher.launch(focusModeManager.buildNotificationPolicyAccessIntent())
            is FocusModeResult.Error -> Log.w(TAG, "Modo Enfoque: ${result.message}")
        }
    }

    private fun releaseFocusMode() {
        when (val result = focusModeManager.deactivateFocusMode(this, tutorCredentialsValidated = true)) {
            is FocusModeResult.Success -> Log.i(TAG, "Modo Enfoque desactivado correctamente.")
            is FocusModeResult.PermissionRequired -> Log.w(TAG, "Falta permiso para restaurar notificaciones.")
            is FocusModeResult.Error -> Log.w(TAG, "Modo Enfoque: ${result.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (focusModeManager.isScreenPinningActive(this)) {
            focusModeManager.stopScreenPinning(this, tutorCredentialsValidated = true)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}