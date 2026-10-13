package com.rifsxd.ksunext.ui.util

import android.content.Context

/** User-selected behavior for the Home screen's plain Reboot action. */
object RebootMode {
    const val PREF_ENABLED = "force_soft_reboot"

    fun isForceSoftRebootEnabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(PREF_ENABLED, false)
}
