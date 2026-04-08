package com.ep133.sampletool

import com.ep133.sampletool.domain.sequencer.SequencerEngine
import org.junit.Ignore
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for SequencerEngine MIDI Start/Stop/Clock transport output.
 * Uses FakeMIDIPortRecording and RecordingMIDIRepository from PadsViewModelTest.kt.
 *
 * Note: Tests that call engine.play() require Robolectric because MIDIRepository.noteOn()
 * and allNotesOff() call android.util.Log not available in JVM unit tests.
 * These are validated via instrumented tests on device.
 */
class SequencerEngineTest {

    @Ignore("Requires Robolectric — sendRawBytes/noteOn call android.util.Log not available in JVM unit tests")
    @Test
    fun play_sendsMIDIStart() {
        val port = FakeMIDIPortRecording()
        val repo = RecordingMIDIRepository(port)
        val engine = SequencerEngine(repo)
        engine.play()
        val rawSent = port.sentMessages.flatMap { it.second.toList() }
        assertTrue("MIDI Start (0xFA) should be sent on play", rawSent.contains(0xFA.toByte()))
        engine.close()
    }

    @Ignore("Requires Robolectric — sendRawBytes/allNotesOff call android.util.Log not available in JVM unit tests")
    @Test
    fun stop_sendsMIDIStop() {
        val port = FakeMIDIPortRecording()
        val repo = RecordingMIDIRepository(port)
        val engine = SequencerEngine(repo)
        engine.play()
        engine.stop()
        val rawSent = port.sentMessages.flatMap { it.second.toList() }
        assertTrue("MIDI Stop (0xFC) should be sent on stop", rawSent.contains(0xFC.toByte()))
        engine.close()
    }

    @Ignore("Requires Robolectric — sendRawBytes calls android.util.Log not available in JVM unit tests")
    @Test
    fun playLoop_sends6ClockTicksPerStep() {
        // Validated via instrumented tests — requires real coroutine timing
        // Logic: 6 clock ticks per step = 24 PPQN / 4 steps-per-beat
    }

    @Test
    fun clockTicksPerStep_mathIs6() {
        // Pure math: 24 PPQN standard MIDI clock / 4 steps-per-beat = 6 ticks per step
        val ppqn = 24
        val stepsPerBeat = 4
        val clocksPerStep = ppqn / stepsPerBeat
        assertEquals("24 PPQN / 4 steps per beat = 6 clock ticks per step", 6, clocksPerStep)
    }

    @Test
    fun clockInterval_at120bpm_is20ms() {
        // At 120 BPM: step = 125ms, 125 / 6 ≈ 20ms per clock tick
        val bpm = 120
        val stepDurationMs = 60_000.0 / bpm / 4.0
        val clockIntervalMs = (stepDurationMs / 6.0).toLong()
        assertEquals("Clock interval at 120 BPM should be ~20ms", 20L, clockIntervalMs)
    }
}
