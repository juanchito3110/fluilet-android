package com.fluilet.app.core

import com.fluilet.app.core.tts.SpeechEngineInit
import com.fluilet.app.core.tts.TtsManager
import com.fluilet.app.core.tts.TtsStatus
import com.fluilet.app.testutil.FakeSpeechEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsManagerTest {

    private val chunks = listOf("Hola mundo feliz.", "Segundo bloque corto.")

    private fun ready(engine: FakeSpeechEngine = FakeSpeechEngine()): Pair<TtsManager, FakeSpeechEngine> {
        val manager = TtsManager { engine }
        manager.initialize()
        manager.load(chunks)
        return manager to engine
    }

    @Test fun initialization() {
        val (m, _) = ready()
        assertEquals(TtsStatus.Idle, m.state.value.status)
        assertEquals(2, m.state.value.chunkCount)
    }

    @Test fun unavailableVoiceIsReportedAndPlayDoesNothing() {
        val engine = FakeSpeechEngine(SpeechEngineInit.Unavailable("Falta la voz", missingVoiceData = true))
        val (m, _) = ready(engine)
        assertEquals(TtsStatus.Unavailable, m.state.value.status)
        assertTrue(m.state.value.missingVoiceData)
        m.play()
        assertTrue(engine.spoken.isEmpty())
    }

    @Test fun playSpeaksCurrentChunkAtConfiguredRate() {
        val (m, e) = ready()
        m.setSpeechRate(0.7f)
        m.play()
        assertEquals(TtsStatus.Playing, m.state.value.status)
        assertEquals("Hola mundo feliz.", e.last.text)
        assertEquals(0.7f, e.last.rate, 0.001f)
    }

    @Test fun rangeStartHighlightsExactWord() {
        val (m, e) = ready()
        m.play()
        e.range(5, 10) // "mundo"
        assertEquals(5 until 10, m.state.value.highlight)
        assertEquals("mundo", chunks[0].substring(m.state.value.highlight!!.first, m.state.value.highlight!!.last + 1))
    }

    @Test fun pauseStopsEngineAndResumeContinuesFromCurrentWord() {
        val (m, e) = ready()
        m.play()
        e.range(5, 10)
        val stopsBefore = e.stopCount
        m.pause()
        assertEquals(TtsStatus.Paused, m.state.value.status)
        assertEquals(stopsBefore + 1, e.stopCount)

        m.resume()
        assertEquals(TtsStatus.Playing, m.state.value.status)
        assertEquals("mundo feliz.", e.last.text) // desde la palabra en curso
        // Los rangos del nuevo texto se re-mapean al bloque original (offset 5).
        e.range(6, 11)
        assertEquals(11 until 16, m.state.value.highlight)
        assertEquals("feliz", chunks[0].substring(11, 16))
    }

    @Test fun staleEventsAreIgnoredAfterPause() {
        val (m, e) = ready()
        m.play()
        val firstId = e.last.id
        e.range(0, 4, firstId)
        m.pause()
        e.done(firstId) // un onDone tardío de la locución cancelada
        assertEquals(TtsStatus.Paused, m.state.value.status)
        assertFalse(m.state.value.finished)
        assertEquals(0, m.state.value.chunkIndex)
    }

    @Test fun doneAdvancesToNextChunkAndFinishesAtEnd() {
        val (m, e) = ready()
        m.play()
        e.done()
        assertEquals(1, m.state.value.chunkIndex)
        assertEquals("Segundo bloque corto.", e.last.text)
        assertNull(m.state.value.highlight)
        e.done()
        assertEquals(TtsStatus.Idle, m.state.value.status)
        assertTrue(m.state.value.finished)
    }

    @Test fun stopClearsHighlightAndKeepsChunk() {
        val (m, e) = ready()
        m.play()
        e.done()
        e.range(0, 7)
        m.stop()
        assertEquals(TtsStatus.Idle, m.state.value.status)
        assertNull(m.state.value.highlight)
        assertEquals(1, m.state.value.chunkIndex)
    }

    @Test fun changingRateWhilePlayingRestartsFromCurrentWord() {
        val (m, e) = ready()
        m.play()
        e.range(5, 10)
        m.setSpeechRate(1.2f)
        assertEquals("mundo feliz.", e.last.text)
        assertEquals(1.2f, e.last.rate, 0.001f)
    }

    @Test fun rateIsClampedForChildren() {
        val (m, _) = ready()
        m.setSpeechRate(9f)
        assertEquals(TtsManager.MAX_RATE, m.state.value.speechRate, 0.0001f)
        m.setSpeechRate(0.1f)
        assertEquals(TtsManager.MIN_RATE, m.state.value.speechRate, 0.0001f)
    }

    @Test fun seekWhilePlayingSpeaksNewChunk() {
        val (m, e) = ready()
        m.play()
        m.seekToChunk(1)
        assertEquals(1, m.state.value.chunkIndex)
        assertEquals("Segundo bloque corto.", e.last.text)
        assertEquals(TtsStatus.Playing, m.state.value.status)
    }

    @Test fun seekWhileIdleOnlyMovesTheChunk() {
        val (m, e) = ready()
        m.seekToChunk(1)
        assertEquals(1, m.state.value.chunkIndex)
        assertTrue(e.spoken.isEmpty())
        assertEquals(TtsStatus.Idle, m.state.value.status)
    }

    @Test fun engineErrorReturnsToIdleWithMessage() {
        val (m, e) = ready()
        m.play()
        e.error()
        assertEquals(TtsStatus.Idle, m.state.value.status)
        assertTrue(m.state.value.errorMessage != null)
    }

    @Test fun releaseShutsEngineDown() {
        val (m, e) = ready()
        m.play()
        m.release()
        assertTrue(e.shutdownCalled)
        assertEquals(TtsStatus.Uninitialized, m.state.value.status)
    }

    // --- "🔊 Escuchar palabra" del Glosario Mágico --------------------------------------------

    @Test fun speakWordPronouncesOnlyThatWordWhileIdle() {
        val (m, e) = ready()
        m.speakWord("planeta")
        assertEquals("planeta", e.last.text)
        assertTrue(e.last.id.startsWith("word-"))
        assertEquals(TtsStatus.Idle, m.state.value.status)
    }

    @Test fun speakWordPausesThePlaybackInProgress() {
        val (m, e) = ready()
        m.play()
        m.speakWord("lava")
        assertEquals(TtsStatus.Paused, m.state.value.status)
        assertEquals("lava", e.last.text)
    }

    @Test fun speakWordIgnoresBlankWordsAndUnavailableVoices() {
        val (m, e) = ready()
        val before = e.spoken.size
        m.speakWord("   ")
        assertEquals(before, e.spoken.size)

        val broken = FakeSpeechEngine(SpeechEngineInit.Unavailable("Sin voz", missingVoiceData = true))
        val (m2, e2) = ready(broken)
        m2.speakWord("volcán")
        assertTrue(e2.spoken.isEmpty())
    }

    @Test fun speakWordBeforeInitializationDoesNothing() {
        val engine = FakeSpeechEngine()
        TtsManager { engine }.speakWord("estrella")
        assertTrue(engine.spoken.isEmpty())
    }
}
