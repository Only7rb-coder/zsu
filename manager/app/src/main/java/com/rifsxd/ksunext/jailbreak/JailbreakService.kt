package com.rifsxd.ksunext.jailbreak

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Empty isolated service used solely to create the app zygote and execute its
 * configured preload callback. The preload owns the late-load process.
 */
class JailbreakService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
