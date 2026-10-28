package com.fluilet.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * `GET /api/v1/adaptations/model-info` (y `model_info` de `GET /admin/overview`). El servidor envía además el
 * historial por época, el manifiesto del dataset y las pruebas de calibración: Gson ignora lo que no se mapea.
 */
data class ModelInfoDto(
    @SerializedName("model_version") val modelVersion: String? = null,
    @SerializedName("checkpoint_name") val checkpointName: String? = null,
    @SerializedName("active") val active: Boolean? = null,
    @SerializedName("acceptance_criteria_met") val acceptanceCriteriaMet: Boolean? = null,
    @SerializedName("all_bands_met") val allBandsMet: Boolean? = null,
    @SerializedName("dataset_v1_size") val datasetV1Size: Int? = null,
    @SerializedName("dataset_v2_size") val datasetV2Size: Int? = null,
    @SerializedName("epochs") val epochs: Int? = null,
    @SerializedName("baseline_sari") val baselineSari: Double? = null,
    @SerializedName("retrained_sari") val retrainedSari: Double? = null,
    /** Exactitud léxica plana, si el servidor la publica; si no, se toma de `retrained_metrics.lexical_top1`. */
    @SerializedName("lexical_accuracy") val lexicalAccuracy: Double? = null,
    @SerializedName("baseline_metrics") val baselineMetrics: ModelMetricsDto? = null,
    @SerializedName("retrained_metrics") val retrainedMetrics: ModelMetricsDto? = null,
    @SerializedName("band_compliance") val bandCompliance: Map<String, BandComplianceDto>? = null,
    @SerializedName("artifact_sha256") val artifactSha256: String? = null
)

data class ModelMetricsDto(
    @SerializedName("sari_score") val sariScore: Double? = null,
    @SerializedName("lexical_top1") val lexicalTop1: Double? = null
)

/** Una cohorte de `band_compliance` (`local_bank`, `easier_diagnostic`, `unseen_v2`, `managed_demos`). */
data class BandComplianceDto(
    @SerializedName("samples") val samples: Int? = null,
    @SerializedName("progressive") val progressive: Int? = null,
    @SerializedName("all_five_bands_met") val allFiveBandsMet: Int? = null
)
