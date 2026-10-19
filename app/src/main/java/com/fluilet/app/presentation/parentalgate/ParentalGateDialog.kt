package com.fluilet.app.presentation.parentalgate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.R
import com.fluilet.app.domain.model.PinVerification
import com.fluilet.app.presentation.common.ErrorMessage

/**
 * Muro Parental. [GateMode.VERIFY] valida el PIN del tutor y entrega una [PinVerification]
 * (necesaria para salir del Modo Enfoque); [GateMode.SETUP] crea el PIN; [GateMode.CHANGE] lo
 * cambia. Desde cualquier modo con PIN existente se puede restablecer reautenticando la contraseña
 * del tutor contra el backend.
 */
@Composable
fun ParentalGateDialog(
    mode: GateMode,
    onSuccess: (PinVerification?) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ParentalGateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(mode) { viewModel.open(mode) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GateEvent.Success -> onSuccess(event.proof)
            }
        }
    }

    val isReset = state.step == GateStep.RESET_PASSWORD
    val title = when {
        isReset -> R.string.gate_reset_title
        mode == GateMode.SETUP -> R.string.gate_setup_title
        mode == GateMode.CHANGE -> R.string.gate_change_title
        else -> R.string.gate_verify_title
    }
    val body = when {
        isReset -> R.string.gate_reset_body
        mode == GateMode.SETUP -> R.string.gate_setup_body
        mode == GateMode.CHANGE -> R.string.gate_change_body
        else -> R.string.gate_verify_body
    }
    val confirmLabel = when {
        isReset || mode != GateMode.VERIFY -> R.string.gate_save
        else -> R.string.gate_unlock
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)

                if (isReset) {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPassword,
                        label = { Text(stringResource(R.string.gate_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!isReset && mode != GateMode.SETUP) {
                    PinField(R.string.gate_current_pin, state.pin, viewModel::onPin, enabled = !state.isLocked,
                        imeAction = if (mode == GateMode.VERIFY) ImeAction.Done else ImeAction.Next, onDone = viewModel::submit)
                }
                if (isReset || mode != GateMode.VERIFY) {
                    PinField(R.string.gate_new_pin, state.newPin, viewModel::onNewPin, imeAction = ImeAction.Next, onDone = viewModel::submit)
                    PinField(R.string.gate_confirm_pin, state.confirmation, viewModel::onConfirmation, imeAction = ImeAction.Done, onDone = viewModel::submit)
                }

                ErrorMessage(state.errorMessage)
                if (state.isLocked) {
                    Text(stringResource(R.string.gate_locked_seconds, state.lockedSeconds), style = MaterialTheme.typography.labelLarge)
                }
                if (state.isBusy) CircularProgressIndicator()

                if (!isReset && mode != GateMode.SETUP) {
                    TextButton(onClick = viewModel::showReset) { Text(stringResource(R.string.gate_forgot_pin)) }
                } else if (isReset) {
                    TextButton(onClick = viewModel::backFromReset) { Text(stringResource(R.string.gate_back)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::submit, enabled = !state.isBusy && !state.isLocked) {
                Text(stringResource(confirmLabel))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun PinField(
    label: Int,
    value: String,
    onChange: (String) -> Unit,
    imeAction: ImeAction,
    onDone: () -> Unit,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth()
    )
}
