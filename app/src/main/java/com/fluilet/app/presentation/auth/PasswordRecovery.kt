package com.fluilet.app.presentation.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fluilet.app.R
import com.fluilet.app.domain.validation.PasswordRequirement
import com.fluilet.app.domain.validation.ResetCode
import com.fluilet.app.presentation.brand.FluiletBrand
import com.fluilet.app.presentation.common.CelebrationBurst
import com.fluilet.app.presentation.common.ErrorMessage
import com.fluilet.app.presentation.common.PrimaryButton
import com.fluilet.app.presentation.common.SpringProgressBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pausa en la pantalla de éxito antes de volver sola a Iniciar Sesión. */
private const val RETURN_TO_LOGIN_MS = 2_800L

/**
 * "¿Olvidaste tu contraseña?" en 3 pasos con transiciones (correo → código de 6 dígitos → contraseña nueva) y la
 * celebración final. [onExit] vuelve a Iniciar Sesión sin cambios; [onFinished] vuelve con el correo listo y el
 * mensaje de confirmación del servidor.
 */
@Composable
internal fun PasswordRecoveryFlow(
    state: PasswordRecoveryUiState,
    viewModel: PasswordRecoveryViewModel,
    onExit: () -> Unit,
    onFinished: (email: String, message: String?) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state.step != RecoveryStep.DONE) StepIndicator(state.step)
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(340)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(340)))
                    .togetherWith(slideOutHorizontally(tween(240)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
                    .using(SizeTransform(clip = false))
            },
            label = "recoveryStep"
        ) { step ->
            when (step) {
                RecoveryStep.EMAIL -> EmailStep(state, viewModel, onExit)
                RecoveryStep.CODE -> CodeStep(state, viewModel)
                RecoveryStep.NEW_PASSWORD -> NewPasswordStep(state, viewModel)
                RecoveryStep.DONE -> DoneStep(state, onFinished)
            }
        }
    }
}

/** "Paso 2 de 3" con tres píldoras: la activa se alarga con un resorte. */
@Composable
private fun StepIndicator(step: RecoveryStep) {
    val current = step.ordinal + 1
    val label = stringResource(R.string.recovery_step_of, current)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(3) { index ->
            val active = index + 1 == current
            val done = index + 1 < current
            val width by animateDpAsState(if (active) 28.dp else 10.dp, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "stepWidth")
            val color by animateColorAsState(
                if (active || done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                tween(300), label = "stepColor"
            )
            Box(Modifier.width(width).height(10.dp).clip(RoundedCornerShape(50)).background(color))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- Paso 1 -------------------------------------------------------------------------------------------

@Composable
private fun EmailStep(state: PasswordRecoveryUiState, viewModel: PasswordRecoveryViewModel, onExit: () -> Unit) {
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepTitle(stringResource(R.string.recovery_email_title), stringResource(R.string.recovery_email_body))
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmail,
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            enabled = !state.isLoading,
            isError = state.emailHint != null,
            supportingText = state.emailHint?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { viewModel.sendCode() }),
            modifier = Modifier.fillMaxWidth()
        )
        ErrorMessage(state.errorMessage)
        PrimaryButton(
            stringResource(R.string.recovery_send_code),
            viewModel::sendCode,
            enabled = state.canSendCode,
            loading = state.isLoading,
            icon = Icons.Filled.MarkEmailRead
        )
        TextButton(onClick = onExit, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.recovery_back_to_login), style = MaterialTheme.typography.labelLarge)
        }
    }
}

// --- Paso 2 -------------------------------------------------------------------------------------------

@Composable
private fun CodeStep(state: PasswordRecoveryUiState, viewModel: PasswordRecoveryViewModel) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepTitle(stringResource(R.string.recovery_code_title), stringResource(R.string.recovery_code_body, state.email.trim()))
        state.infoMessage?.let { InfoNote(it) }
        ResetCodeBoxes(
            code = state.code,
            rejections = state.codeRejections,
            enabled = !state.isLoading,
            onChange = viewModel::onCode,
            onDone = viewModel::verifyCode
        )
        ErrorMessage(state.errorMessage)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    scope.launch {
                        val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.coerceToText(context)?.toString()
                        viewModel.pasteCode(text)
                    }
                },
                enabled = !state.isLoading
            ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.recovery_paste), style = MaterialTheme.typography.labelLarge)
            }
            ResendButton(state.resendSeconds, state.canResend, viewModel::resendCode)
        }
        PrimaryButton(stringResource(R.string.recovery_verify), viewModel::verifyCode, enabled = state.canVerify, loading = state.isLoading)
        TextButton(onClick = { viewModel.back() }, enabled = !state.isLoading, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.recovery_change_email), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Aviso suave con el mensaje del servidor ("Si el correo está registrado, recibirás un código..."). */
