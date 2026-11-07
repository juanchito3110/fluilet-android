package com.fluilet.app.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.fluilet.app.R
import com.fluilet.app.presentation.common.AdaptiveText
import com.fluilet.app.presentation.common.ButtonLabel
import com.fluilet.app.presentation.common.DialogTextButton
import com.fluilet.app.presentation.common.DialogTitle
import com.fluilet.app.presentation.common.ErrorMessage
import com.fluilet.app.presentation.common.MinTouchHeight
import com.fluilet.app.presentation.common.PrivacyNoticeDialog
import com.fluilet.app.presentation.common.SecondaryButton
import com.fluilet.app.presentation.common.CollapsibleCard

/** Reglas de la confirmación de baja (sin Android, probadas con pruebas unitarias). */
object AccountDeletion {
    /** Palabra que hay que escribir para habilitar "Eliminar definitivamente". */
    const val CONFIRM_WORD = "ELIMINAR"

    /** Se acepta en mayúsculas o minúsculas y con espacios alrededor; nada más. */
    fun confirmed(typed: String): Boolean = typed.trim().equals(CONFIRM_WORD, ignoreCase = true)
}

/**
 * 🛡️ Cuenta, privacidad y eliminación de datos (tutor y adulto independiente): tarjeta desplegable, cerrada por defecto
 * para que Inicio quede corto. Al abrirla muestra el correo de la cuenta, el "Aviso de Privacidad y Uso de Datos" y
 * "Eliminar mi cuenta y datos" con una confirmación escrita, como pide la política de datos de Google Play.
 */
@Composable
internal fun AccountPrivacyCard(state: HomeUiState, onDeleteAccount: () -> Unit, onDismissError: () -> Unit) {
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    CollapsibleCard(
        title = stringResource(R.string.account_title),
        subtitle = stringResource(R.string.account_subtitle),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (state.email.isNotBlank()) {
            AdaptiveText(stringResource(R.string.account_email, state.email), style = MaterialTheme.typography.titleSmall)
        }
        Text(stringResource(R.string.account_body), style = MaterialTheme.typography.bodyMedium)
        SecondaryButton(stringResource(R.string.privacy_title), { showPrivacy = true }, icon = Icons.Filled.PrivacyTip)
        OutlinedButton(
            onClick = { onDismissError(); confirmDelete = true },
            enabled = !state.deletingAccount,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchHeight),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            if (state.deletingAccount) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                ButtonLabel(stringResource(R.string.account_deleting))
            } else {
                Icon(Icons.Filled.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                ButtonLabel(stringResource(R.string.account_delete))
            }
        }
        ErrorMessage(state.accountError)
    }
    if (showPrivacy) PrivacyNoticeDialog(onDismiss = { showPrivacy = false })
    if (confirmDelete) {
        DeleteAccountDialog(
            isTutor = state.isTutor,
            onConfirm = { confirmDelete = false; onDeleteAccount() },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** Confirmación de la baja: explica qué se borra y exige escribir "ELIMINAR" antes de habilitar el botón. */
@Composable
private fun DeleteAccountDialog(isTutor: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val word = stringResource(R.string.account_delete_word)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { DialogTitle(stringResource(R.string.account_delete_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.account_delete_body), style = MaterialTheme.typography.bodyMedium)
                if (isTutor) Text(stringResource(R.string.account_delete_body_tutor), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(MAX_TYPED) },
                    label = { Text(stringResource(R.string.account_delete_type, word)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            DialogTextButton(
                stringResource(R.string.account_delete_confirm),
                onConfirm,
                enabled = AccountDeletion.confirmed(typed),
                color = if (AccountDeletion.confirmed(typed)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        dismissButton = { DialogTextButton(stringResource(R.string.action_cancel), onDismiss) }
    )
}

private const val MAX_TYPED = 20
