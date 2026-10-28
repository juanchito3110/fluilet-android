package com.fluilet.app.domain.model

import java.util.Locale

/** Par `original -> adapted` que reemplazó el motor (`lexical_replacements` del backend o del motor local). */
data class LexicalReplacement(val original: String, val adapted: String)

/**
 * Bandas Szigriszt-Pazos que exige el backend 42482e0 a cada nivel (`docs/model-retraining-report.md`):
 * N1 82–100, N2 74–84, N3 65–74, N4 58–66, N5 50–65. Se solapan a propósito: no son la escala INFLESZ.
 */
enum class ReadabilityBand(val level: Int, val min: Double, val max: Double) {
    N1(1, 82.0, 100.0),
    N2(2, 74.0, 84.0),
    N3(3, 65.0, 74.0),
    N4(4, 58.0, 66.0),
    N5(5, 50.0, 65.0);

    operator fun contains(score: Double): Boolean = score in min..max

    /** Ej.: `82–100`. */
    val range: String get() = "${min.toInt()}–${max.toInt()}"

    companion object {
        fun forLevel(level: Int?): ReadabilityBand? = entries.firstOrNull { it.level == level }
    }
}

/**
 * Una de las 5 variantes de la misma lectura (`level_previews`): texto completo del nivel, su puntaje
 * Szigriszt-Pazos, la etiqueta de dificultad, palabras por oración y sílabas por palabra.
 */
data class LevelPreview(
    val level: Int,
    val adaptedText: String,
    val readabilityScore: Double,
    val readabilityLevel: String,
    val avgSentenceLength: Double,
    val syllablesPerWord: Double,
    /** `target_met` del servidor (banda + longitud de oración); el motor local lo calcula solo con la banda. */
    val targetMet: Boolean = false,
    /** Reemplazos propios de este nivel (solo los conoce el motor local; el servidor los da para el nivel pedido). */
    val lexicalReplacements: List<LexicalReplacement> = emptyList(),
    val fromServer: Boolean = true
) {
    val band: ReadabilityBand? get() = ReadabilityBand.forLevel(level)

    /** El puntaje cae dentro de la banda de su nivel. */
    val inBand: Boolean get() = band?.contains(readabilityScore) == true
}

/** Lectura asignada a un niño (`GET /children/{id}/library`): metadatos numéricos, sin texto. */
data class LibraryItem(
    val documentId: String,
    val category: String,
    val level: Int?,
    val originalScore: Double?,
    val adaptedScore: Double?
)

/** Una cohorte de la verificación de 5 bandas (`band_compliance`): cuántas lecturas cumplen las 5 a la vez. */
data class BandCohort(val key: String, val label: String, val met: Int, val total: Int, val progressive: Int) {
    val complete: Boolean get() = total > 0 && met == total && progressive == total

    /** Ej.: `Catálogo 31/31`. */
    val summary: String get() = "$label $met/$total"
}

/**
 * Auditoría del modelo reentrenado (`GET /adaptations/model-info` y `model_info` de `GET /admin/overview`).
 * Las precisiones léxicas son fracciones (0.7325 = 73.25 %). [fromServer] es `false` cuando se muestran los
 * valores verificados del commit 42482e0 porque el servidor no respondió.
 */
data class ModelAudit(
    val modelVersion: String,
    val checkpointName: String?,
    val active: Boolean,
    val acceptanceCriteriaMet: Boolean,
    val allBandsMet: Boolean,
    val datasetV1Size: Int?,
    val datasetV2Size: Int,
    val epochs: Int,
    val baselineSari: Double,
    val retrainedSari: Double,
    val baselineLexicalAccuracy: Double,
    val lexicalAccuracy: Double,
    val cohorts: List<BandCohort>,
    val artifactSha256: String? = null,
    val fromServer: Boolean = true
) {
    /** Ej.: `4,738`. */
    val datasetPairs: String get() = String.format(Locale.US, "%,d", datasetV2Size)

    /** Ej.: `73.25%` y `53.07%`. */
    val lexicalAccuracyText: String get() = percent(lexicalAccuracy)
    val baselineLexicalAccuracyText: String get() = percent(baselineLexicalAccuracy)

    /** Ej.: `44.558` y `44.544`. */
    val retrainedSariText: String get() = String.format(Locale.US, "%.3f", retrainedSari)
    val baselineSariText: String get() = String.format(Locale.US, "%.3f", baselineSari)

    /** Ej.: `Catálogo 31/31 · EASIER 6/6 · Controlados 6/6 · Demos 15/15`. */
    val bandsSummary: String get() = cohorts.joinToString(" · ") { it.summary }

    /** Las 5 bandas y la progresión se cumplen en todas las cohortes medidas. */
    val fiveBandsVerified: Boolean get() = allBandsMet && cohorts.isNotEmpty() && cohorts.all { it.complete }

    /** Ej.: `context-ranker-es-v2.3 · context-ranker-v2.3`. */
    val versionLine: String get() = listOfNotNull(modelVersion, checkpointName?.takeIf { it.isNotBlank() }).joinToString(" · ")

    /** Sello del lector: `4,738 pares · SARI 44.56 · Exactitud 73.25% · 5 Bandas Verificadas`. */
    val badgeLine: String
        get() = buildString {
            append("$datasetPairs pares · SARI ").append(String.format(Locale.US, "%.2f", retrainedSari))
            append(" · Exactitud ").append(lexicalAccuracyText)
            if (fiveBandsVerified) append(" · 5 Bandas Verificadas")
        }

    companion object {
        /** Claves de `band_compliance` en el orden y con el nombre que se muestran. */
        val COHORT_LABELS: Map<String, String> = linkedMapOf(
            "local_bank" to "Catálogo",
            "easier_diagnostic" to "EASIER",
            "unseen_v2" to "Controlados",
            "managed_demos" to "Demos"
        )

        private fun percent(fraction: Double) = String.format(Locale.US, "%.2f%%", fraction * 100)

        /** Valores de `GET /adaptations/model-info` verificados en el commit 42482e0 del backend (v2.3). */
        val VERIFIED_42482E0 = ModelAudit(
            modelVersion = "context-ranker-es-v2.3",
            checkpointName = "context-ranker-v2.3",
            active = true,
            acceptanceCriteriaMet = true,
            allBandsMet = true,
            datasetV1Size = 7704,
            datasetV2Size = 4738,
            epochs = 36,
            baselineSari = 44.54384786845761,
            retrainedSari = 44.557636124673856,
            baselineLexicalAccuracy = 0.5307017543859649,
            lexicalAccuracy = 0.7324561403508771,
            cohorts = listOf(
                BandCohort("local_bank", "Catálogo", 31, 31, 31),
                BandCohort("easier_diagnostic", "EASIER", 6, 6, 6),
                BandCohort("unseen_v2", "Controlados", 6, 6, 6),
                BandCohort("managed_demos", "Demos", 15, 15, 15)
            ),
            artifactSha256 = "67e79bf3c13381cc8d000575e6e57b4f9787f895cbfb8cfc4569fb9ae3ba2507",
            fromServer = false
        )
    }
}
