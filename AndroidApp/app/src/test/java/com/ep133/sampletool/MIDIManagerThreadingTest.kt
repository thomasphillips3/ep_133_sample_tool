package com.ep133.sampletool

import org.junit.Ignore
import org.junit.Test

class MIDIManagerThreadingTest {

    @Ignore("Requires Android instrumented environment — mainHandler.post{} dispatches to Looper.getMainLooper() which is not available in JVM unit tests. Threading fix is validated by Phase 1 code review.")
    @Test
    fun onMidiReceived_isInvokedOnMainThread() {
        // Cannot be meaningfully unit-tested without a real Android Looper.
        // MidiReceiver.onSend fires on a MIDI thread; mainHandler.post{} ensures callbacks
        // arrive on the main thread before invoking onMidiReceived.
        // Validated structurally in Phase 1 — see MIDIManager.kt mainHandler.post{} usage.
    }
}
