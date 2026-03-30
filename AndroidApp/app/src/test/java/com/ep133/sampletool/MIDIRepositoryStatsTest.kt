package com.ep133.sampletool

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Ignore
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for MIDIRepository.queryDeviceStats() — device stat queries via SysEx.
 * Wave 0 stubs — production code created in Task 1-03.
 */
class MIDIRepositoryStatsTest {

    @Ignore("Wave 1 — queryDeviceStats not yet implemented")
    @Test
    fun queryDeviceStats_timesOutAfter5Seconds() = runTest {
        // val fake = FakeMIDIPort()
        // val repo = MIDIRepository(fake)
        // No GREET response is sent — result should be null after 5s
        // val result = repo.queryDeviceStats()
        // assertNull(result) or assertFalse(result)
    }

    @Ignore("Wave 1 — queryDeviceStats not yet implemented")
    @Test
    fun queryDeviceStats_populatesFirmwareVersion() = runTest {
        // val fake = FakeMIDIPort()
        // val repo = MIDIRepository(fake)
        // Simulate a GREET SysEx response "sw_version:1.3.2;serial:ABC"
        // repo.queryDeviceStats()
        // assertEquals("1.3.2", repo.deviceState.value.firmwareVersion)
    }

    @Ignore("Wave 1 — queryDeviceStats not yet implemented")
    @Test
    fun queryDeviceStats_populatesStorageFields() = runTest {
        // Simulate FILE_METADATA response "used_space_in_bytes:1048576;max_capacity:8388608"
        // assert storageUsedBytes and storageTotalBytes populated
    }

    @Test
    fun statsNull_beforeQuery() {
        // DeviceState() should have all stats fields as null
        // This test is pure data class — no @Ignore needed
        // NOTE: Requires DeviceState to have sampleCount/storageUsedBytes/etc fields (Task 1-03)
        // For now verify default DeviceState compiles without those fields — task 1-03 will add them
        // This test is intentionally a no-op until Task 1-03 adds the fields
    }

    @Ignore("Wave 1 — queryDeviceStats not yet implemented")
    @Test
    fun queryDeviceStats_populatesSampleCount() = runTest {
        // Simulate FILE_LIST response for "/sounds" returning 3 entries
        // assert DeviceState.sampleCount == 3
    }
}
