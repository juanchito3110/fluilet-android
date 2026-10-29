package com.fluilet.app.presentation.auth

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.fluilet.app.presentation.common.CelebrationBurst
import com.fluilet.app.domain.gamification.Accessory
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.presentation.brand.FluiletBrand
import com.fluilet.app.presentation.fluibot.FluiBotCanvas
import com.fluilet.app.presentation.fluibot.FluiBotMood
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.fluilet.app.presentation.common.SelectableChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.fluilet.app.presentation.brand.FluiletFullLogo
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.R
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.presentation.common.ErrorMessage
import com.fluilet.app.presentation.common.PrimaryButton

@Composable
internal fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onDone: () -> Unit,
    isError: Boolean = false,
    supportingText: String? = null
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (visible) R.string.auth_hide_password else R.string.auth_show_password)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/** Pestañas de la pantalla de acceso. */
enum class AuthTab { LOGIN, REGISTER }

/**
 * Iniciar Sesión / Crear Cuenta, como en cualquier app de Google Play: una cabecera con FluiBot dando la bienvenida
 * (piensa mientras se verifica la cuenta), el logo de Fluilet, pestañas y el formulario estándar con transiciones
 * animadas entre ambos. No hay atajos ni credenciales precargadas: las cuentas de la demostración se escriben a mano
 * igual que cualquier otra. "¿Olvidaste tu contraseña?" abre la recuperación en 3 pasos en esta misma pantalla, con
 * FluiBot acompañando cada paso. [onClose] solo existe al abrirla desde "✨ Ver Intro / Cuenta" (vista previa sin
 * cerrar sesión).
 */
