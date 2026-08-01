package com.rifsxd.ksunext.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * ZSU: open an external link without ever crashing the app.
 * The stock uriHandler.openUri() throws ActivityNotFoundException when no
 * app on the device can handle the URL (e.g. debloated browser), killing
 * the app. On failure we copy the link to the clipboard and show why.
 */
fun safeOpenUri(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("link", url))
            Toast.makeText(
                context,
                "No app could open the link - copied to clipboard\n(${e.javaClass.simpleName})",
                Toast.LENGTH_LONG
            ).show()
        } catch (e2: Exception) {
            Toast.makeText(context, url, Toast.LENGTH_LONG).show()
        }
    }
}
