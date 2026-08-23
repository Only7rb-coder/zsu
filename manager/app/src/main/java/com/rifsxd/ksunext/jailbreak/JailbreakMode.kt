package com.rifsxd.ksunext.jailbreak

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.rifsxd.ksunext.ui.util.getSelinuxEnforce

/**
 * Manages the explicit, opt-in late-load flow. The service runs in an app zygote
 * so its preload bridge can invoke the manager-bundled `ksud late-load` command.
 */
object JailbreakMode {
    private const val PREFS_NAME = "settings"
    private const val PREF_AUTO_JAILBREAK = "auto_jailbreak"

    fun start(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || getSelinuxEnforce() != false) return false
        return runCatching {
            context.startService(Intent(context, JailbreakService::class.java))
            true
        }.getOrDefault(false)
    }

    fun isAutoEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_AUTO_JAILBREAK, false)

    fun setAutoEnabled(context: Context, enabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_AUTO_JAILBREAK, enabled)
                .apply()
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, JailbreakBootReceiver::class.java),
                state,
                PackageManager.DONT_KILL_APP
            )
            true
        }.getOrDefault(false)
    }
}
