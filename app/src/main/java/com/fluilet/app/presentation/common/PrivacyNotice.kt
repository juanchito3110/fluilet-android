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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/** Secciones del aviso, en orden: título y cuerpo (recursos de texto). */
private val PrivacySections = listOf(
    R.string.privacy_collect_title to R.string.privacy_collect_body,
    R.string.privacy_use_title to R.string.privacy_use_body,
    R.string.privacy_share_title to R.string.privacy_share_body,
    R.string.privacy_protect_title to R.string.privacy_protect_body,
    R.string.privacy_retention_title to R.string.privacy_retention_body,
    R.string.privacy_rights_title to R.string.privacy_rights_body
)

/**
 * Ventana "Aviso de Privacidad y Uso de Datos", igual a la política pública del servidor: qué guarda Fluilet, para qué,
 * qué proveedores técnicos usa (sin venta ni publicidad), cómo se protege, cuánto se conserva (hasta borrar la cuenta,
 * de forma permanente e inmediata) y cómo eliminarla, con los enlaces a las páginas públicas. Se desplaza si el
 * teléfono es pequeño o la letra grande.
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
                PrivacyPublicLinks()
            }
        },
        confirmButton = { DialogTextButton(stringResource(R.string.privacy_close), onDismiss) }
    )
}

/**
 * Páginas públicas del servidor (las mismas que se registran en Google Play): política de privacidad y eliminación de
 * cuenta y datos. Cada dirección se ve completa (baja de renglón si no cabe) y se abre en el navegador al tocarla.
 */
@Composable
fun PrivacyPublicLinks(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AdaptiveText(stringResource(R.string.privacy_links_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        PublicLink(stringResource(R.string.privacy_policy_label), stringResource(R.string.privacy_policy_url))
        PublicLink(stringResource(R.string.privacy_delete_label), stringResource(R.string.privacy_delete_url))
    }
}

@Composable
private fun PublicLink(label: String, url: String) {
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    Text(
        buildAnnotatedString {
            append("$label: ")
            withLink(LinkAnnotation.Url(url, linkStyle)) { append(url) }
        },
        style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph)
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
