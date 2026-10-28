package com.rifsxd.ksunext.ui.util

import com.rifsxd.ksunext.ksuApp
import java.io.File

private const val DAEMON_NAME = "libzsu.so"

/** Returns the bundled userspace daemon for normal and generated clone APKs. */
fun getKsuDaemonFile(): File {
    val libraryDir = File(ksuApp.applicationInfo.nativeLibraryDir)
    return File(libraryDir, DAEMON_NAME)
}
