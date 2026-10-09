package com.rifsxd.ksunext.ghostlock

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Runs the manager-bundled GhostLock payload using the upstream JSON profile contract. */
object GhostlockRunner {
    private const val BINARY_NAME = "libghostlock.so"
    private const val KSUD_NAME = "libksud.so"
    private const val WORK_DIR_NAME = "ghostlock"
    private const val LOG_NAME = ".ghostlock_ksu.log"
    private const val PROFILE_NAME = "active-profile.json"
    // Profiles allow heap preparation to run for up to 240 seconds. Keep the
    // outer watchdog longer so it cannot stop a valid slow run prematurely.
    private const val TIMEOUT_SECONDS = 300L

    data class Result(val success: Boolean, val timedOut: Boolean, val output: String)

    /** The bundled profile catalog is the single source of truth for support. */
    fun isKernelSupported(context: Context, release: String): Boolean =
        GhostlockProfileConfiguration.hasProfile(context, release)

    private fun runOnce(context: Context): Result {
        val workDir = File(context.filesDir, WORK_DIR_NAME)
        if (!workDir.exists() && !workDir.mkdirs()) {
            return Result(false, false, "Unable to create GhostLock working directory")
        }
        val packagedBinary = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)
        if (!packagedBinary.isFile) {
            return Result(false, false, "GhostLock payload is not present in this APK")
        }
        // Match the native payload's uname(2) check; os.version can be stale
        // or "unknown" on Android, including some RedMagic builds.
        val release = GhostlockKernelRelease.current()
        if (!isKernelSupported(context, release)) {
            return Result(false, false, "No bundled GhostLock profile for $release")
        }
        val cpuPair = GhostlockCpuPairCatalog.selected(context)
        val profile = try {
            GhostlockProfileConfiguration.resolve(context, release, cpuPair.main, cpuPair.consumer)
        } catch (error: Exception) {
            return Result(false, false, "Unable to resolve GhostLock profile: ${error.message}")
        }
        val profileFile = File(workDir, PROFILE_NAME)
        profileFile.writeText(profile)
        val packagedKsud = File(context.applicationInfo.nativeLibraryDir, KSUD_NAME)
        if (!packagedKsud.isFile) {
            return Result(false, false, "ZSU ksud is not present in this APK")
        }
        val output = StringBuilder()
        val process = try {
            ProcessBuilder(packagedBinary.absolutePath, "--profile", profileFile.absolutePath)
                .directory(workDir)
                .redirectErrorStream(true)
                .apply {
                    val env = environment()
                    env["GHOSTLOCK_HOME"] = workDir.absolutePath
                    env["TMPDIR"] = workDir.absolutePath
                    env["HOME"] = workDir.absolutePath
                    env["GHOSTLOCK_KSUD"] = packagedKsud.absolutePath
                    // The native runtime applies explicit CPU choices from these variables.
                    env["GHOSTLOCK_CORE"] = cpuPair.main.toString()
                    env["GHOSTLOCK_CONSUMER_CORE"] = cpuPair.consumer.toString()
                }
                .start()
        } catch (error: IOException) {
            return Result(false, false, "Unable to start GhostLock: ${error.message}")
        }
        val reader = thread(start = true, name = "ghostlock-output-reader", isDaemon = true) {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) { output.append(line).append('\n') }
                    }
                }
            } catch (_: IOException) {
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
                    .append(runCatching { ksuLog.readText() }.getOrDefault(""))
            }
        }
        val exitCode = if (finished) process.exitValue() else -1
        val finalOutput = synchronized(output) { output.toString().trim() }
        val prefix = if (!finished) {
            "GhostLock timed out after $TIMEOUT_SECONDS seconds"
        } else {
            "GhostLock exited with code $exitCode"
        }
        return Result(
            finished && exitCode == 0,
            !finished,
            listOf(prefix, finalOutput).filter { it.isNotBlank() }.joinToString("\n"),
        )
    }

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
