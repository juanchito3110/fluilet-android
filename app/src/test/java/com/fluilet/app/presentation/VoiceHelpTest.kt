package com.fluilet.app.presentation

import com.fluilet.app.core.tts.SpeechText
import com.fluilet.app.core.tts.TtsState
import com.fluilet.app.core.tts.TtsStatus
import com.fluilet.app.presentation.reader.VoiceHelp
import com.fluilet.app.presentation.reader.VoiceIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ventanita de ayuda de la voz: cuándo aparece y qué hace "Subir volumen y escuchar"; texto limpio para el motor. */
class VoiceHelpTest {

    private val idle = TtsState(status = TtsStatus.Idle)

    @Test fun playingIsAllowedWithAReadyEngineAndAudibleVolume() {
        assertNull(VoiceHelp.beforePlay(idle, volume = 8, maxVolume = 15))
        assertNull("motor iniciando: el play queda en cola", VoiceHelp.beforePlay(TtsState(status = TtsStatus.Initializing), 8, 15))
    }

    @Test fun mutedOrNearlyMutedMediaVolumeAsksToRaiseIt() {
        assertEquals(VoiceIssue.LOW_VOLUME, VoiceHelp.beforePlay(idle, volume = 0, maxVolume = 15))
        assertEquals(VoiceIssue.LOW_VOLUME, VoiceHelp.beforePlay(idle, volume = 1, maxVolume = 15))
        assertNull(VoiceHelp.beforePlay(idle, volume = 2, maxVolume = 15))
    }

    @Test fun anEngineWithoutSpanishAsksToActivateItEvenWithVolume() {
        val unavailable = TtsState(status = TtsStatus.Unavailable, errorMessage = "Falta la voz en español.", missingVoiceData = true)
        assertEquals(VoiceIssue.ENGINE, VoiceHelp.beforePlay(unavailable, volume = 10, maxVolume = 15))
    }

    @Test fun aFailedUtteranceOpensTheHelpButAnUnavailableEngineWaitsForPlay() {
        assertEquals(VoiceIssue.ENGINE, VoiceHelp.afterError(TtsState(status = TtsStatus.Idle, errorMessage = "No se escuchó la voz.")))
        assertNull("al abrir el lector sin voz no salta sola", VoiceHelp.afterError(TtsState(status = TtsStatus.Unavailable, errorMessage = "Falta la voz")))
        assertNull(VoiceHelp.afterError(TtsState(status = TtsStatus.Playing, errorMessage = "x")))
        assertNull(VoiceHelp.afterError(idle))
    }

    @Test fun raisingTheVolumeLeavesItAt65PercentOfTheMaximum() {
        assertEquals(10, VoiceHelp.targetVolume(15))
        assertEquals(16, VoiceHelp.targetVolume(25))
        assertEquals(1, VoiceHelp.targetVolume(1))
        assertEquals(0, VoiceHelp.percent(0, 15))
        assertEquals(67, VoiceHelp.percent(10, 15))
    }

    @Test fun speechTextIsCleanedWithoutMovingAnyWord() {
        val raw = "Hola\nmundo​ feliz.\tFin ya."
        val clean = SpeechText.clean(raw)
        assertEquals(raw.length, clean.length)
        assertEquals("Hola mundo  feliz. Fin ya.", clean)
        assertEquals(raw.indexOf("feliz"), clean.indexOf("feliz"))
    }
}
