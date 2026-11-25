package com.rifsxd.ksunext.ghostlock

import android.content.Context
import android.os.Process
import androidx.annotation.Keep
import com.ghostlock.app.data.NativeProfileDocument
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

@Keep
class GhostlockUserService(private val context: Context) : IGhostlockUserService.Stub() {
    private val running = AtomicBoolean(false)
    private val worker = AtomicReference<Thread?>()

    override fun runExploit(
        profileBlob: ByteArray,
        managerPackage: String?,
        callback: IGhostlockCallback,
        statusCallback: IGhostlockStatusCallback,
    ) {
        if (!running.compareAndSet(false, true)) {
            runCatching { callback.onLog("Another GhostLock service operation is already running") }
            runCatching { callback.onComplete(1, false) }
            return
        }
        val thread = Thread({
            var exitCode = 1
            var timedOut = false
            try {
                require(Process.myUid() == Process.SHELL_UID) {
                    "Shizuku UserService uid=${Process.myUid()}, expected ${Process.SHELL_UID}"
                }
                val processStatus = File("/proc/self/status").readText()
                require(Regex("(?m)^Seccomp:\\s*0$").containsMatchIn(processStatus)) {
                    "Shizuku UserService is still seccomp-filtered"
                }
                require(NativeProfileDocument.fromBinary(profileBlob) != null) {
                    "invalid GLK1 profile"
                }
                val binary = File(context.applicationInfo.nativeLibraryDir, "libghostlock.so")
                require(binary.isFile) { "missing GhostLock binary: ${binary.absolutePath}" }
                val ksud = GhostlockManagerScanner.findKsud(context.packageManager, managerPackage)
                require(ksud != null) { "no KernelSU manager with packaged ksud was found" }
                val workDir = File("/data/local/tmp/zsu-ghostlock")
                require(workDir.isDirectory || workDir.mkdirs()) { "cannot create ${workDir.absolutePath}" }

                runCatching {
                    callback.onLog("Shizuku ready: uid=${Process.myUid()} Seccomp=0; ksud=${ksud.absolutePath}")
                }
                val result = GhostlockProcess.run(
                    binary,
                    ksud,
                    profileBlob,
                    workDir,
                    { line -> runCatching { callback.onLog(line) } },
                    { step, status -> runCatching { statusCallback.onStatus(step, status) } },
                )
                exitCode = result.exitCode
                timedOut = result.timedOut
            } catch (error: Exception) {
                runCatching { callback.onLog("Shizuku GhostLock error: ${error.message}") }
            } finally {
                running.set(false)
                worker.set(null)
                runCatching { callback.onComplete(exitCode, timedOut) }
            }
        }, "ghostlock-shizuku-runner")
        thread.isDaemon = true
        worker.set(thread)
        try {
            thread.start()
        } catch (error: Exception) {
            worker.set(null)
            running.set(false)
            runCatching { callback.onLog("Unable to start Shizuku GhostLock: ${error.message}") }
            runCatching { callback.onComplete(1, false) }
        }
    }

    override fun destroy() {
        val active = worker.get()
        if (active != null && running.get()) {
            active.interrupt()
        } else {
            exitProcess(0)
        }
    }
}
