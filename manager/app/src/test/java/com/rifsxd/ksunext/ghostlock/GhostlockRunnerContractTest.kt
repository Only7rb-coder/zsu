package com.rifsxd.ksunext.ghostlock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class GhostlockRunnerContractTest {
    @Test
    fun directRunUsesZsuDaemonAndAppCall() {
        val source = File("src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockRunner.kt").readText()
        val process = File("src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockProcess.kt").readText()
        assertTrue(source.contains("getKsuDaemonFile()"))
        assertTrue(process.contains("\"--ghostlock-app-call\""))
        assertFalse(source.contains("GHOSTLOCK_CORE"))
        assertFalse(source.contains("GHOSTLOCK_CONSUMER_CORE"))
        assertFalse(source.contains("\"--profile\""))
    }

    @Test
    fun failedRunHasNoRetryLoop() {
        val runner = File("src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockRunner.kt").readText()
        val process = File("src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockProcess.kt").readText()
        assertFalse(runner.contains("runOnce(context"))
        assertFalse(Regex("for\\s*\\(.*attempt").containsMatchIn(process))
    }

    @Test
    fun operationLockExcludesAnotherThread() {
        val permit = GhostlockOperationLock.tryAcquire()
        assertNotNull(permit)
        val acquired = AtomicReference<Closeable?>(null)
        val thread = Thread {
            acquired.set(GhostlockOperationLock.tryAcquire())
        }
        thread.start()
        thread.join()
        assertNull(acquired.get())
        permit!!.close()
        permit.close()
        val afterRelease = GhostlockOperationLock.tryAcquire()
        assertNotNull(afterRelease)
        afterRelease!!.close()
    }
}
