package com.rifsxd.ksunext.ui.util

import android.content.Context
import android.content.res.Configuration

/**
 * Persists and applies a display-density override to the ZSU manager process.
 *
 * This deliberately changes only the manager's configuration context; it does
 * not run the global `wm density` command and therefore does not affect other
 * applications or the device-wide display density.
 */
object AppDensity {
    const val PREF_KEY = "app_density_dpi"

    fun configuredDpi(context: Context): Int? {
        val value = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getInt(PREF_KEY, 0)
        return value.takeIf { it > 0 }
    }

    fun currentDpi(context: Context): Int = context.resources.configuration.densityDpi

    fun apply(context: Context): Context {
        val dpi = configuredDpi(context) ?: return context
        val configuration = Configuration(context.resources.configuration).apply {
            densityDpi = dpi
        }
        return context.createConfigurationContext(configuration)
    }
}
