package com.fluilet.app.presentation.tutor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.RbacRoleState
import com.fluilet.app.domain.model.SessionProgress
import com.fluilet.app.domain.model.UserRole

@Composable
fun TutorDashboardScreen(
    childId: String,
    modifier: Modifier = Modifier,
    viewModel: TutorDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(childId) { viewModel.loadDashboard(childId) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Monitoreo de Progreso en Tiempo Real", style = MaterialTheme.typography.titleLarge)
        if (uiState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartCard("Tiempo de Lectura Acumulado", Modifier.weight(1f)) {
                ReadingTimeLineChart(uiState.sessionProgress)
            }
            ChartCard("Porcentaje de Avance", Modifier.weight(1f)) {
                ProgressBarChart(uiState.sessionProgress)
            }
        }

        NlpLevelCard(uiState.nlpLevel) { level -> viewModel.onNlpLevelSelected(level, childId) }
        RbacRolesCard(uiState.rbacRoles, viewModel::onRoleToggled)

        uiState.errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
            LaunchedEffect(message) { viewModel.consumeError() }
        }
    }
}

@Composable
private fun ChartCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// NOTA: gráficos simplificados con Canvas para no atar el proyecto a una librería externa
// todavía. Si se requiere mayor fidelidad visual, sustituir por Vico o MPAndroidChart
// manteniendo la misma firma de entrada (List<SessionProgress>).

@Composable
private fun ReadingTimeLineChart(data: List<SessionProgress>) {
    if (data.isEmpty()) { EmptyChartPlaceholder(); return }
    val maxMinutes = data.maxOf { it.accumulatedMinutes }.coerceAtLeast(1f)
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics {
                contentDescription = "Tiempo de lectura acumulado por sesión: " +
                        data.joinToString { "${it.sessionLabel}: ${it.accumulatedMinutes.toInt()} minutos" }
            }
    ) {
        val stepX = size.width / (data.size - 1).coerceAtLeast(1)
        val points = data.mapIndexed { index, session ->
            Offset(stepX * index, size.height - (session.accumulatedMinutes / maxMinutes) * size.height)
        }
        for (i in 0 until points.size - 1) {
            drawLine(primaryColor, points[i], points[i + 1], strokeWidth = 4f, cap = StrokeCap.Round)
        }
        points.forEach { drawCircle(primaryColor, radius = 6f, center = it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        data.forEach { Text(it.sessionLabel, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun ProgressBarChart(data: List<SessionProgress>) {
    if (data.isEmpty()) { EmptyChartPlaceholder(); return }
    val secondaryColor = MaterialTheme.colorScheme.secondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics {
                contentDescription = "Porcentaje de avance por sesión: " +
                        data.joinToString { "${it.sessionLabel}: ${it.completionPercent} por ciento" }
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { session ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${session.completionPercent}%", style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier
                        .width(28.dp)
                        .fillMaxHeight(fraction = (session.completionPercent / 100f).coerceIn(0.02f, 1f))
                        .background(secondaryColor, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                )
                Text(session.sessionLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptyChartPlaceholder() {
    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
        Text("Sin datos de progreso aún", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NlpLevelCard(selectedLevel: NlpLevel, onLevelSelected: (NlpLevel) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Ajustes de Simplificación NLP", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                NlpLevel.entries.forEachIndexed { index, level ->
                    SegmentedButton(
                        selected = level == selectedLevel,
                        onClick = { onLevelSelected(level) },
                        shape = SegmentedButtonDefaults.itemShape(index, NlpLevel.entries.size),
                        modifier = Modifier.sizeIn(minHeight = 44.dp)
                    ) { Text(level.displayName) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(selectedLevel.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RbacRolesCard(roles: List<RbacRoleState>, onRoleToggled: (UserRole, Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Gestión de Roles RBAC", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            roles.forEach { roleState ->
                Row(
                    Modifier.fillMaxWidth().sizeIn(minHeight = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (roleState.role == UserRole.TUTOR) Icons.Filled.SupervisorAccount else Icons.Filled.ChildCare,
                            contentDescription = null
                        )
                        Text(roleState.label)
                    }
                    Switch(
                        checked = roleState.enabled,
                        onCheckedChange = { checked -> onRoleToggled(roleState.role, checked) },
                        modifier = Modifier.semantics {
                            contentDescription = "${roleState.label}, ${if (roleState.enabled) "activo" else "inactivo"}"
                        }
                    )
                }
            }
        }
    }
}