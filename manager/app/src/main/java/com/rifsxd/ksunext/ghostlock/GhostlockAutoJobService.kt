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
    private var activeRun: kotlinx.coroutines.Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!GhostlockAutoMode.isEnabled(this)) return false
        val supportedAbi = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
        val supportedKernel = GhostlockRunner.isKernelSupported(
            this,
            GhostlockKernelRelease.current()
        )
        if (!supportedAbi || !supportedKernel) return false
        if (activeRun?.isActive == true) return false
        if (!GhostlockAutoMode.claimAttempt(this)) return false
        activeRun = jobScope.launch {
            try {
                val result = runCatching { GhostlockRunner.run(applicationContext) }
                    .getOrNull()
                jobFinished(params, false)
                GhostlockAutoMode.clearAttempt(applicationContext)
                if (result?.success != true) {
                    /* A completed native failure is not retryable: the route
                     * may have partially changed kernel state. Require the
                     * user to inspect the log and explicitly re-arm Auto mode. */
                    GhostlockAutoMode.disableAfterFailure(applicationContext)
                }
            } finally {
                /* If the job is interrupted or the process is killed, the
                 * running marker remains set so the next boot refuses another
                 * attempt. */
                activeRun = null
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        activeRun?.cancel()
        activeRun = null
        return true
    }

    override fun onDestroy() {
        jobScope.cancel()
        super.onDestroy()
    }
}
