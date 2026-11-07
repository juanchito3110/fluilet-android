package com.fluilet.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fluilet.app.R

/** Secciones del aviso, en orden: título y cuerpo (recursos de texto). */
private val PrivacySections = listOf(
    R.string.privacy_collect_title to R.string.privacy_collect_body,
    R.string.privacy_use_title to R.string.privacy_use_body,
    R.string.privacy_share_title to R.string.privacy_share_body,
    R.string.privacy_protect_title to R.string.privacy_protect_body,
    R.string.privacy_rights_title to R.string.privacy_rights_body
)

/**
 * Ventana "Aviso de Privacidad y Uso de Datos": qué guarda Fluilet, para qué, con quién se comparte (con nadie), cómo
 * se protege y cómo eliminar la cuenta. Se desplaza si el teléfono es pequeño o la letra grande.
 */
@Composable
fun PrivacyNoticeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.PrivacyTip, contentDescription = null) },
        title = { DialogTitle(stringResource(R.string.privacy_title)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyMedium)
                PrivacySections.forEach { (title, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AdaptiveText(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { DialogTextButton(stringResource(R.string.privacy_close), onDismiss) }
    )
}

/** Enlace "🛡️ Aviso de Privacidad y Uso de Datos" que abre la ventana del aviso (Login y Registro). */
@Composable
fun PrivacyNoticeLink(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DialogTextButton(stringResource(R.string.privacy_link), { open = true })
    }
    if (open) PrivacyNoticeDialog(onDismiss = { open = false })
}
