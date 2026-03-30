package com.ep133.sampletool

import org.junit.Ignore
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for BackupManager — PAK backup creation and restore via TE file-transfer protocol.
 * Wave 0 stubs — production code created in Task 2-01.
 */
class BackupRestoreTest {

    @Ignore("Wave 2 — BackupManager not yet implemented")
    @Test
    fun backupFile_isZipFormat() {
        // val backupBytes = ... (completed backup)
        // ZIP magic: PK header [0x50, 0x4B, 0x03, 0x04]
        // assertEquals(0x50.toByte(), backupBytes[0])
        // assertEquals(0x4B.toByte(), backupBytes[1])
        // assertEquals(0x03.toByte(), backupBytes[2])
        // assertEquals(0x04.toByte(), backupBytes[3])
    }

    @Ignore("Wave 2 — BackupManager not yet implemented")
    @Test
    fun backupFile_containsWavFiles() {
        // Assert ZIP entry names include at least one ".wav" entry
    }

    @Ignore("Wave 2 — BackupManager not yet implemented")
    @Test
    fun backupFile_containsMetadataJson() {
        // Assert ZIP entry names include a ".json" entry
    }

    @Ignore("Wave 2 — BackupManager not yet implemented")
    @Test
    fun fileGetProtocol_buildsCorrectFrame() {
        // val frame = SysExProtocol.buildFileGetFrame(deviceId = 0, path = "/sounds/001.wav", chunkIndex = 0, requestId = 1)
        // FILE_GET command byte is 3
    }

    @Ignore("Wave 2 — BackupManager not yet implemented")
    @Test
    fun restoreFromValidPak_sendsFilePutCommands() {
        // Given a valid PAK byte array, BackupManager.restore() should invoke sendMidi with FILE_PUT (cmd=2) frames
    }
}
