package com.fluilet.app.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.presentation.auth.LoginScreen
import com.fluilet.app.presentation.reader.ReaderScreen
import com.fluilet.app.presentation.theme.FluiletTheme
import com.fluilet.app.presentation.tutor.TutorDashboardScreen

sealed class FluiletDestination(val route: String) {
    data object Login : FluiletDestination("login")
    data object TutorDashboard : FluiletDestination("tutor_dashboard")
    data object Reader : FluiletDestination("reader")
}

private const val TRANSITION_DURATION_MS = 350

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    onToggleFocusModeHardware: (Boolean) -> Unit = {}
) {
    // Tema base para Login/Tutor. ReaderScreen aplica su propio FluiletTheme anidado
    // con la paleta dinámica del usuario — Compose respeta el tema más interno.
    FluiletTheme(palette = AccessiblePalette.ESTANDAR) {
        NavHost(
            navController = navController,
            startDestination = FluiletDestination.Login.route,
            enterTransition = { fadeIn(animationSpec = tween(TRANSITION_DURATION_MS)) },
            exitTransition = { fadeOut(animationSpec = tween(TRANSITION_DURATION_MS)) },
            popEnterTransition = { fadeIn(animationSpec = tween(TRANSITION_DURATION_MS)) },
            popExitTransition = { fadeOut(animationSpec = tween(TRANSITION_DURATION_MS)) }
        ) {
            composable(FluiletDestination.Login.route) {
                LoginScreen(
                    onLoginSuccess = { role ->
                        val destination = if (role == UserRole.TUTOR) {
                            FluiletDestination.TutorDashboard.route
                        } else {
                            FluiletDestination.Reader.route
                        }
                        navController.navigate(destination) {
                            popUpTo(FluiletDestination.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(FluiletDestination.TutorDashboard.route) {
                // TODO: reemplazar por el childId real cuando exista selector de perfil / backend.
                TutorDashboardScreen(childId = "demo-child-id")
            }

            composable(FluiletDestination.Reader.route) {
                ReaderScreen(onToggleFocusModeHardware = onToggleFocusModeHardware)
            }
        }
    }
}