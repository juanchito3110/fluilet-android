package com.fluilet.app.presentation.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.fluilet.app.presentation.common.ButtonLabel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fluilet.app.R
import com.fluilet.app.core.tts.TtsStatus
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import com.fluilet.app.core.tts.VoiceTest
import kotlinx.coroutines.delay

/** Estado de la ventanita de ayuda de la voz y la acción "reproducir" que la consulta antes de sonar. */
@Stable
internal class VoiceHelpController(private val context: Context, private val vm: ReaderViewModel) {
    var issue by mutableStateOf<VoiceIssue?>(null)
        private set

    /** Ventana "Preparar dictado por voz" visible, y si ya se decidió empezar la lectura (Modo Enfoque). */
    var prepVisible by mutableStateOf(false)
        private set
    var prepDone by mutableStateOf(vm.readingStarted)
        private set
    private var openedSettings = false
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    val volume: Int get() = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    val maxVolume: Int get() = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

    /** ▶/⏸: pausar siempre funciona; para reproducir, primero se revisa volumen y motor. */
    fun playOrPause(state: ReaderUiState) {
        val busy = state.tts.status == TtsStatus.Playing || state.tts.pendingPlay
        if (busy) {
            vm.togglePlayPause()
            return
        }
        val problem = VoiceHelp.beforePlay(state.tts, volume, maxVolume)
        if (problem != null) issue = problem else vm.togglePlayPause()
    }

    fun show(problem: VoiceIssue) {
        issue = problem
    }

    fun dismiss() {
        issue = null
        vm.dismissVoiceError()
    }

    /** Sube el volumen multimedia al 65 % y reproduce de inmediato. */
    fun raiseVolumeAndPlay() {
        raiseVolume()
        issue = null
        vm.dismissVoiceError()
        vm.togglePlayPause()
    }

    private fun raiseVolume() {
        try {
            audio?.setStreamVolume(AudioManager.STREAM_MUSIC, VoiceHelp.targetVolume(maxVolume), AudioManager.FLAG_SHOW_UI)
        } catch (_: SecurityException) {
            // Algún modo "No molestar" impide el cambio: el sistema muestra su propio control.
        }
    }

    /** Abre la activación/instalación de voces del teléfono; al volver se reintenta el motor. */
    fun openVoiceEngine() {
        launchVoiceSettings()
        issue = null
        vm.dismissVoiceError()
    }

