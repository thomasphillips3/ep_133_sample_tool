package com.ep133.sampletool

import com.ep133.sampletool.domain.midi.MIDIRepository
import com.ep133.sampletool.midi.MIDIPort
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * FakeMIDIPort: minimal MIDIPort implementation for unit testing MIDIRepository.
 */
class FakeMIDIPort : MIDIPort {
    override var onMidiReceived: ((String, ByteArray) -> Unit)? = null
    override var onDevicesChanged: (() -> Unit)? = null

    private val _inputs = mutableListOf<MIDIPort.Device>()
    private val _outputs = mutableListOf<MIDIPort.Device>()

    override fun getUSBDevices(): MIDIPort.Devices =
        MIDIPort.Devices(inputs = _inputs.toList(), outputs = _outputs.toList())

    override fun sendMidi(portId: String, data: ByteArray) {}
    override fun requestUSBPermissions() {}
    override fun refreshDevices() {}
    override fun startListening(portId: String) {}
    override fun closeAllListeners() {}
    override fun prewarmSendPort(portId: String) {}
    override fun close() {}

    /** Simulate a device being added — triggers onDevicesChanged callback. */
    fun simulateDeviceAdded(id: String, name: String) {
        _outputs.add(MIDIPort.Device(id, name))
        _inputs.add(MIDIPort.Device(id, name))
        onDevicesChanged?.invoke()
    }
}

class MIDIRepositoryTest {

    @Test
    fun deviceState_emitsConnectedTrueWhenDeviceAdded() = runTest {
        val fake = FakeMIDIPort()
        val repo = MIDIRepository(fake)

        // Initially disconnected
        assertFalse(repo.deviceState.value.connected)

        // Simulate device added
        fake.simulateDeviceAdded("test-out", "EP-133")

        // deviceState should now be connected
        assertTrue(repo.deviceState.value.connected)
    }
}
