package com.rifsxd.ksunext.ghostlock

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs the manager-bundled Ghostlock payload without invoking a root shell.
 * The payload remains entirely app-side; ZSU kernel and ksud source are not modified.
 */
object GhostlockRunner {
    private const val BINARY_NAME = "libghostlock.so"
    private const val KSUD_NAME = "libksud.so"
    private const val WORK_DIR_NAME = "ghostlock"
    private const val LOG_NAME = ".ghostlock_ksu.log"
    // The native path can retry W1/W2/W3 and then wait for the independent
    // KernelSU handoff. Keep a bounded four-minute deadline so slow devices do
    // not get cut off while still failing a genuinely stalled attempt promptly.
    private const val TIMEOUT_SECONDS = 240L

    /** Exact uname -r values for which the bundled offset tables exist. */
    val supportedKernels: Set<String> = setOf(
        "6.12.23-android16-5-g16e473de48a3-abogki462654244-4k",
        "6.12.23-android16-5-g75e9b1c7ae7c-abogki463945075-4k",
        "6.12.23-android16-5-g82efd98459a2-ab14457512-4k",
        "6.12.23-android16-5-ga8f88ad96df3-ab13929693-4k",
        "6.12.23-android16-5-gb2a876903b49-ab14541642-4k",
        "6.12.23-android16-5-gf1bdb13583da-ab13761046-4k",
        "6.12.30-android16-5-g6e872b4863d6-ab13847919-4k",
        "6.12.38-android16-5-g844001fb8721-ab14552068-4k",
        // UNVERIFIED TEST ALIAS: reuses the existing 6.12.38 offsets.
        "6.12.38-android16-5-g665eafb62659-ab14778838-4k",
        "6.6.102-android15-8-gb01b41c2647c-ab15574720-4k",
        "6.6.102-android15-8-gfe76d1bc97fd-ab14689815-4k",
        "6.6.118-android15-8-g2e6b9c3812c5-ab15114928-4k",
        "6.6.118-android15-8-g608a629fedf7-ab15154340-4k",
        "6.6.118-android15-8-g93e223c276e7-abogki500782043-4k",
        "6.6.118-android15-8-gc44b714366cc-abogki519650608-4k",
        "6.6.118-android15-8-ge56cf6b09cca-ab15511674-4k",
        "6.6.118-android15-8-ge58033dc8ea6-abogki498046332-4k",
        "6.6.118-android15-8-gebdfad32d749-ab15099304-4k",
        "6.6.30-android15-8-g54dcbfbef792-ab12368803-4k",
        "6.6.77-android15-8-g4a507830d890-ab13636293-4k",
        "6.6.77-android15-8-g63ce7556864c-ab13994517-4k",
        "6.6.77-android15-8-gca30f3b4bef6-abogki440974771-4k",
        "6.6.89-android15-8-g0889fe95bb10-ab14402178-4k",
        "6.6.89-android15-8-g096cdb6ecefc-ab14358676-4k",
        "6.6.89-android15-8-gf4dc45704e54-abogki446052083-4k",
        "6.6.92-android15-8-g3637f4904cf5-ab13944661-4k"
    )

    data class Result(
        val success: Boolean,
        val timedOut: Boolean,
        val output: String
    )

    fun isKernelSupported(release: String): Boolean = supportedKernels.contains(release)

    private fun runOnce(context: Context): Result {
        val workDir = File(context.filesDir, WORK_DIR_NAME)
        if (!workDir.exists() && !workDir.mkdirs()) {
            return Result(false, false, "Unable to create BL Root working directory: ${workDir.absolutePath}")
        }

        val packagedBinary = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)
        if (!packagedBinary.isFile) {
            return Result(false, false, "BL Root payload is not present in this APK")
        }

        // Native libraries are extracted to an executable filesystem location by Android.
        // Do not copy the ELF payload into filesDir: app-private data is commonly mounted noexec.
        val binary = packagedBinary
        val packagedKsud = File(context.applicationInfo.nativeLibraryDir, KSUD_NAME)

        val output = StringBuilder()
        val process = try {
            ProcessBuilder(binary.absolutePath)
                .directory(workDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["GHOSTLOCK_HOME"] = workDir.absolutePath
                    environment()["TMPDIR"] = workDir.absolutePath
                    environment()["HOME"] = workDir.absolutePath
                    if (packagedKsud.isFile) {
                        environment()["GHOSTLOCK_KSUD"] = packagedKsud.absolutePath
                    }
                }
                .start()
        } catch (error: IOException) {
            return Result(false, false, "Unable to start BL Root: ${error.message}")
        }

        val reader = thread(start = true, name = "ghostlock-output-reader", isDaemon = true) {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) {
                            output.append(line).append('\n')
                        }
                    }
                }
            } catch (_: IOException) {
                // The process may close its stream during timeout cleanup.
            }
        }

        val finished = try {
            process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            false
        }

        if (!finished) {
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
        }

        reader.join(3000)

        val ksuLog = File(workDir, LOG_NAME)
        if (ksuLog.isFile) {
            synchronized(output) {
                output.append("\n--- KernelSU handoff log ---\n")
                runCatching { output.append(ksuLog.readText()) }
            }
        }

        val exitCode = if (finished) process.exitValue() else -1
        val finalOutput = synchronized(output) { output.toString().trim() }
        val prefix = if (!finished) "BL Root timed out after ${TIMEOUT_SECONDS}s" else "BL Root exited with code $exitCode"
        val fullOutput = listOf(prefix, finalOutput).filter { it.isNotBlank() }.joinToString("\n")
        return Result(finished && exitCode == 0, !finished, fullOutput)
    }

    /**
     * A single bounded retry handles transient heap-spray/task-discovery misses.
     * Never retry a timeout, because that may indicate a stalled or unstable
     * kernel path rather than a normal first-attempt miss.
     */
    fun run(context: Context): Result {
        val first = runOnce(context)
        if (first.success || first.timedOut) return first
        Thread.sleep(750L)
        val second = runOnce(context)
        return if (second.success) {
            second.copy(output = "First attempt failed; controlled retry succeeded.\n${second.output}")
        } else {
            second.copy(output = "First attempt failed; controlled retry also failed.\n${second.output}")
        }
    }
}