@Composable
fun AuthScreen(
    initialTab: AuthTab = AuthTab.LOGIN,
    onClose: (() -> Unit)? = null,
    loginViewModel: LoginViewModel = hiltViewModel(),
    registerViewModel: RegisterViewModel = hiltViewModel(),
    recoveryViewModel: PasswordRecoveryViewModel = hiltViewModel()
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var recovering by rememberSaveable { mutableStateOf(false) }
    val loginState by loginViewModel.state.collectAsStateWithLifecycle()
    val registerState by registerViewModel.state.collectAsStateWithLifecycle()
    val recoveryState by recoveryViewModel.state.collectAsStateWithLifecycle()
    val hero = when {
        recovering -> recoveryHero(recoveryState)
        (if (tab == AuthTab.LOGIN) loginState.isLoading else registerState.isLoading) ->
            HeroContent(FluiBotMood.THINKING, R.string.auth_hero_title, R.string.auth_hero_loading)
        else -> HeroContent(FluiBotMood.HAPPY, R.string.auth_hero_title, R.string.auth_hero_subtitle)
    }
    val finishRecovery: (String, String?) -> Unit = { email, message ->
        loginViewModel.onPasswordRecovered(email, message)
        tab = AuthTab.LOGIN
        recovering = false
    }

    // Atrás dentro de la recuperación retrocede un paso; desde el primero vuelve a Iniciar Sesión.
    BackHandler(enabled = recovering) {
        if (!recoveryViewModel.back()) recovering = false
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()) {
            WelcomeHeader(hero, celebration = if (recovering) recoveryState.celebration else 0, onClose = onClose)
            AnimatedContent(
                targetState = recovering,
                transitionSpec = {
                    (fadeIn(tween(320)) + slideInHorizontally(tween(320)) { if (targetState) it / 4 else -it / 4 })
                        .togetherWith(fadeOut(tween(180)))
                        .using(SizeTransform(clip = false))
                },
                label = "authMode"
            ) { inRecovery ->
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (inRecovery) {
                        PasswordRecoveryFlow(
                            state = recoveryState,
                            viewModel = recoveryViewModel,
                            onExit = { recovering = false },
                            onFinished = finishRecovery
                        )
                    } else {
                        AuthForms(
                            tab = tab,
                            onTab = { tab = it },
                            loginViewModel = loginViewModel,
                            registerViewModel = registerViewModel,
                            onForgotPassword = {
                                recoveryViewModel.start(loginState.email)
                                recovering = true
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Logo, pestañas y el formulario de la pestaña elegida. */
@Composable
private fun AuthForms(
    tab: AuthTab,
    onTab: (AuthTab) -> Unit,
    loginViewModel: LoginViewModel,
    registerViewModel: RegisterViewModel,
    onForgotPassword: () -> Unit
) {
    FluiletFullLogo(Modifier.fillMaxWidth())
    Text(
        stringResource(R.string.auth_login_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
        Tab(selected = tab == AuthTab.LOGIN, onClick = { onTab(AuthTab.LOGIN) }, text = { Text(stringResource(R.string.auth_tab_login), style = MaterialTheme.typography.titleSmall) })
        Tab(selected = tab == AuthTab.REGISTER, onClick = { onTab(AuthTab.REGISTER) }, text = { Text(stringResource(R.string.auth_tab_register), style = MaterialTheme.typography.titleSmall) })
    }
    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            (slideInHorizontally(tween(320)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(320)))
                .togetherWith(slideOutHorizontally(tween(240)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
                .using(SizeTransform(clip = false))
        },
        label = "authForm"
    ) { current ->
        when (current) {
            AuthTab.LOGIN -> LoginForm(loginViewModel, onForgotPassword)
            AuthTab.REGISTER -> RegisterForm(registerViewModel)
        }
    }
}

/** Lo que muestra la cabecera: el gesto de FluiBot, el título y la frase de apoyo. */
private data class HeroContent(val mood: FluiBotMood, @StringRes val title: Int, @StringRes val subtitle: Int)

private fun recoveryHero(state: PasswordRecoveryUiState): HeroContent = when {
    state.step == RecoveryStep.DONE -> HeroContent(state.mood, R.string.recovery_hero_done, R.string.recovery_hero_celebrate)
    state.isLoading -> HeroContent(
        state.mood,
        R.string.recovery_hero_title,
        when (state.step) {
            RecoveryStep.CODE -> R.string.recovery_hero_verifying
            RecoveryStep.NEW_PASSWORD -> R.string.recovery_hero_saving
            else -> R.string.recovery_hero_sending
        }
    )
    else -> HeroContent(
        state.mood,
        R.string.recovery_hero_title,
        when (state.step) {
            RecoveryStep.CODE -> R.string.recovery_hero_code
            RecoveryStep.NEW_PASSWORD -> R.string.recovery_hero_password
            else -> R.string.recovery_hero_email
        }
    )
}

/**
 * Cabecera con degradado y FluiBot que entra con un rebote, saluda, piensa mientras se trabaja y celebra (salta y
 * suelta estrellas, con confeti encima) cuando [celebration] sube. Salta al tocarlo.
 */
@Composable
private fun WelcomeHeader(hero: HeroContent, celebration: Int, onClose: (() -> Unit)?) {
    val appeared = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(Brush.verticalGradient(listOf(FluiletBrand.Purple, FluiletBrand.SloganPurple, FluiletBrand.Lavender)))
            .statusBarsPadding()
            .padding(bottom = 20.dp)
    ) {
        if (onClose != null) {
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
                Text(stringResource(R.string.auth_back_to_panel), color = Color.White)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = if (onClose != null) 40.dp else 12.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AnimatedVisibility(
                visibleState = appeared,
                enter = fadeIn(tween(500)) + scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow), initialScale = 0.5f)
            ) {
                FluiBotCanvas(
                    stage = FluiBotStage.GUARDIAN,
                    equipped = setOf(Accessory.EMBLEMA_ESTELAR.id),
                    mood = hero.mood,
                    celebrationTick = celebration,
                    modifier = Modifier.size(176.dp)
                )
            }
            AnimatedContent(targetState = hero.title, transitionSpec = { fadeIn(tween(250)).togetherWith(fadeOut(tween(150))) }, label = "heroTitle") { title ->
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
            }
            AnimatedContent(targetState = hero.subtitle, transitionSpec = { fadeIn(tween(250)).togetherWith(fadeOut(tween(150))) }, label = "heroLine") { line ->
                Text(
                    stringResource(line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        // Confeti sobre la cabecera al restablecer la contraseña (no intercepta toques).
        CelebrationBurst(trigger = celebration, modifier = Modifier.matchParentSize(), originY = 0.4f)
    }
}

@Composable
private fun LoginForm(viewModel: LoginViewModel, onForgotPassword: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RecoveredNotice(state.notice)
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmail,
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(state.password, viewModel::onPassword, stringResource(R.string.auth_password), ImeAction.Done, viewModel::submit)
        ErrorMessage(state.errorMessage)
        PrimaryButton(stringResource(R.string.auth_login), viewModel::submit, enabled = state.canSubmit, loading = state.isLoading)
        TextButton(
            onClick = onForgotPassword,
            enabled = !state.isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.auth_forgot_password), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RegisterForm(viewModel: RegisterViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.auth_register_subtitle), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.fullName,
            onValueChange = viewModel::onName,
            label = { Text(stringResource(R.string.auth_full_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmail,
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(
            value = state.password,
            onChange = viewModel::onPassword,
            label = stringResource(R.string.auth_password),
            imeAction = ImeAction.Done,
            onDone = viewModel::submit,
            isError = state.passwordHint != null,
            supportingText = state.passwordHint ?: stringResource(R.string.auth_password_rules)
        )

        Text(stringResource(R.string.auth_role_label), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RoleChip(UserRole.TUTOR, R.string.auth_role_tutor, state.role, viewModel::onRole)
            RoleChip(UserRole.ADULTO_INDEPENDIENTE, R.string.auth_role_adult, state.role, viewModel::onRole)
        }
        if (state.needsPin) {
            OutlinedTextField(
                value = state.pin,
                onValueChange = viewModel::onPin,
                label = { Text(stringResource(R.string.auth_pin)) },
                singleLine = true,
                isError = state.pinHint != null,
                supportingText = { Text(state.pinHint ?: stringResource(R.string.auth_pin_rules)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
        }
        ErrorMessage(state.errorMessage)
        PrimaryButton(stringResource(R.string.auth_register), viewModel::submit, enabled = state.canSubmit, loading = state.isLoading)
    }
}

@Composable
private fun RoleChip(role: UserRole, label: Int, selected: UserRole, onSelect: (UserRole) -> Unit) {
    SelectableChip(
        selected = role == selected,
        onClick = { onSelect(role) },
        label = { Text(stringResource(label), style = MaterialTheme.typography.bodyMedium) },
        modifier = Modifier.fillMaxWidth()
    )
}
