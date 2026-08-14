package com.rifsxd.ksunext.ui.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Reapplies Reset Props after boot when the user left the setting enabled. */
class ResetPropsBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!preferences.getBoolean(ResetProps.PREF_ENABLED, false)) return

        val pendingResult = goAsync()
        Thread {
            try {
                val result = ResetProps.run()
                Log.i(
                    TAG,
                    "Boot Reset Props finished: success=${result.success}, " +
                        "changed=${result.changed}, failed=${result.failed}, " +
                        "diagnostic=${result.diagnostic}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Boot Reset Props failed", e)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "ResetPropsBoot"
    }
}
