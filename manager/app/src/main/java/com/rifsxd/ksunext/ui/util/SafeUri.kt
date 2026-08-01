package com.rifsxd.ksunext.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * ZSU: open an external link without ever crashing the app.
 * The stock uriHandler.openUri() throws ActivityNotFoundException when no
 * app on the device can handle the URL (e.g. debloated browser), killing
 * the app. This wrapper falls back to showing the URL in a toast instead.
 */
fun safeOpenUri(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, url, Toast.LENGTH_LONG).show()
    }
}
