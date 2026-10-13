package com.rifsxd.ksunext.ghostlock

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Controls the opt-in, best-effort post-reboot Ghostlock attempt.
 * A successful manual BL Root run arms the setting; the user can disable it
 * later in Settings. Ghostlock changes live kernel state, so this re-applies
 * it after a full reboot rather than making the kernel state itself persistent.
 */
object GhostlockAutoMode {
    private const val PREFS_NAME = "settings"
    private const val PREF_ARMED = "auto_ghostlock_armed"
    private const val PREF_ENABLED = "auto_ghostlock_enabled"
    private const val PREF_ATTEMPT_STATE = "auto_ghostlock_attempt_state"
    private const val PREF_LAST_BOOT_COUNT = "auto_ghostlock_last_boot_count"
    private const val PREF_SCHEDULED_ELAPSED = "auto_ghostlock_scheduled_elapsed"
    private const val ATTEMPT_NONE = 0
    private const val ATTEMPT_PENDING = 1
    private const val ATTEMPT_RUNNING = 2
    private const val JOB_ID = 0x5A535547

    fun isArmed(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_ARMED, false)

    fun isEnabled(context: Context): Boolean =
        isArmed(context) && context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_ENABLED, false)

    /** Arms Auto Ghostlock after success, but keeps execution explicitly opt-in. */
    fun onManualSuccess(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ARMED, true)
            .putInt(PREF_ATTEMPT_STATE, ATTEMPT_NONE)
            .remove(PREF_SCHEDULED_ELAPSED)
            // This flag controls behavior at the next full boot; wait for the
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
            .putInt(PREF_ATTEMPT_STATE, ATTEMPT_NONE)
            .remove(PREF_SCHEDULED_ELAPSED)
            // Do not leave boot behavior in an in-memory apply() queue.
            .commit()
        if (!saved) return false
        if (enabled) setBootReceiverEnabled(context, true)
        if (!enabled) cancelScheduledJob(context)
        return true
    }

    /** Schedule one deferred attempt for the current full boot only. */
    fun scheduleAfterBoot(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        when (prefs.getInt(PREF_ATTEMPT_STATE, ATTEMPT_NONE)) {
            ATTEMPT_RUNNING -> {
                /* The previous boot died while native Ghostlock was active. */
                disableAfterFailure(context)
                return false
            }
            ATTEMPT_PENDING -> {
                /* A duplicate BOOT_COMPLETED in the same boot must not replace
                 * the already queued job. */
                val currentBoot = currentBootCount(context)
                val lastBoot = prefs.getInt(PREF_LAST_BOOT_COUNT, -1)
                if (currentBoot < 0 || currentBoot == lastBoot) return false
                prefs.edit().putInt(PREF_ATTEMPT_STATE, ATTEMPT_NONE).commit()
                cancelScheduledJob(context)
            }
        }
        val currentBoot = currentBootCount(context)
        val lastBoot = prefs.getInt(PREF_LAST_BOOT_COUNT, -1)
        if (currentBoot >= 0 && currentBoot == lastBoot) {
            /* Userspace/soft reboot keeps the kernel and boot generation. */
            return false
        }
        return schedule(context, currentBoot)
    }

    /** Claim the pending job; running state survives process death/reboot. */
    fun claimAttempt(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(PREF_ATTEMPT_STATE, ATTEMPT_NONE) != ATTEMPT_PENDING) {
            return false
        }
        val scheduledAt = prefs.getLong(PREF_SCHEDULED_ELAPSED, -1L)
        if (scheduledAt >= 0L && SystemClock.elapsedRealtime() < scheduledAt) {
            /* JobScheduler delivered a non-persisted job across a reboot. */
            clearAttempt(context)
            return false
        }
        return prefs.edit()
            .putInt(PREF_ATTEMPT_STATE, ATTEMPT_RUNNING)
            .commit()
    }

    fun clearAttempt(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_ATTEMPT_STATE, ATTEMPT_NONE)
            .remove(PREF_SCHEDULED_ELAPSED)
            .commit()
    }

    /** Disable automatic execution after a completed native failure. */
    fun disableAfterFailure(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ENABLED, false)
            .putInt(PREF_ATTEMPT_STATE, ATTEMPT_NONE)
            .remove(PREF_SCHEDULED_ELAPSED)
            .commit()
        cancelScheduledJob(context)
    }

    private fun schedule(context: Context, bootCount: Int): Boolean {
        if (!isEnabled(context)) return false
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return false
        val component = ComponentName(context, GhostlockAutoJobService::class.java)
        val now = SystemClock.elapsedRealtime()
        val marked = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_ATTEMPT_STATE, ATTEMPT_PENDING)
            .putInt(PREF_LAST_BOOT_COUNT, bootCount)
            .putLong(PREF_SCHEDULED_ELAPSED, now)
            .commit()
        if (!marked) return false
        val job = JobInfo.Builder(JOB_ID, component)
            .setMinimumLatency(10_000L)
            .setOverrideDeadline(60_000L)
            .setRequiresDeviceIdle(false)
            .setRequiresCharging(false)
            .build()
        val result = scheduler.schedule(job)
        if (result != JobScheduler.RESULT_SUCCESS) clearAttempt(context)
        return result == JobScheduler.RESULT_SUCCESS
    }

    private fun currentBootCount(context: Context): Int =
        runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.BOOT_COUNT,
                -1,
            )
        }.getOrDefault(-1)

    private fun cancelScheduledJob(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
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
