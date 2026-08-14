package com.rifsxd.ksunext.ui.util

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.rifsxd.ksunext.ksuApp
import com.rifsxd.ksunext.ui.util.module.LatestVersionInfo

/**
 * @author weishu
 * @date 2023/6/22.
 */
@SuppressLint("Range")
fun download(
    context: Context,
    url: String,
    fileName: String,
    description: String,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    onFailed: (Int) -> Unit = {}
): Long {
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val query = DownloadManager.Query()
    query.setFilterByStatus(
        DownloadManager.STATUS_RUNNING or
            DownloadManager.STATUS_PAUSED or
            DownloadManager.STATUS_PENDING or
            DownloadManager.STATUS_SUCCESSFUL or
            DownloadManager.STATUS_FAILED
    )
    downloadManager.query(query).use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
            val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
            val localUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val columnTitle = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
            if (url == uri || fileName == columnTitle) {
                when (status) {
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_PAUSED -> {
                        onDownloading()
                        return id
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        if (!localUri.isNullOrBlank()) onDownloaded(localUri.toUri())
                        return id
                    }
                    DownloadManager.STATUS_FAILED -> {
                        onFailed(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
                        return id
                    }
                }
            }
        }
    }

    val request = DownloadManager.Request(url.toUri())
        .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            fileName
        )
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setMimeType("application/zip")
        .setTitle(fileName)
        .setDescription(description)

    return downloadManager.enqueue(request)
}

fun installDownloadedApk(context: Context, uri: Uri): Boolean {
    val apk = File(context.cacheDir, "zsu-manager-update.apk")
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            apk.outputStream().use { output -> input.copyTo(output) }
        } ?: return false
        DisguiseEngine.installViaRoot(apk)
    } catch (_: Exception) {
        false
    } finally {
        apk.delete()
    }
}

fun checkNewVersion(preferSpoofed: Boolean? = null): LatestVersionInfo {
    // Next version updates
    val url = "https://api.github.com/repos/Only7rb-coder/zsu/releases/latest"
    // default null value if failed
    val defaultValue = LatestVersionInfo()
    runCatching {
        ksuApp.okhttpClient.newCall(okhttp3.Request.Builder().url(url).build()).execute()
            .use { response ->
                if (!response.isSuccessful) {
                    return defaultValue
                }
                val body = response.body?.string() ?: return defaultValue
                val json = org.json.JSONObject(body)
                val changelog = json.optString("body")
                val versionTag = json.optString("tag_name", "")

                val assets = json.getJSONArray("assets")

                val mainApk = mutableListOf<Triple<String, String, String>>()
                val spoofedApk = mutableListOf<Triple<String, String, String>>()
                
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    val isApk = name.endsWith(".apk")
                    if (!isApk) continue

                    // ZSU release assets are named ZSU_<versionName>_<versionCode>-release.apk.
                    // The spoofed release asset may have an additional -spoofed suffix.
                    val regex = Regex("^ZSU_(.+?)_(\\d+)-release(?:-spoofed)?\\.apk$")
                    val matchResult = regex.matchEntire(name) ?: continue
                    val versionName = matchResult.groupValues[1]
                    val versionCode = matchResult.groupValues[2]
                    val downloadUrl = asset.getString("browser_download_url")
                    
                    val isSpoofed = name.contains("spoofed", ignoreCase = true)
                    val apkInfo = Triple(versionName, versionCode, downloadUrl)
                    
                    if (isSpoofed) {
                        spoofedApk.add(apkInfo)
                    } else {
                        mainApk.add(apkInfo)
                    }
                }
                
                val selectedApk = when (preferSpoofed) {
                    true -> spoofedApk.firstOrNull() ?: mainApk.firstOrNull()
                    false -> mainApk.firstOrNull() ?: spoofedApk.firstOrNull()
                    null -> mainApk.firstOrNull() ?: spoofedApk.firstOrNull() // Default to main
                }
                
                if (selectedApk != null) {
                    val versionCode = selectedApk.second.toInt()
                    val downloadUrl = selectedApk.third
                    return LatestVersionInfo(
                        versionCode,
                        downloadUrl,
                        changelog,
                        versionTag
                    )
                }
            }
    }
    return defaultValue
}

@Composable
fun DownloadListener(
    context: Context,
    onDownloaded: (Uri) -> Unit,
    downloadId: Long? = null,
    onFailed: (Int) -> Unit = {}
) {
    DisposableEffect(context, downloadId) {
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("Range")
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val id = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, -1
                    )
                    if (downloadId != null && id != downloadId) return
                    val query = DownloadManager.Query().setFilterById(id)
                    val downloadManager =
                        context?.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(
                            cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        )
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val uri = cursor.getString(
                                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                                )
                                if (!uri.isNullOrBlank()) onDownloaded(uri.toUri())
                            }
                            DownloadManager.STATUS_FAILED -> {
                                onFailed(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
                            }
                        }
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}
