package com.rifsxd.ksunext.ghostlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Schedules one best-effort Ghostlock attempt after each completed boot. */
class GhostlockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            GhostlockAutoMode.scheduleAfterBoot(context.applicationContext)
        }
    }
}
