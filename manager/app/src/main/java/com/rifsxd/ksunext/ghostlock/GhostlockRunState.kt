package com.rifsxd.ksunext.ghostlock

import java.io.File
import java.io.FileOutputStream

internal class GhostlockRunState(private val file: File) {
    private val lock = Any()
    private val steps = linkedMapOf<String, String>()

    fun reset() = synchronized(lock) {
        steps.clear()
        Steps.forEach { steps[it] = NotStarted }
        persistLocked()
    }

    fun apply(step: String, status: String) = synchronized(lock) {
        if (step in steps) {
            steps[step] = status
            persistLocked()
        }
    }

    fun clear() = synchronized(lock) {
        steps.clear()
        file.delete()
    }

    fun stuckStep(): String? = synchronized(lock) {
        Steps.firstOrNull { steps[it] == InProgress }
    }

    private fun persistLocked() {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            out.write(RunStateCodec.encode(steps, System.currentTimeMillis()).toByteArray())
            out.flush()
            out.fd.sync()
        }
    }

    companion object {
        val Steps = RunStateCodec.Steps
        const val NotStarted = RunStateCodec.NotStarted
        const val InProgress = RunStateCodec.InProgress
        const val Completed = RunStateCodec.Completed
    }
}
