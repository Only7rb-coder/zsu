package com.rifsxd.ksunext.ghostlock

import android.content.Context
import com.rifsxd.ksunext.ui.util.getKsuDaemonFile
import java.io.File

/** Runs the manager-bundled GhostLock payload using the upstream GLK1 contract. */
object GhostlockRunner {
    private const val BinaryName = "libghostlock.so"
    private const val WorkDirectoryName = "ghostlock"
    private const val ResultLogName = "last-run.log"

    data class Result(val success: Boolean, val timedOut: Boolean, val output: String)

    /** The bundled profile catalog is the single source of truth for support. */
    fun isKernelSupported(context: Context, release: String): Boolean =
        GhostlockProfileConfiguration.hasProfile(context, release)

    fun run(context: Context, onOutput: (String) -> Unit = {}): Result {
        val release = GhostlockKernelRelease.current()
        val pair = GhostlockCpuPairCatalog.selected(context, release)
        val profile = try {
            GhostlockProfileConfiguration.resolve(context, release, pair.main, pair.consumer)
        } catch (error: Exception) {
            return Result(false, false, "Unable to resolve GhostLock profile: ${error.message}")
        }
        return run(context, profile, onOutput)
    }

    internal fun run(
        context: Context,
        profile: ResolvedGhostlockProfile,
        onOutput: (String) -> Unit = {},
    ): Result {
        GhostlockOperationLock.tryAcquire()?.use {
            val workDir = File(context.filesDir, WorkDirectoryName)
            if (!workDir.isDirectory && !workDir.mkdirs()) {
                return Result(false, false, "Unable to create GhostLock working directory")
            }
            val binary = File(context.applicationInfo.nativeLibraryDir, BinaryName)
            val ksud = getKsuDaemonFile()
            if (!binary.isFile) return Result(false, false, "GhostLock payload is not present in this APK")
            if (!ksud.isFile) return Result(false, false, "ZSU ksud is not present in this APK")

            val runState = GhostlockRunState(File(workDir, ".ghostlock_run_state.json"))
            runState.reset()
            val native = try {
                GhostlockProcess.run(binary, ksud, profile.bytes, workDir, onOutput) { step, status ->
                    if (status == "disabled") runState.clear() else runState.apply(step, status)
                }
            } catch (error: Exception) {
                return Result(false, false, "Unable to start GhostLock: ${error.message}")
            }
            val prefix = when {
                native.timedOut -> "GhostLock timed out after 300 seconds"
                native.exitCode == 137 ->
                    "GhostLock was killed by SIGKILL (137), usually Android memory/process pressure; reboot before retrying"
                native.exitCode > 128 ->
                    "GhostLock was terminated by signal ${native.exitCode - 128} (exit ${native.exitCode})"
                else -> "GhostLock exited with code ${native.exitCode}"
            }
            val output = listOf(prefix, native.output).filter(String::isNotBlank).joinToString("\n")
            runCatching { File(workDir, ResultLogName).writeText(output) }
            return Result(!native.timedOut && native.exitCode == 0, native.timedOut, output)
        }
        return Result(false, false, "Another GhostLock operation is already running")
    }
}
