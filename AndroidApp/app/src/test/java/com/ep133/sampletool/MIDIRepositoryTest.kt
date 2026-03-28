package com.ep133.sampletool

import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test

class MIDIRepositoryTest {

    @Ignore("Wave 0 stub — implement after threading fix")
    @Test
    fun deviceState_emitsConnectedTrueWhenDeviceAdded() = runTest {
        // Implement after MIDIRepository threading changes are stable.
        // Verify that deviceState StateFlow emits connected=true when a device is added.
    }
}
