package com.rifsxd.ksunext.ghostlock

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

/**
 * Controls the opt-in, best-effort post-reboot Ghostlock attempt.
 * A successful manual BL Root run arms the setting; the user can disable it
 * later in Settings. This does not make root persistent in the kernel.
 */
object GhostlockAutoMode {
    private const val PREFS_NAME = "settings"
    private const val PREF_ARMED = "auto_ghostlock_armed"
    private const val PREF_ENABLED = "auto_ghostlock_enabled"
    private const val JOB_ID = 0x5A535547

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
            .apply()
        setBootReceiverEnabled(context, true)
    }

    /** Returns false when enabling is attempted before a successful manual run. */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (enabled && !isArmed(context)) return false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ENABLED, enabled)
            .apply()
        setBootReceiverEnabled(context, enabled)
        if (!enabled) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
        return true
    }

    /** Schedule one deferred attempt for the current boot. */
    fun scheduleAfterBoot(context: Context): Boolean {
        if (!isEnabled(context)) return false
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return false
        val component = ComponentName(context, GhostlockAutoJobService::class.java)
        val job = JobInfo.Builder(JOB_ID, component)
            .setMinimumLatency(10_000L)
            .setOverrideDeadline(60_000L)
            .setRequiresDeviceIdle(false)
            .setRequiresCharging(false)
            .build()
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
    }

    private fun setBootReceiverEnabled(context: Context, enabled: Boolean) {
        runCatching {
            val component = ComponentName(context, GhostlockBootReceiver::class.java)
            val state = if (enabled) {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                component,
                state,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }
    }
}
