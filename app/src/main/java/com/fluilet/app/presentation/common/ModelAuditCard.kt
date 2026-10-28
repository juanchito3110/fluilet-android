package com.fluilet.app.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fluilet.app.R
import com.fluilet.app.domain.model.ModelAudit

/**
 * 🧠 Auditoría del Modelo IA Reentrenado (Panel Maestro y 📊 Estadísticas): criterios académicos, dataset, exactitud
 * léxica, SARI y cumplimiento de las 5 bandas, tal como los publica `GET /adaptations/model-info`.
 */
@Composable
fun ModelAuditCard(audit: ModelAudit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.audit_title), style = MaterialTheme.typography.titleMedium)
            Text(audit.versionLine, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (!audit.active) Text(stringResource(R.string.audit_inactive), style = MaterialTheme.typography.bodyMedium)
            AuditRow(
                stringResource(R.string.audit_criteria_label),
                stringResource(if (audit.acceptanceCriteriaMet) R.string.audit_criteria_ok else R.string.audit_criteria_pending)
            )
            AuditRow(stringResource(R.string.audit_dataset_label), stringResource(R.string.audit_dataset_value, audit.datasetPairs, pluralStringResource(R.plurals.audit_epochs, audit.epochs, audit.epochs)))
            AuditRow(
                stringResource(R.string.audit_lexical_label),
                stringResource(R.string.audit_lexical_value, audit.lexicalAccuracyText, audit.baselineLexicalAccuracyText)
            )
            AuditRow(
                stringResource(R.string.audit_sari_label),
                stringResource(R.string.audit_sari_value, audit.retrainedSariText, audit.baselineSariText)
            )
            if (audit.cohorts.isNotEmpty()) AuditRow(stringResource(R.string.audit_bands_label), audit.bandsSummary)
            Text(
                stringResource(if (audit.fromServer) R.string.audit_source_server else R.string.audit_source_fallback),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AuditRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}
