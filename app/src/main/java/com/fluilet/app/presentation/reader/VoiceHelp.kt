package com.fluilet.app.presentation.reader

import com.fluilet.app.core.tts.TtsState
import com.fluilet.app.core.tts.TtsStatus
import kotlin.math.roundToInt

/** Motivo de la ventanita de ayuda de la voz. */
enum class VoiceIssue {
    /** El volumen multimedia está en 0 o casi: la voz sonaría pero no se escucharía. */
    LOW_VOLUME,

    /** El motor de voz no está listo, no tiene español o no emitió audio. */
    ENGINE
}

/**
 * Cuándo mostrar la ventanita de ayuda de la voz en lugar de un aviso dentro de la pantalla de lectura (que achicaría la
 * tarjeta del texto). Solo al pedir "reproducir" o si el motor falla durante la lectura.
 */
object VoiceHelp {

    /** Hasta este porcentaje del máximo, el volumen multimedia se considera "muy bajo". */
    const val LOW_VOLUME_FRACTION = 0.10f

    /** Nivel al que "Subir volumen y escuchar" deja el volumen multimedia. */
    const val TARGET_VOLUME_FRACTION = 0.65f

    /** Antes de reproducir: problema que impediría escuchar la voz, o `null` si se puede reproducir ya. */
    fun beforePlay(tts: TtsState, volume: Int, maxVolume: Int): VoiceIssue? = when {
        tts.status == TtsStatus.Unavailable -> VoiceIssue.ENGINE
        isLow(volume, maxVolume) -> VoiceIssue.LOW_VOLUME
        else -> null
    }

    /**
     * Durante la lectura: una locución falló (no sonó) y el lector volvió a reposo → ayuda para activar el motor. Un motor
     * que ni siquiera inició ([TtsStatus.Unavailable]) no abre nada solo: la ventanita aparece al pulsar reproducir.
     */
    fun afterError(tts: TtsState): VoiceIssue? = if (tts.errorMessage != null && tts.status == TtsStatus.Idle) VoiceIssue.ENGINE else null

    fun isLow(volume: Int, maxVolume: Int): Boolean = maxVolume > 0 && volume <= (maxVolume * LOW_VOLUME_FRACTION).toInt()

    /** Volumen al 65 % del máximo (al menos 1). */
    fun targetVolume(maxVolume: Int): Int = (maxVolume * TARGET_VOLUME_FRACTION).roundToInt().coerceIn(1, maxVolume.coerceAtLeast(1))

    fun percent(volume: Int, maxVolume: Int): Int = if (maxVolume <= 0) 0 else (volume * 100f / maxVolume).roundToInt()
}

/**
 * Ventana "Preparar dictado por voz" al entrar a una lectura, ANTES de fijar la pantalla del Modo Enfoque (que
 * bloquea los ajustes del teléfono). `true` = mostrarla; `false` = empezar ya; `null` = el motor aún está iniciando.
 */
object VoicePrep {
    fun needed(tts: TtsState, volume: Int, maxVolume: Int): Boolean? = when {
        tts.status == TtsStatus.Uninitialized || tts.status == TtsStatus.Initializing -> null
        tts.status == TtsStatus.Unavailable -> true
        // "Español (México) admitido" pero sin voz descargada (caso Huawei): no sonaría.
        tts.localVoiceReady == false -> true
        VoiceHelp.isLow(volume, maxVolume) -> true
        // Hasta que la voz suene bien una vez en este teléfono.
        !tts.voiceVerified -> true
        else -> false
    }

    /** Si el motor sigue iniciando tras este tiempo, se muestra la ventana igualmente (nunca se queda esperando). */
    const val MAX_WAIT_MS = 4_000L
}

