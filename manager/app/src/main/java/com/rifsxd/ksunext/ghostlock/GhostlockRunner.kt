package com.rifsxd.ksunext.ghostlock

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Runs the manager-bundled GhostLock payload using the upstream JSON profile contract. */
object GhostlockRunner {
    private const val BINARY_NAME = "libghostlock.so"
    private const val KSUD_NAME = "libksud.so"
    private const val WORK_DIR_NAME = "ghostlock"
    private const val LOG_NAME = ".ghostlock_ksu.log"
    private const val PROFILE_NAME = "active-profile.json"
    private const val TIMEOUT_SECONDS = 90L

    /** Exact uname -r values represented by the bundled upstream GhostLock catalog. */
    val supportedKernels: Set<String> = setOf(
        "5.15.189-android13-8-00016-g51bba4309aac-ab14546557",
        "6.1.115-android14-11-ga2521ca27699-ab13294383",
        "6.1.118-android14-11-ga3b9c44908dd-ab13320413",
        "6.1.118-android14-11-gca0ef6d17716-ab13624819",
        "6.1.138-android14-11-g0c3d559bcd85-ab14529422",
        "6.1.138-android14-11-g44bda9e8f6e9-ab13792638",
        "6.1.138-android14-11-g6ab8c9a86a33-ab14396278",
        "6.1.138-android14-11-g965475777129-mi",
        "6.1.145-android14-11-g09f1c0074ad7-ab14226177",
        "6.1.145-android14-11-g74d1702dab4d-ab14669069",
        "6.1.145-android14-11-geaa643a2c0ee-ab14763719",
        "6.1.162-android14-11-gce140c0e5bf5-ab15450923",
        "6.12.23-android16-5-g16e473de48a3-abogki462654244-4k",
        "6.12.23-android16-5-g75e9b1c7ae7c-abogki463945075-4k",
        "6.12.23-android16-5-g82efd98459a2-ab14457512-4k",
        "6.12.23-android16-5-ga8f88ad96df3-ab13929693-4k",
        "6.12.23-android16-5-gb2a876903b49-ab14541642-4k",
        "6.12.23-android16-5-gf1bdb13583da-ab13761046-4k",
        "6.12.30-android16-5-g6e872b4863d6-ab13847919-4k",
        "6.12.38-android16-5-g1d46253471dd-ab15048002-4k",
        "6.12.38-android16-5-g3c4da6410bcb-ab13872285-4k",
        "6.12.38-android16-5-g74ad46052215-ab14494108-4k",
        "6.12.38-android16-5-g844001fb8721-ab14552068-4k",
        "6.6.102-android15-8-gab8eb70a71b8-ab14350911-4k",
        "6.6.102-android15-8-gb01b41c2647c-ab15574720-4k",
        "6.6.102-android15-8-gfe76d1bc97fd-ab14689815-4k",
        "6.6.118-android15-8-g2e6b9c3812c5-ab15114928-4k",
        "6.6.118-android15-8-g608a629fedf7-ab15154340-4k",
        "6.6.118-android15-8-g93e223c276e7-abogki500782043-4k",
        "6.6.118-android15-8-gbf8cd367de7a-ab15314822-4k",
        "6.6.118-android15-8-gc44b714366cc-abogki519650608-4k",
        "6.6.118-android15-8-ge56cf6b09cca-ab15511674-4k",
        "6.6.118-android15-8-ge58033dc8ea6-abogki498046332-4k",
        "6.6.118-android15-8-gebdfad32d749-ab15099304-4k",
        "6.6.30-android15-8-g54dcbfbef792-ab12368803-4k",
        "6.6.77-android15-8-g4a507830d890-ab13636293-4k",
        "6.6.77-android15-8-g63ce7556864c-ab13994517-4k",
        "6.6.77-android15-8-gca30f3b4bef6-abogki440974771-4k",
        "6.6.89-android15-8-g0889fe95bb10-ab14402178-4k",
        "6.6.89-android15-8-g096cdb6ecefc-ab14358676-4k",
        "6.6.89-android15-8-g42db9ecb036b-ab14487600-4k",
        "6.6.89-android15-8-g8e4be6b47e40-ab14134548-4k",
        "6.6.89-android15-8-gb99b4586a3ee-ab13754593-4k",
        "6.6.89-android15-8-gf4dc45704e54-abogki446052083-4k",
        "6.6.92-android15-8-g3637f4904cf5-ab13944661-4k"
    )

    data class Result(val success: Boolean, val timedOut: Boolean, val output: String)
    fun isKernelSupported(release: String): Boolean = supportedKernels.contains(release)

    private fun runOnce(context: Context): Result {
        val workDir = File(context.filesDir, WORK_DIR_NAME)
        if (!workDir.exists() && !workDir.mkdirs()) return Result(false, false, "Unable to create GhostLock working directory")
        val packagedBinary = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)
        if (!packagedBinary.isFile) return Result(false, false, "GhostLock payload is not present in this APK")
        val release = System.getProperty("os.version", "").orEmpty()
        if (!isKernelSupported(release)) return Result(false, false, "No bundled GhostLock profile for $release")
        val profile = try {
            GhostlockProfileConfiguration.resolve(context, release)
        } catch (error: Exception) {
            return Result(false, false, "Unable to resolve GhostLock profile: ${error.message}")
        }
        val profileFile = File(workDir, PROFILE_NAME)
        profileFile.writeText(profile)
        val packagedKsud = File(context.applicationInfo.nativeLibraryDir, KSUD_NAME)
        if (!packagedKsud.isFile) return Result(false, false, "ZSU ksud is not present in this APK")
        val output = StringBuilder()
        val process = try {
            ProcessBuilder(packagedBinary.absolutePath, "--profile", profileFile.absolutePath)
                .directory(workDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["GHOSTLOCK_HOME"] = workDir.absolutePath
                    environment()["TMPDIR"] = workDir.absolutePath
                    environment()["HOME"] = workDir.absolutePath
                    environment()["GHOSTLOCK_KSUD"] = packagedKsud.absolutePath
                }
                .start()
        } catch (error: IOException) {
            return Result(false, false, "Unable to start GhostLock: ${error.message}")
        }
        val reader = thread(start = true, name = "ghostlock-output-reader", isDaemon = true) {
            try { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line -> synchronized(output) { output.append(line).append('\n') } } } }
            catch (_: IOException) {}
        }
        val finished = try { process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        catch (error: InterruptedException) { Thread.currentThread().interrupt(); process.destroyForcibly(); false }
        if (!finished) { process.destroy(); if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly() }
        reader.join(3000)
        val ksuLog = File(workDir, LOG_NAME)
        if (ksuLog.isFile) synchronized(output) { output.append("\n--- KernelSU handoff log ---\n").append(runCatching { ksuLog.readText() }.getOrDefault("")) }
        val exitCode = if (finished) process.exitValue() else -1
        val finalOutput = synchronized(output) { output.toString().trim() }
        val prefix = if (!finished) "GhostLock timed out after $TIMEOUT_SECONDS seconds" else "GhostLock exited with code $exitCode"
        return Result(finished && exitCode == 0, !finished, listOf(prefix, finalOutput).filter { it.isNotBlank() }.joinToString("\n"))
    }

    fun run(context: Context): Result {
        val first = runOnce(context)
        if (first.success || first.timedOut) return first
        Thread.sleep(750L)
        val second = runOnce(context)
        return if (second.success) second.copy(output = "First attempt failed; controlled retry succeeded.\n${second.output}")
        else second.copy(output = "First attempt failed; controlled retry also failed.\n${second.output}")
    }
}
