package com.rifsxd.ksunext.ghostlock

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File

/** Resolves KernelSU-family manager daemons without broad package visibility. */
object GhostlockManagerScanner {
    private const val PreferencesName = "ghostlock_settings"
    private const val ManagerPreference = "manager_package"

    val Builtin = listOf(
        "com.zsu.zsu",
        "me.weishu.kernelsu.pr",
        "me.weishu.kernelsu",
        "com.sukisu.ultra.pr",
        "com.sukisu.ultra",
        "com.resukisu.resukisu",
        "org.bakasu.bakasu",
        "com.kowx712.supermanager",
    )

    data class Candidate(
        val packageName: String,
        val label: String,
        val hasKsud: Boolean,
    )

    @Suppress("DEPRECATION")
    fun scan(packageManager: PackageManager): List<Candidate> = Builtin.mapNotNull { packageName ->
        runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()?.let { info ->
            Candidate(
                packageName = packageName,
                label = info.loadLabel(packageManager).toString(),
                hasKsud = ksud(info).isFile,
            )
        }
    }

    fun selected(context: Context): String? =
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getString(ManagerPreference, null)?.takeIf(String::isNotBlank)

    fun select(context: Context, packageName: String?) {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE).edit().apply {
            if (packageName.isNullOrBlank()) remove(ManagerPreference)
            else putString(ManagerPreference, packageName)
        }.commit()
    }

    /** Chosen package first, then ZSU and the remaining built-in managers. */
    fun resolvePackages(chosen: String?): List<String> =
        (listOfNotNull(chosen?.takeIf(String::isNotBlank)) + Builtin).distinct()

    fun findKsud(packageManager: PackageManager, chosen: String?): File? =
        resolvePackages(chosen).firstNotNullOfOrNull { packageName ->
            runCatching { packageManager.getApplicationInfo(packageName, 0) }
                .getOrNull()?.let(::ksud)?.takeIf(File::isFile)
        }

    private fun ksud(info: ApplicationInfo): File = File(info.nativeLibraryDir, "libksud.so")
}
