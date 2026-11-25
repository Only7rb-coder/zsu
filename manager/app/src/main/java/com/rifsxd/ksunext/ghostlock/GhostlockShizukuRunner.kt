package com.rifsxd.ksunext.ghostlock

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import com.rifsxd.ksunext.BuildConfig
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Runs one native attempt through Shizuku; only pre-dispatch connections may retry. */
class GhostlockShizukuRunner(private val context: Context) {
    data class Result(val exitCode: Int, val timedOut: Boolean)

    private sealed interface ConnectionResult {
        data class Completed(val value: Result) : ConnectionResult
        data object Retryable : ConnectionResult
    }

    fun run(
        profile: ByteArray,
        managerPackage: String?,
        onLog: (String) -> Unit,
        onStatus: (String, String) -> Unit,
    ): Result {
        val permit = GhostlockOperationLock.tryAcquire() ?: run {
            onLog("Another GhostLock operation is already running")
            return Result(1, false)
        }
        permit.use {
            if (!ready(onLog)) return Result(1, false)
            repeat(3) { attempt ->
                when (val result = connectAndRunOnce(profile, managerPackage, onLog, onStatus)) {
                    is ConnectionResult.Completed -> return result.value
                    ConnectionResult.Retryable -> {
                        if (attempt < 2) onLog("Retrying Shizuku UserService connection")
                    }
                }
            }
            onLog("Shizuku UserService did not connect after 3 attempts")
            return Result(1, false)
        }
    }

    fun requestPermission() {
        runCatching {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(PermissionRequestCode)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun ready(onLog: (String) -> Unit): Boolean {
        val failure = runCatching {
            when {
                !Shizuku.pingBinder() -> "Shizuku is not running"
                Shizuku.getUid() != Process.SHELL_UID -> "Shizuku is not running as shell"
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                    "Shizuku permission has not been granted"
                else -> null
            }
        }.getOrElse { "Shizuku is unavailable: ${it.message}" }
        if (failure != null) onLog(failure)
        return failure == null
    }

    private fun connectAndRunOnce(
        profile: ByteArray,
        managerPackage: String?,
        onLog: (String) -> Unit,
        onStatus: (String, String) -> Unit,
    ): ConnectionResult {
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, GhostlockUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("ghostlock")
            .debuggable(false)
            .version(BuildConfig.VERSION_CODE)
        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val terminal = AtomicBoolean(false)
        val connected = AtomicBoolean(false)
        val nativeDispatched = AtomicBoolean(false)
        val unbound = AtomicBoolean(false)
        val outcome = AtomicReference<ConnectionResult>()
        val service = AtomicReference<IGhostlockUserService?>()

        fun complete(result: ConnectionResult) {
            if (terminal.compareAndSet(false, true)) {
                outcome.set(result)
                latch.countDown()
            }
        }

        val timeout = Runnable {
            if (!connected.get()) {
                onLog("Shizuku UserService did not connect within 3 seconds")
                complete(ConnectionResult.Retryable)
            }
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (terminal.get()) return
                connected.set(true)
                handler.removeCallbacks(timeout)
                onLog("Shizuku UserService connected")
                val remote = IGhostlockUserService.Stub.asInterface(binder)
                service.set(remote)
                val logSink = onLog
                val statusSink = onStatus
                val callback = object : IGhostlockCallback.Stub() {
                    override fun onLog(line: String) {
                        runCatching { logSink(line) }
                    }

                    override fun onComplete(exitCode: Int, timedOut: Boolean) {
                        complete(ConnectionResult.Completed(Result(exitCode, timedOut)))
                    }
                }
                val statusCallback = object : IGhostlockStatusCallback.Stub() {
                    override fun onStatus(step: String, status: String) {
                        runCatching { statusSink(step, status) }
                    }
                }
                /* Binder failure after dispatch is ambiguous: the native process may
                 * already be active, so it is a terminal failure, never a retry. */
                nativeDispatched.set(true)
                try {
                    remote.runExploit(profile, managerPackage, callback, statusCallback)
                } catch (error: Exception) {
                    onLog("Shizuku native dispatch failed: ${error.message}")
                    complete(ConnectionResult.Completed(Result(1, false)))
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                disconnected(onLog, nativeDispatched.get(), ::complete)
            }

            override fun onBindingDied(name: ComponentName) {
                disconnected(onLog, nativeDispatched.get(), ::complete)
            }

            override fun onNullBinding(name: ComponentName) {
                disconnected(onLog, nativeDispatched.get(), ::complete)
            }
        }

        try {
            onLog("Binding Shizuku UserService")
            try {
                Shizuku.bindUserService(args, connection)
            } catch (error: Exception) {
                onLog("Cannot bind Shizuku UserService: ${error.message}")
                complete(ConnectionResult.Retryable)
            }
            if (!terminal.get()) handler.postDelayed(timeout, ConnectTimeoutMs)
            try {
                latch.await()
            } catch (_: InterruptedException) {
                if (nativeDispatched.get()) runCatching { service.get()?.destroy() }
                complete(ConnectionResult.Completed(Result(1, false)))
                Thread.currentThread().interrupt()
            }
        } finally {
            handler.removeCallbacks(timeout)
            if (unbound.compareAndSet(false, true)) {
                runCatching { Shizuku.unbindUserService(args, connection, true) }
            }
        }
        return outcome.get() ?: ConnectionResult.Completed(Result(1, false))
    }

    private fun disconnected(
        onLog: (String) -> Unit,
        nativeDispatched: Boolean,
        complete: (ConnectionResult) -> Unit,
    ) {
        onLog(
            if (nativeDispatched) "Shizuku disconnected after native dispatch; not retrying"
            else "Shizuku UserService disconnected before native dispatch",
        )
        complete(
            if (nativeDispatched) ConnectionResult.Completed(Result(1, false))
            else ConnectionResult.Retryable,
        )
    }

    private companion object {
        const val ConnectTimeoutMs = 3_000L
        const val PermissionRequestCode = 4815
    }
}
