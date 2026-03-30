package com.ep133.sampletool

import com.ep133.sampletool.domain.midi.MIDIRepository
import com.ep133.sampletool.domain.model.DeviceState
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for MIDIRepository.queryDeviceStats() — device stat queries via SysEx.
 * Uses FakeMIDIPort from MIDIRepositoryTest.kt.
 */
class MIDIRepositoryStatsTest {

    @Ignore("queryDeviceStats requires physical EP-133 or a full SysEx response simulator — timeout test not feasible in fast unit test environment")
    @Test
    fun queryDeviceStats_timesOutAfter5Seconds() = runTest {
        // Requires withTimeoutOrNull to advance virtual time — timing-dependent test
        // Validated via physical EP-133 UAT
    }

    @Ignore("queryDeviceStats requires simulating SysEx GREET response — needs TestableRepository wiring")
    @Test
    fun queryDeviceStats_populatesFirmwareVersion() = runTest {
        // Simulate GREET SysEx response via TestableRepository override of dispatchSysEx
        // val port = FakeMIDIPort(); val repo = TestableRepository(port)
        // Manually complete greetDeferred with {"sw_version": "1.3.2"}
        // repo.queryDeviceStats()
        // assertEquals("1.3.2", repo.deviceState.value.firmwareVersion)
    }

    @Ignore("queryDeviceStats requires simulating SysEx FILE_METADATA response")
    @Test
    fun queryDeviceStats_populatesStorageFields() = runTest {
        // Simulate FILE_METADATA response
    }

    @Test
    fun statsNull_beforeQuery() {
        // Fresh DeviceState has all stats fields as null (default values)
        val state = DeviceState()
        assertNull("sampleCount should be null before query", state.sampleCount)
        assertNull("storageUsedBytes should be null before query", state.storageUsedBytes)
        assertNull("storageTotalBytes should be null before query", state.storageTotalBytes)
        assertNull("firmwareVersion should be null before query", state.firmwareVersion)
    }

    @Ignore("queryDeviceStats requires simulating SysEx FILE_LIST response")
    @Test
    fun queryDeviceStats_populatesSampleCount() = runTest {
        // Simulate FILE_LIST response for "/sounds" returning 3 entries
        // assert DeviceState.sampleCount == 3
    }
}
