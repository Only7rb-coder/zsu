package com.rifsxd.ksunext.ghostlock

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal object GhostlockProcess {
    private const val TimeoutSeconds = 300L
    private const val StatusMarker = "\u001eGLK_STATUS"
    private const val StatusAck = "\u001eGLK_STATUS_ACK\n"
    private const val StatusDisabled = "\u001eGLK_STATUS_DISABLED"

    data class Result(val exitCode: Int, val timedOut: Boolean, val output: String)

    @Throws(IOException::class)
    fun writeFrame(output: OutputStream, payload: ByteArray) {
        require(payload.size <= 1024 * 1024) { "GhostLock profile exceeds 1 MiB" }
        val length = payload.size
        output.write(byteArrayOf(
            (length ushr 24).toByte(), (length ushr 16).toByte(),
            (length ushr 8).toByte(), length.toByte(),
        ))
        output.write(payload)
        output.flush()
    }

    fun handleStatusLine(
        line: String,
        output: OutputStream,
        onStatus: (String, String) -> Unit,
    ): Boolean {
        if (!line.startsWith(StatusMarker)) return false
        if (line.contains(StatusDisabled)) {
            runCatching { onStatus("", "disabled") }
            return true
        }
        val parts = line.removePrefix(StatusMarker).trim().split(' ')
        if (parts.size >= 2) {
            runCatching { onStatus(parts[0], parts[1]) }
            runCatching {
                output.write(StatusAck.toByteArray(Charsets.UTF_8))
                output.flush()
            }
        }
        return true
    }

    fun run(
        binary: File,
        ksud: File,
        profile: ByteArray,
        workDir: File,
        onLog: (String) -> Unit,
        onStatus: (String, String) -> Unit,
    ): Result {
        require(binary.isFile) { "missing GhostLock binary: ${binary.absolutePath}" }
        require(ksud.isFile) { "missing ZSU ksud: ${ksud.absolutePath}" }
        require(profile.size <= 1024 * 1024) { "GhostLock profile exceeds 1 MiB" }
        require(workDir.isDirectory || workDir.mkdirs()) { "cannot create ${workDir.absolutePath}" }

        val nativeLog = File(workDir, ".ghostlock_native.log")
        require(!nativeLog.exists() || nativeLog.delete()) { "cannot replace ${nativeLog.absolutePath}" }
        val ksuLog = File(workDir, "ghostlock-ksu-${System.currentTimeMillis()}-${System.nanoTime()}.log")
        val nativeLines = BoundedLog()
        val ksuLines = BoundedLog()
        val process = ProcessBuilder(
            binary.absolutePath,
            "--ghostlock-app-call",
            "--enable-status-record",
        )
            .directory(workDir)
            .redirectErrorStream(true)
            .redirectOutput(nativeLog)
            .apply {
                val environment = environment()
                environment["GHOSTLOCK_HOME"] = workDir.absolutePath
                environment["TMPDIR"] = workDir.absolutePath
                environment["HOME"] = workDir.absolutePath
                environment["GHOSTLOCK_KSUD"] = ksud.absolutePath
                environment["GHOSTLOCK_KSU_LOG"] = ksuLog.absolutePath
            }
            .start()

        val stdin = process.outputStream
        val nativeTailer = GhostlockFileTailer(nativeLog) { rawLine ->
            val line = rawLine.removeSuffix("\r")
            if (!handleStatusLine(line, stdin, onStatus)) {
                nativeLines.append(line)
                if (line.isNotBlank()) runCatching { onLog(line) }
            }
        }
        val ksuTailer = GhostlockFileTailer(ksuLog) { rawLine ->
            val line = rawLine.removeSuffix("\r")
            ksuLines.append(line)
            if (line.isNotBlank()) runCatching { onLog(line) }
        }
        var timedOut = false
        var interrupted = false
        var exitCode = 1
        try {
            nativeTailer.start("ghostlock-native-log")
            ksuTailer.start("ghostlock-ksu-log")
            val writeError = runCatching { writeFrame(stdin, profile) }.exceptionOrNull()
            if (writeError != null) {
                nativeLines.append("Unable to send GLK1 profile: ${writeError.message}")
                terminate(process)
            } else {
                val finished = process.waitFor(TimeoutSeconds, TimeUnit.SECONDS)
                if (finished) {
                    exitCode = process.exitValue()
                } else {
                    timedOut = true
                    terminate(process)
                }
            }
            if (process.isAlive) {
                exitCode = -1
            } else if (writeError == null && !timedOut) {
                exitCode = process.exitValue()
            }
        } catch (_: InterruptedException) {
            interrupted = true
            nativeLines.append("GhostLock was interrupted; native process terminated")
            runCatching { terminate(process) }
            exitCode = 1
        } finally {
            if (process.isAlive) runCatching { process.destroyForcibly() }
            nativeTailer.finish()
            ksuTailer.finish()
            runCatching { stdin.close() }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }

        val nativeOutput = nativeLines.text()
        val ksuOutput = ksuLines.text()
        val output = buildString {
            append(nativeOutput)
            if (ksuOutput.isNotBlank()) {
                if (isNotEmpty() && !endsWith("\n")) append('\n')
                append("--- KernelSU handoff log ---\n")
                append(ksuOutput)
            }
        }.trim()
        if (interrupted) Thread.currentThread().interrupt()
        return Result(exitCode, timedOut, output)
    }

    private fun terminate(process: Process) {
        process.destroy()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
        }
    }
}

/** Tails newline-complete UTF-8 records; partial bytes are re-read on the next drain. */
internal class GhostlockFileTailer(
    private val file: File,
    private val onLine: (String) -> Unit,
) {
    private val lock = Any()
    private var offset = 0L
    private var worker: Thread? = null

    fun start(name: String) {
        check(worker == null) { "tailer already started" }
        worker = thread(start = true, name = name, isDaemon = true) {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    drain()
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (_: IOException) {
                    try {
                        Thread.sleep(100)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    fun drain() = synchronized(lock) {
        if (!file.isFile) return@synchronized
        RandomAccessFile(file, "r").use { handle ->
            if (offset > handle.length()) offset = 0L
            handle.seek(offset)
            val pending = ByteArrayOutputStream()
            while (true) {
                val value = handle.read()
                if (value == -1) break
                if (value == '\n'.code) {
                    offset = handle.filePointer
                    onLine(String(pending.toByteArray(), Charsets.UTF_8))
                    pending.reset()
                } else {
                    pending.write(value)
                }
            }
        }
    }

    fun finish() {
        worker?.interrupt()
        try {
            worker?.join(2_000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        worker = null
        runCatching { drain() }
    }
}

private class BoundedLog(private val limit: Int = 256 * 1024) {
    private val text = StringBuilder()
    private var truncated = false

    @Synchronized
    fun append(line: String) {
        if (truncated) return
        val remaining = limit - text.length
        if (line.length + 1 > remaining) {
            if (remaining > 0) text.append(line.take(remaining))
            text.append("\n[log truncated; complete output remains in the run files]\n")
            truncated = true
        } else {
            text.append(line).append('\n')
        }
    }

    @Synchronized
    fun text(): String = text.toString()
}
