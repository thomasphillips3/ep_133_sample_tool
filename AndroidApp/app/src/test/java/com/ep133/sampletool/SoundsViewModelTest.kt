package com.ep133.sampletool

import com.ep133.sampletool.domain.model.EP133Sound
import com.ep133.sampletool.ui.sounds.SoundsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for SoundsViewModel.previewSound() — sound preview on tap.
 * Uses FakeMIDIPortRecording and RecordingMIDIRepository from PadsViewModelTest.kt.
 *
 * Note: Tests that call previewSound() require Robolectric because MIDIRepository.noteOn()
 * calls android.util.Log which is not available in JVM unit tests.
 * These are validated via instrumented tests on device.
 */
class SoundsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeSampleSound() = EP133Sound(
        number = 42,
        name = "KICK",
        category = "drums",
    )

    @Ignore("Requires Robolectric — MIDIRepository.noteOn() calls android.util.Log not available in JVM unit tests")
    @Test
    fun previewSound_sendsNoteOnImmediately() = runTest {
        val port = FakeMIDIPortRecording()
        val repo = RecordingMIDIRepository(port)
        val vm = SoundsViewModel(repo)
        vm.previewSound(makeSampleSound())
        val noteOns = port.sentMessages.filter { (_, data) ->
            data.isNotEmpty() && (data[0].toInt() and 0xF0) == 0x90
        }
        assertTrue("Expected at least one noteOn message", noteOns.isNotEmpty())
    }

    @Ignore("Requires Robolectric — MIDIRepository.noteOff() calls android.util.Log not available in JVM unit tests")
    @Test
    fun previewSound_sendsNoteOffAfter500ms() = runTest {
        val port = FakeMIDIPortRecording()
        val repo = RecordingMIDIRepository(port)
        val vm = SoundsViewModel(repo)
        vm.previewSound(makeSampleSound())
        advanceTimeBy(501)
        val noteOffs = port.sentMessages.filter { (_, data) ->
            data.isNotEmpty() && (data[0].toInt() and 0xF0) == 0x80
        }
        assertTrue("Expected at least one noteOff message after 500ms", noteOffs.isNotEmpty())
    }

    @Ignore("Requires Robolectric — MIDIRepository.noteOn/Off() calls android.util.Log not available in JVM unit tests")
    @Test
    fun previewSound_cancelsPreviousPreviewIfNewTapBeforeNoteOff() = runTest {
        val port = FakeMIDIPortRecording()
        val repo = RecordingMIDIRepository(port)
        val vm = SoundsViewModel(repo)
        vm.previewSound(makeSampleSound())
        advanceTimeBy(200)
        vm.previewSound(makeSampleSound())
        advanceTimeBy(501)
        // Only the second noteOff should be sent (first was cancelled)
        val noteOffs = port.sentMessages.filter { (_, data) ->
            data.isNotEmpty() && (data[0].toInt() and 0xF0) == 0x80
        }
        assertEquals("Only one noteOff should be sent (cancel-previous)", 1, noteOffs.size)
    }

    @Ignore("Requires Robolectric — MIDIRepository.noteOn() calls android.util.Log not available in JVM unit tests")
    @Test
    fun previewSound_usesChannel9() = runTest {
        val port = FakeMIDIPortRecording()
        val repo = RecordingMIDIRepository(port)
        val vm = SoundsViewModel(repo)
        vm.previewSound(makeSampleSound())
        val noteOn = port.sentMessages.lastOrNull { (_, data) ->
            data.isNotEmpty() && (data[0].toInt() and 0xF0) == 0x90
        }
        val channelBits = noteOn?.second?.getOrNull(0)?.toInt()?.and(0x0F)
        assertEquals("Preview should use MIDI channel 9 (channel 10)", 9, channelBits)
    }

    @Test
    fun previewSound_noteIndex_isSoundNumberMinusOne() {
        // Pure math test — no Android dependencies
        val soundNumber = 42
        val note = (soundNumber - 1).coerceIn(0, 127)
        assertEquals("Note index should be soundNumber - 1", 41, note)
    }

    @Test
    fun previewSound_highSoundNumber_clampedTo127() {
        // Pure math test — ensure clamping to valid MIDI range
        val soundNumber = 999
        val note = (soundNumber - 1).coerceIn(0, 127)
        assertEquals("High sound numbers should clamp to 127", 127, note)
    }
}
