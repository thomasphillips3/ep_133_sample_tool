package com.ep133.sampletool

import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test

class SequencerEngineScopeTest {

    @Ignore("Wave 0 stub — implement after SequencerEngine scope fix")
    @Test
    fun close_cancelsPendingNoteOffJobs() = runTest {
        // Implement after SequencerEngine.close() is added.
        // Verify that calling close() cancels all pending note-off coroutine jobs,
        // preventing coroutine leaks after app destroy or screen navigation.
    }
}
