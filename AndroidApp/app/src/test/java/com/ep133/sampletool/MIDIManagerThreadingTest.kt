package com.ep133.sampletool

import org.junit.Ignore
import org.junit.Test

class MIDIManagerThreadingTest {

    @Ignore("Wave 0 stub — implement after threading fix")
    @Test
    fun onMidiReceived_isInvokedOnMainThread() {
        // Implement after MIDIManager threading fix is merged.
        // Verify that MidiReceiver.onSend callbacks are dispatched to the main thread
        // before invoking onMidiReceived, preventing crashes from Compose state mutations
        // on the MIDI thread.
    }
}
