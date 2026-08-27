package com.rifsxd.ksunext.ghostlock

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Runs one post-boot Ghostlock attempt without keeping a background service alive. */
class GhostlockAutoJobService : JobService() {
    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        if (!GhostlockAutoMode.isEnabled(this)) return false
        val supportedAbi = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
        val supportedKernel = GhostlockRunner.isKernelSupported(
            System.getProperty("os.version", "unknown")
        )
        if (!supportedAbi || !supportedKernel) return false
        jobScope.launch {
            runCatching { GhostlockRunner.run(applicationContext) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        jobScope.cancel()
        super.onDestroy()
    }
}
