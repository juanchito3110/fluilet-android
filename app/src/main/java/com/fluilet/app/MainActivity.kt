package com.fluilet.app

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fluilet.app.core.focusmode.FocusModeManager
import com.fluilet.app.core.locale.AppLocale
import com.fluilet.app.core.focusmode.FocusModeResult
import com.fluilet.app.core.focusmode.FocusSessionController
import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.presentation.navigation.AppNavigation
import com.fluilet.app.presentation.theme.FluiletTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Traduce el estado de [FocusSessionController] a `startLockTask()` / `stopLockTask()`.
 * La salida del anclaje solo ocurre cuando el controlador cierra la sesión, y éste exige la
 * prueba de PIN del tutor (Muro Parental): aquí no hay ninguna ruta de salida sin validar.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var focusModeManager: FocusModeManager
    @Inject lateinit var focusController: FocusSessionController

    /** Anclaje que pidió la app (no tocamos un anclaje que el usuario activó por su cuenta). */
    private var lockRequested = false

    // Español siempre, incluso con el sistema en inglés: afecta a Material 3 (selector de fecha, etc.).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Release: el contenido (textos de menores) no aparece en capturas ni en la vista de recientes.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                focusController.state
                    .map { it.isActive && it.lockRequested }
                    .distinctUntilChanged()
                    .collect(::applyLock)
            }
        }

        setContent {
            FluiletTheme(palette = AccessiblePalette.DEFAULT) {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Si el niño salió del anclaje con el gesto del sistema, se vuelve a anclar al regresar.
        if (lockRequested && !focusModeManager.isScreenPinningActive(this)) {
            focusModeManager.startScreenPinning(this)
        }
    }

    private fun applyLock(shouldLock: Boolean) {
        if (shouldLock) {
            lockRequested = true
            handleResult(focusModeManager.startScreenPinning(this))
        } else if (lockRequested) {
            lockRequested = false
            handleResult(focusModeManager.stopScreenPinning(this))
        }
    }

    private fun handleResult(result: FocusModeResult) {
        if (result is FocusModeResult.Error) {
            android.util.Log.w(TAG, "Modo Enfoque: ${result.message}")
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