    /** Descarga/activación de voces (ACTION_INSTALL_TTS_DATA, luego los ajustes de voz y, al final, Ajustes). */
    private fun launchVoiceSettings() {
        val intents = listOf(
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent(TTS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        openedSettings = intents.any { intent ->
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }

    // --- Preparar dictado por voz (antes del Modo Enfoque) -----------------------------------------

    fun showPrep() {
        if (!prepDone) prepVisible = true
    }

    /** "Probar voz ahora": sube el volumen si está muy bajo y pronuncia la frase de prueba. */
    fun testVoice() {
        if (VoiceHelp.isLow(volume, maxVolume)) raiseVolume()
        vm.testVoice()
    }

    /** "Descargar / Activar voz en el teléfono", sin cerrar la ventana: al volver se revisa de nuevo la voz. */
    fun downloadVoice() = launchVoiceSettings()

    /** "Continuar a la lectura": empieza la lectura y, con un niño, el Modo Enfoque. */
    fun continueReading() {
        prepVisible = false
        prepDone = true
        vm.dismissVoiceError()
        vm.beginReading()
    }

    fun onResume() {
        if (openedSettings) {
            openedSettings = false
            vm.retryVoice()
        }
    }

    private companion object {
        const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
    }
}

@Composable
internal fun rememberVoiceHelp(state: ReaderUiState, vm: ReaderViewModel): VoiceHelpController {
    val context = LocalContext.current
    val controller = remember(vm) { VoiceHelpController(context, vm) }
    // Si el motor falla durante la lectura (no sonó), aparece la ventanita en vez de un aviso dentro de la pantalla.
    LaunchedEffect(state.tts.errorMessage, state.tts.status) {
        VoiceHelp.afterError(state.tts)?.let(controller::show)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.onResume() }
    return controller
}

/**
 * Ventanita emergente de la voz: nunca empuja ni achica la tarjeta de lectura. Ofrece subir el volumen y escuchar
 * (si está muy bajo), activar el motor de voz del teléfono y cerrar para seguir leyendo en silencio.
 */
@Composable
internal fun VoiceHelpDialog(controller: VoiceHelpController, state: ReaderUiState) {
    val issue = controller.issue ?: return
    val lowVolume = VoiceHelp.isLow(controller.volume, controller.maxVolume)
    AlertDialog(
        onDismissRequest = controller::dismiss,
        icon = {
            Icon(
                if (issue == VoiceIssue.LOW_VOLUME) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.RecordVoiceOver,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                stringResource(if (issue == VoiceIssue.LOW_VOLUME) R.string.voice_help_title_volume else R.string.voice_help_title_engine),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (issue == VoiceIssue.LOW_VOLUME) {
                        stringResource(R.string.voice_help_body_volume, VoiceHelp.percent(controller.volume, controller.maxVolume))
                    } else {
                        state.tts.errorMessage ?: stringResource(R.string.voice_help_body_engine)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (issue == VoiceIssue.ENGINE && state.lockActive) {
                    Text(stringResource(R.string.voice_help_lock_note), style = MaterialTheme.typography.bodySmall)
                }
                if (lowVolume) {
                    Button(
                        onClick = controller::raiseVolumeAndPlay,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        ButtonLabel(stringResource(R.string.voice_help_raise))
                    }
                }
                val activate: @Composable RowScope.() -> Unit = {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    ButtonLabel(stringResource(R.string.voice_help_activate))
                }
                if (lowVolume) {
                    OutlinedButton(onClick = controller::openVoiceEngine, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { activate() }
                } else {
                    Button(onClick = controller::openVoiceEngine, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { activate() }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.voice_help_close)) } }
    )
}

/**
 * Al entrar a una lectura decide, ANTES de fijar la pantalla, si hace falta la ventana "Preparar dictado por voz":
 * motor sin español, voz no descargada, volumen en 0 o voz nunca verificada en este teléfono. Si todo está listo,
 * empieza la lectura directamente. Si el motor tarda en iniciar, la ventana aparece igual tras [VoicePrep.MAX_WAIT_MS].
 */
@Composable
internal fun VoicePrepGate(controller: VoiceHelpController, state: ReaderUiState) {
    LaunchedEffect(state.tts.status, state.tts.localVoiceReady, state.tts.voiceVerified) {
        if (controller.prepDone || controller.prepVisible) return@LaunchedEffect
        when (VoicePrep.needed(state.tts, controller.volume, controller.maxVolume)) {
            false -> controller.continueReading()
            true -> controller.showPrep()
            null -> {
                delay(VoicePrep.MAX_WAIT_MS)
                controller.showPrep()
            }
        }
    }
    if (controller.prepVisible) VoicePrepDialog(controller, state)
}

@Composable
private fun VoicePrepDialog(controller: VoiceHelpController, state: ReaderUiState) {
    val tts = state.tts
    AlertDialog(
        // Atrás o tocar fuera = continuar: la lectura nunca queda detrás de una ventana cerrada.
        onDismissRequest = controller::continueReading,
        icon = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(32.dp)) },
        title = { Text(stringResource(R.string.voice_prep_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.voice_prep_body), style = MaterialTheme.typography.bodyMedium)
                val status = when {
                    tts.voiceTest == VoiceTest.RUNNING -> stringResource(R.string.voice_prep_testing)
                    tts.voiceTest == VoiceTest.PASSED -> stringResource(R.string.voice_prep_passed)
                    tts.voiceTest == VoiceTest.FAILED -> stringResource(R.string.voice_prep_failed)
                    tts.status == TtsStatus.Uninitialized || tts.status == TtsStatus.Initializing -> stringResource(R.string.voice_prep_status_starting)
                    tts.status == TtsStatus.Unavailable -> stringResource(R.string.voice_prep_status_unavailable)
                    tts.localVoiceReady == false -> stringResource(R.string.voice_prep_status_no_local)
                    VoiceHelp.isLow(controller.volume, controller.maxVolume) ->
                        stringResource(R.string.voice_prep_status_volume, VoiceHelp.percent(controller.volume, controller.maxVolume))
                    else -> stringResource(R.string.voice_prep_status_unverified)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (tts.voiceTest) {
                        VoiceTest.RUNNING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        VoiceTest.PASSED -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        else -> Unit
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (tts.voiceTest == VoiceTest.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = controller::testVoice,
                    enabled = tts.voiceTest != VoiceTest.RUNNING,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    ButtonLabel(stringResource(R.string.voice_prep_test))
                }
                OutlinedButton(
                    onClick = controller::downloadVoice,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    ButtonLabel(stringResource(R.string.voice_prep_download))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = controller::continueReading) {
                Text(stringResource(R.string.voice_prep_continue), style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

