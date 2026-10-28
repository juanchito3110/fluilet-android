package com.fluilet.app.data.mapper

import com.fluilet.app.data.remote.dto.ModelInfoDto
import com.fluilet.app.domain.model.BandCohort
import com.fluilet.app.domain.model.ModelAudit

/**
 * `model-info` -> auditoría. Si el servidor dice que no hay modelo activo, se refleja tal cual (no se rellenan
 * métricas verificadas): solo un fallo de red justifica mostrar los valores del commit 42482e0.
 */
fun ModelInfoDto.toDomain(): ModelAudit {
    val known = ModelAudit.COHORT_LABELS
    val compliance = bandCompliance.orEmpty()
    val cohorts = (known.keys + compliance.keys.filterNot { it in known }).mapNotNull { key ->
        val c = compliance[key] ?: return@mapNotNull null
        val total = c.samples ?: return@mapNotNull null
        BandCohort(key, known[key] ?: key, c.allFiveBandsMet ?: 0, total, c.progressive ?: 0)
    }
    return ModelAudit(
        modelVersion = modelVersion?.takeIf { it.isNotBlank() } ?: "unavailable",
        checkpointName = checkpointName?.takeIf { it.isNotBlank() },
        active = active ?: (modelVersion != null && modelVersion != "unavailable"),
        acceptanceCriteriaMet = acceptanceCriteriaMet ?: false,
        allBandsMet = allBandsMet ?: false,
        datasetV1Size = datasetV1Size,
        datasetV2Size = (datasetV2Size ?: 0).coerceAtLeast(0),
        epochs = (epochs ?: 0).coerceAtLeast(0),
        baselineSari = baselineSari ?: baselineMetrics?.sariScore ?: 0.0,
        retrainedSari = retrainedSari ?: retrainedMetrics?.sariScore ?: 0.0,
        baselineLexicalAccuracy = fraction(baselineMetrics?.lexicalTop1),
        lexicalAccuracy = fraction(lexicalAccuracy ?: retrainedMetrics?.lexicalTop1),
        cohorts = cohorts,
        artifactSha256 = artifactSha256?.takeIf { it.isNotBlank() },
        fromServer = true
    )
}

/** Acepta fracción (0.7325) o porcentaje (73.25). */
private fun fraction(value: Double?): Double = when {
    value == null || value < 0 -> 0.0
    value > 1.0 -> value / 100.0
    else -> value
}
