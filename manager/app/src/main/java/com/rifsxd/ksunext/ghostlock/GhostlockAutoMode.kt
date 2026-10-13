package com.rifsxd.ksunext.ghostlock

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle

/**
 * Controls the opt-in, best-effort post-reboot Ghostlock attempt.
 * A successful manual BL Root run arms the setting; the user can disable it
 * later in Settings. Ghostlock changes live kernel state, so this re-applies
 * it after reboot rather than making the kernel state itself persistent.
 */
object GhostlockAutoMode {
    private const val PREFS_NAME = "settings"
    private const val PREF_ARMED = "auto_ghostlock_armed"
    private const val PREF_ENABLED = "auto_ghostlock_enabled"
    private const val JOB_ID = 0x5A535547
    private const val MAX_BOOT_ATTEMPTS = 3
    private const val EXTRA_ATTEMPT = "ghostlock_attempt"

    fun isArmed(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_ARMED, false)

    fun isEnabled(context: Context): Boolean =
        isArmed(context) && context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_ENABLED, false)

    /** Arms and enables automatic attempts after a successful manual run. */
    fun onManualSuccess(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ARMED, true)
            .putBoolean(PREF_ENABLED, true)
            // This flag controls behavior at the next boot; wait for the
            // write to reach disk before reporting success to the UI.
            .commit()
        setBootReceiverEnabled(context, true)
    }

    /** Returns false when enabling is attempted before a successful manual run. */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (enabled && !isArmed(context)) return false
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ENABLED, enabled)
            // Do not leave boot behavior in an in-memory apply() queue.
            .commit()
        if (!saved) return false
        if (enabled) setBootReceiverEnabled(context, true)
        if (!enabled) {
            cancelScheduledJobs(context)
        }
        return true
    }

    /** Schedule one deferred attempt for the current boot. */
    fun scheduleAfterBoot(context: Context): Boolean {
        return schedule(context, attempt = 0)
    }

    /** Schedule a bounded retry after a failed attempt in the same boot. */
    fun scheduleRetry(context: Context, completedAttempt: Int): Boolean {
        val nextAttempt = completedAttempt + 1
        if (nextAttempt >= MAX_BOOT_ATTEMPTS) return false
        return schedule(context, nextAttempt)
    }

    private fun schedule(context: Context, attempt: Int): Boolean {
        if (!isEnabled(context)) return false
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return false
        val component = ComponentName(context, GhostlockAutoJobService::class.java)
        val job = JobInfo.Builder(JOB_ID + attempt, component)
            .setMinimumLatency(if (attempt == 0) 10_000L else attempt * 30_000L)
            .setOverrideDeadline(if (attempt == 0) 60_000L else attempt * 60_000L)
            .setRequiresDeviceIdle(false)
            .setRequiresCharging(false)
            .setExtras(PersistableBundle().apply {
                putInt(EXTRA_ATTEMPT, attempt)
            })
            .build()
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
    }

    private fun cancelScheduledJobs(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.let { scheduler ->
            repeat(MAX_BOOT_ATTEMPTS) { attempt ->
                scheduler.cancel(JOB_ID + attempt)
            }
        }
    }

    private fun setBootReceiverEnabled(context: Context, enabled: Boolean) {
        if (!enabled) return
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, GhostlockBootReceiver::class.java),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }
    }
}
