package com.rifsxd.ksunext.ui.util

import com.rifsxd.ksunext.ksuApp
import java.io.File

private const val NORMAL_DAEMON_NAME = "libksud.so"
private const val DISGUISED_DAEMON_NAME = "libzsu.so"

/** Returns the bundled userspace daemon for normal and generated clone APKs. */
fun getKsuDaemonFile(): File {
    val libraryDir = File(ksuApp.applicationInfo.nativeLibraryDir)
    return sequenceOf(NORMAL_DAEMON_NAME, DISGUISED_DAEMON_NAME)
        .map { File(libraryDir, it) }
        .firstOrNull(File::isFile)
        ?: File(libraryDir, NORMAL_DAEMON_NAME)
}
