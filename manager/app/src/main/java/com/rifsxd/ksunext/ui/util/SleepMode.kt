package com.rifsxd.ksunext.ui.util

import android.content.Context
import androidx.core.content.edit
import com.topjohnwu.superuser.Shell

/**
 * Puts only the ZSU manager application into Android's inactive/app-standby
 * state and force-stops it. This does not suspend the KernelSU driver, root,
 * modules, or any other application.
 */
object SleepMode {
    const val PREF_ENABLED = "manager_sleep_mode"

    private fun packageArg(context: Context): String =
        "'${context.packageName.replace("'", "'\\''")}'"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(PREF_ENABLED, false)

    fun wakeIfNeeded(context: Context): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_ENABLED, false)) return true

        // Clear the UI state immediately; restore it only if the root command fails.
        prefs.edit { putBoolean(PREF_ENABLED, false) }
        val packageArg = packageArg(context)
        val result = Shell.cmd(
            "am set-inactive $packageArg false || " +
                    "cmd package set-inactive $packageArg false"
        ).exec()
        if (!result.isSuccess) {
            prefs.edit { putBoolean(PREF_ENABLED, true) }
        }
        return result.isSuccess
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val packageArg = packageArg(context)
        val setInactive = if (enabled) "true" else "false"

        if (enabled) {
            // Persist before force-stop: the process may be killed immediately.
            prefs.edit { putBoolean(PREF_ENABLED, true) }
        }

        val inactiveResult = Shell.cmd(
            "am set-inactive $packageArg $setInactive || " +
                    "cmd package set-inactive $packageArg $setInactive"
        ).exec()
        if (!inactiveResult.isSuccess) {
            if (enabled) prefs.edit { putBoolean(PREF_ENABLED, false) }
            return false
        }

        if (enabled) {
            val stopResult = Shell.cmd("am force-stop $packageArg").exec()
            if (!stopResult.isSuccess) {
                prefs.edit { putBoolean(PREF_ENABLED, false) }
                return false
            }
        } else {
            prefs.edit { putBoolean(PREF_ENABLED, false) }
        }

        return true
    }
}
