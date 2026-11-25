package com.rifsxd.ksunext.ghostlock

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

class GhostlockProcessTest {
    private class CloseTrackingOutputStream : OutputStream() {
        private val delegate = ByteArrayOutputStream()
        var closed = false
        override fun write(value: Int) = delegate.write(value)
        override fun close() { closed = true }
        fun bytes(): ByteArray = delegate.toByteArray()
    }

    @Test
    fun frameIsBigEndianAndLeavesChannelOpen() {
        val output = CloseTrackingOutputStream()
        GhostlockProcess.writeFrame(output, byteArrayOf(1, 2, 3, 4))
        assertArrayEquals(byteArrayOf(0, 0, 0, 4, 1, 2, 3, 4), output.bytes())
        assertFalse(output.closed)
    }

    @Test
    fun emptyFrameHasZeroLengthAndStaysOpen() {
        val output = CloseTrackingOutputStream()
        GhostlockProcess.writeFrame(output, ByteArray(0))
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), output.bytes())
        assertFalse(output.closed)
    }

    @Test
    fun oversizedFrameIsRejectedBeforeWriting() {
        val output = CloseTrackingOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            GhostlockProcess.writeFrame(output, ByteArray(1024 * 1024 + 1))
        }
        assertEquals(0, output.bytes().size)
    }

    @Test
    fun statusLinesAreConsumedAndAcknowledged() {
        val output = ByteArrayOutputStream()
        val events = mutableListOf<Pair<String, String>>()
        assertTrue(GhostlockProcess.handleStatusLine("\u001eGLK_STATUS w1a in_progress", output) { step, status ->
            events += step to status
        })
        assertEquals(listOf("w1a" to "in_progress"), events)
        assertEquals("\u001eGLK_STATUS_ACK\n", output.toString(Charsets.UTF_8))
    }

    @Test
    fun disabledStatusClearsStateWithoutAcknowledgement() {
        val output = ByteArrayOutputStream()
        val events = mutableListOf<Pair<String, String>>()
        assertTrue(GhostlockProcess.handleStatusLine("\u001eGLK_STATUS_DISABLED", output) { step, status ->
            events += step to status
        })
        assertEquals(listOf("" to "disabled"), events)
        assertEquals(0, output.size())
    }

    @Test
    fun malformedStatusIsConsumedWithoutAcknowledgement() {
        val output = ByteArrayOutputStream()
        assertTrue(GhostlockProcess.handleStatusLine("\u001eGLK_STATUS", output) { _, _ -> })
        assertEquals(0, output.size())
    }

    @Test
    fun tailerRereadsPartialLineAndDrainsFinalCompleteLine() {
        val directory = createTempDir()
        val file = File(directory, "native.log")
        val lines = mutableListOf<String>()
        val tailer = GhostlockFileTailer(file, lines::add)
        file.writeText("first\npartial")
        tailer.drain()
        assertEquals(listOf("first"), lines)
        file.appendText("-complete\nlast")
        tailer.drain()
        assertEquals(listOf("first", "partial-complete"), lines)
        file.appendText("-line\n")
        tailer.drain()
        assertEquals(listOf("first", "partial-complete", "last-line"), lines)
        directory.deleteRecursively()
    }
}