@Composable
private fun InfoNote(message: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(FluiletBrand.Mint.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Filled.MarkEmailRead, contentDescription = null, tint = FluiletBrand.Mint, modifier = Modifier.size(20.dp))
        Text(message, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Las 6 casillas del código. Un único campo de texto invisible ocupa toda la fila: así funcionan el teclado numérico,
 * el borrado, la sugerencia del portapapeles del teclado y el "Pegar" de una pulsación larga. Las casillas solo
 * dibujan el estado (dígito que entra con un rebote, casilla activa con cursor, temblor al rechazar el código).
 */
@Composable
internal fun ResetCodeBoxes(
    code: String,
    rejections: Int,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(rejections) {
        if (rejections == 0) return@LaunchedEffect
        for (offset in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(offset, tween(55))
        focusRequester.requestFocus()
    }
    val description = stringResource(R.string.recovery_code_boxes)
    val progress = stringResource(R.string.recovery_code_progress, code.length)
    val hidden = TextSelectionColors(handleColor = Color.Transparent, backgroundColor = Color.Transparent)
    CompositionLocalProvider(LocalTextSelectionColors provides hidden) {
        BasicTextField(
            value = code,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = modifier.fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .graphicsLayer { translationX = shake.value.dp.toPx() }
                .semantics {
                    contentDescription = description
                    stateDescription = progress
                },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.widthIn(max = 380.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val active = code.length.coerceAtMost(ResetCode.LENGTH - 1)
                        repeat(ResetCode.LENGTH) { index ->
                            CodeBox(
                                digit = code.getOrNull(index),
                                active = focused && enabled && index == active,
                                error = rejections > 0 && code.isEmpty(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Box(Modifier.matchParentSize()) { inner() }
                }
            }
        )
    }
}

@Composable
private fun CodeBox(digit: Char?, active: Boolean, error: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(
        when {
            error -> scheme.error
            active -> scheme.primary
            digit != null -> scheme.primary.copy(alpha = 0.55f)
            else -> scheme.outlineVariant
        },
        tween(220), label = "codeBorder"
    )
    val borderWidth by animateDpAsState(if (active) 2.5.dp else 1.5.dp, tween(180), label = "codeBorderWidth")
    val fill by animateColorAsState(if (digit != null) scheme.primary.copy(alpha = 0.10f) else scheme.surface, tween(220), label = "codeFill")
    val scale by animateFloatAsState(if (active) 1.06f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "codeScale")
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.aspectRatio(0.8f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(fill).border(borderWidth, borderColor, shape),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = digit,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.4f) + fadeIn(tween(120)))
                    .togetherWith(fadeOut(tween(90)))
            },
            label = "codeDigit"
        ) { value ->
            Text(
                value?.toString() ?: "",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
        if (active && digit == null) BlinkingCaret()
    }
}

@Composable
private fun BlinkingCaret() {
    val blink = rememberInfiniteTransition(label = "caret")
    val alpha by blink.animateFloat(1f, 0f, infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse), label = "caretAlpha")
    Box(Modifier.width(2.dp).height(26.dp).graphicsLayer { this.alpha = alpha }.background(MaterialTheme.colorScheme.primary))
}

/** "Reenviar código" con un anillo que se vacía durante el minuto de espera. */
@Composable
private fun ResendButton(seconds: Int, enabled: Boolean, onResend: () -> Unit) {
    val fraction by animateFloatAsState(
        seconds / PasswordRecoveryViewModel.RESEND_COOLDOWN_SECONDS.toFloat(),
        tween(1_000, easing = LinearEasing), label = "resendCooldown"
    )
    TextButton(onClick = onResend, enabled = enabled) {
        if (seconds > 0) {
            CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(18.dp), strokeWidth = 2.5.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.recovery_resend_in, "0:%02d".format(seconds)),
                style = MaterialTheme.typography.labelLarge
            )
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.recovery_resend), style = MaterialTheme.typography.labelLarge)
        }
    }
}

// --- Paso 3 -------------------------------------------------------------------------------------------

