package com.fluilet.app.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fluilet.app.domain.model.SessionState
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.presentation.auth.LoginScreen
import com.fluilet.app.presentation.auth.RegisterScreen
import com.fluilet.app.presentation.child.ChildFormScreen
import com.fluilet.app.presentation.common.FullScreenLoading
import com.fluilet.app.presentation.home.HomeScreen
import com.fluilet.app.presentation.reader.ReaderScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val READER = "reader"
    const val CHILD_FORM = "child_form?childId={childId}"

    fun childForm(childId: String? = null) = if (childId == null) "child_form" else "child_form?childId=$childId"

    /** Tutor y adulto independiente entran al panel; la vista de niño (NINO) va directo a la lectura. */
    fun homeFor(role: UserRole) = if (role.isChildView) READER else HOME
}

private const val TRANSITION_MS = 250
private val optionalChildArg = navArgument("childId") {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: AppViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsStateWithLifecycle()

    // La sesión manda: login/logout/expiración del token redirigen sin importar dónde esté el usuario.
    LaunchedEffect(session) {
        val current = navController.currentDestination?.route
        when (val s = session) {
            SessionState.Loading -> Unit
            SessionState.LoggedOut -> if (current != Routes.LOGIN && current != Routes.REGISTER) {
                navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            }
            is SessionState.LoggedIn -> if (current == null || current == Routes.SPLASH || current == Routes.LOGIN || current == Routes.REGISTER) {
                navController.navigate(Routes.homeFor(s.role)) { popUpTo(0) { inclusive = true } }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(TRANSITION_MS)) },
        exitTransition = { fadeOut(tween(TRANSITION_MS)) },
        popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
        popExitTransition = { fadeOut(tween(TRANSITION_MS)) }
    ) {
        composable(Routes.SPLASH) { FullScreenLoading() }

        composable(Routes.LOGIN) {
            LoginScreen(onRegister = { navController.navigate(Routes.REGISTER) })
        }
        composable(Routes.REGISTER) {
            RegisterScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNewChild = { navController.navigate(Routes.childForm()) },
                onEditChild = { navController.navigate(Routes.childForm(it)) },
                onStartReading = { navController.navigate(Routes.READER) }
            )
        }
        composable(Routes.CHILD_FORM, arguments = listOf(optionalChildArg)) {
            ChildFormScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.READER) {
            ReaderScreen(
                onExit = {
                    val role = (session as? SessionState.LoggedIn)?.role
                    if (role == UserRole.NINO) {
                        // La vista de niño no tiene panel al que volver: tras el Muro Parental se cierra la sesión.
                        viewModel.logout()
                    } else if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                        navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                    }
                }
            )
        }
    }
}
