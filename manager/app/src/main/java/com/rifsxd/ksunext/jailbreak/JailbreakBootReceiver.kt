package com.rifsxd.ksunext.jailbreak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the app-zygote late-load service only after the user explicitly enables Auto Jailbreak. */
class JailbreakBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || !JailbreakMode.isAutoEnabled(context)) return
        JailbreakMode.start(context)
    }
}