@Composable
private fun NewPasswordStep(state: PasswordRecoveryUiState, viewModel: PasswordRecoveryViewModel) {
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepTitle(stringResource(R.string.recovery_password_title), stringResource(R.string.recovery_password_body))
        PasswordField(
            value = state.newPassword,
            onChange = viewModel::onNewPassword,
            label = stringResource(R.string.recovery_new_password),
            imeAction = ImeAction.Next,
            onDone = {},
            isError = state.passwordHint != null && state.strength == 1f,
            supportingText = state.passwordHint.takeIf { state.strength == 1f }
        )
        PasswordField(
            value = state.confirmPassword,
            onChange = viewModel::onConfirmPassword,
            label = stringResource(R.string.recovery_confirm_password),
            imeAction = ImeAction.Done,
            onDone = viewModel::resetPassword,
            isError = state.confirmHint != null,
            supportingText = state.confirmHint
        )
        RequirementsCard(state)
        ErrorMessage(state.errorMessage)
        PrimaryButton(stringResource(R.string.recovery_save), viewModel::resetPassword, enabled = state.canReset, loading = state.isLoading)
    }
}

/** Lista viva de requisitos con una barra de seguridad que se llena con resorte y cambia de color. */
@Composable
private fun RequirementsCard(state: PasswordRecoveryUiState) {
    val met = state.requirements.count { it.second }
    val total = state.requirements.size
    val barColor by animateColorAsState(
        when {
            state.strength >= 1f -> FluiletBrand.Mint
            state.strength >= 0.5f -> FluiletBrand.Orange
            else -> MaterialTheme.colorScheme.error
        },
        tween(300), label = "strengthColor"
    )
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.recovery_requirements), style = MaterialTheme.typography.titleSmall)
            val strengthLabel = stringResource(R.string.recovery_strength, met, total)
            SpringProgressBar(
                fraction = state.strength,
                height = 8.dp,
                brush = SolidColor(barColor),
                modifier = Modifier.semantics { contentDescription = strengthLabel }
            )
            state.requirements.forEach { (requirement, ok) -> RequirementRow(stringResource(requirement.label()), ok) }
            RequirementRow(stringResource(R.string.recovery_req_match), state.passwordsMatch)
        }
    }
}

private fun PasswordRequirement.label(): Int = when (this) {
    PasswordRequirement.LENGTH -> R.string.recovery_req_length
    PasswordRequirement.UPPERCASE -> R.string.recovery_req_upper
    PasswordRequirement.LOWERCASE -> R.string.recovery_req_lower
    PasswordRequirement.DIGIT -> R.string.recovery_req_digit
    PasswordRequirement.SYMBOL -> R.string.recovery_req_symbol
    PasswordRequirement.NO_SPACES -> R.string.recovery_req_spaces
}

@Composable
private fun RequirementRow(label: String, met: Boolean) {
    val color by animateColorAsState(if (met) FluiletBrand.Mint else MaterialTheme.colorScheme.onSurfaceVariant, tween(250), label = "requirement")
    val description = stringResource(if (met) R.string.recovery_req_met else R.string.recovery_req_pending, label)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedContent(
            targetState = met,
            transitionSpec = { scaleIn(spring(dampingRatio = 0.45f), initialScale = 0.3f).togetherWith(fadeOut(tween(100))) },
            label = "requirementIcon"
        ) { ok ->
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (met) MaterialTheme.colorScheme.onSurface else color)
    }
}

// --- Listo --------------------------------------------------------------------------------------------

/** Contraseña cambiada: confeti y estrellas, y vuelta sola a Iniciar Sesión con el correo listo. */
@Composable
private fun DoneStep(state: PasswordRecoveryUiState, onFinished: (String, String?) -> Unit) {
    LaunchedEffect(state.celebration) {
        delay(RETURN_TO_LOGIN_MS)
        onFinished(state.email, state.successMessage)
    }
    Box {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = FluiletBrand.Mint.copy(alpha = 0.16f))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FluiletBrand.Mint, modifier = Modifier.size(56.dp))
                Text(stringResource(R.string.recovery_done_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                state.successMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
                Text(
                    stringResource(R.string.recovery_done_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                PrimaryButton(stringResource(R.string.recovery_go_login), { onFinished(state.email, state.successMessage) })
            }
        }
        CelebrationBurst(trigger = state.celebration, modifier = Modifier.matchParentSize(), originY = 0.3f)
    }
}

/** Confirmación verde en Iniciar Sesión tras restablecer la contraseña. */
@Composable
internal fun RecoveredNotice(message: String?) {
    // Conserva el último texto mientras la tarjeta se pliega al desaparecer.
    var shown by remember { mutableStateOf(message) }
    if (message != null) shown = message
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(FluiletBrand.Mint.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FluiletBrand.Mint, modifier = Modifier.size(22.dp))
            Text(shown.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
