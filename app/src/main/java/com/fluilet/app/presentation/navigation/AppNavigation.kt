package com.fluilet.app.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.fluilet.app.presentation.admin.MasterAdminScreen
import com.fluilet.app.presentation.auth.AuthScreen
import com.fluilet.app.presentation.brand.FluiletSplashScreen
import com.fluilet.app.presentation.child.ChildFormScreen
import com.fluilet.app.presentation.home.HomeScreen
import com.fluilet.app.presentation.reader.ReaderScreen

object Routes {
    const val SPLASH = "splash"
    /** Iniciar Sesión / Crear Cuenta (pestañas). */
    const val AUTH = "auth"

    /** Repetir la intro y la pantalla de acceso desde el panel, sin cerrar sesión (demostración en vivo). */
    const val INTRO_PREVIEW = "intro_preview"
    const val AUTH_PREVIEW = "auth_preview"
    const val HOME = "home"

    /** Sistema de Control Maestro (`admin@fluilet.com`). */
    const val ADMIN = "admin"
    const val READER = "reader"
    const val CHILD_FORM = "child_form?childId={childId}"

    fun childForm(childId: String? = null) = if (childId == null) "child_form" else "child_form?childId=$childId"

    /** Tutor y adulto independiente entran al panel; la vista de niño (NINO) va directo a la lectura. */
    fun homeFor(role: UserRole) = when {
        role.isMasterAdmin -> ADMIN
        role.isChildView -> READER
        else -> HOME
    }

    /**
     * A dónde redirigir según la sesión (`null` = quedarse). La intro siempre se ve primero; las vistas previas
     * "Ver Intro / Cuenta" no se tocan salvo que se inicie otra sesión desde ellas.
     */
    fun redirectFor(current: String?, splashDone: Boolean, session: SessionState): String? {
        if (current == SPLASH && !splashDone) return null
        return when (session) {
            SessionState.Loading -> null
            SessionState.LoggedOut ->
                if (current != AUTH && current != AUTH_PREVIEW && current != INTRO_PREVIEW) AUTH else null
            is SessionState.LoggedIn ->
                if (current == null || current == SPLASH || current == AUTH || current == AUTH_PREVIEW) homeFor(session.role) else null
        }
    }
}

private const val TRANSITION_MS = 300
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

    // La intro animada se muestra siempre primero; al terminar, la sesión decide a dónde ir.
    var splashDone by rememberSaveable { mutableStateOf(false) }

    // La sesión manda: login/logout/expiración del token redirigen sin importar dónde esté el usuario.
    LaunchedEffect(session, splashDone) {
        Routes.redirectFor(navController.currentDestination?.route, splashDone, session)?.let { target ->
            navController.navigate(target) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        // Fundido con un leve deslizamiento horizontal (hacia la izquierda al avanzar y a la derecha al volver).
        enterTransition = { fadeIn(tween(TRANSITION_MS)) + slideInHorizontally(tween(TRANSITION_MS)) { it / 8 } },
        exitTransition = { fadeOut(tween(TRANSITION_MS)) + slideOutHorizontally(tween(TRANSITION_MS)) { -it / 8 } },
        popEnterTransition = { fadeIn(tween(TRANSITION_MS)) + slideInHorizontally(tween(TRANSITION_MS)) { -it / 8 } },
        popExitTransition = { fadeOut(tween(TRANSITION_MS)) + slideOutHorizontally(tween(TRANSITION_MS)) { it / 8 } }
    ) {
        composable(Routes.SPLASH) { FluiletSplashScreen(onFinished = { splashDone = true }) }
        composable(Routes.AUTH) { AuthScreen() }

        composable(Routes.INTRO_PREVIEW) {
            FluiletSplashScreen(onFinished = {
                navController.navigate(Routes.AUTH_PREVIEW) { popUpTo(Routes.INTRO_PREVIEW) { inclusive = true } }
            })
        }
        composable(Routes.AUTH_PREVIEW) {
            AuthScreen(onClose = {
                if (!navController.popBackStack(Routes.HOME, inclusive = false)) navController.popBackStack()
            })
        }

        composable(Routes.ADMIN) { MasterAdminScreen() }

        composable(Routes.HOME) {
            HomeScreen(
                onNewChild = { navController.navigate(Routes.childForm()) },
                onEditChild = { navController.navigate(Routes.childForm(it)) },
                onStartReading = { navController.navigate(Routes.READER) },
                onShowIntro = { navController.navigate(Routes.INTRO_PREVIEW) }
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
