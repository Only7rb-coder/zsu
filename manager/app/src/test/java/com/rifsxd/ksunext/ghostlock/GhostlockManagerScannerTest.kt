package com.rifsxd.ksunext.ghostlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GhostlockManagerScannerTest {
    @Test
    fun zsuIsFirstAndExplicitChoiceWins() {
        assertEquals("com.zsu.zsu", GhostlockManagerScanner.resolvePackages(null).first())
        assertEquals(
            "org.bakasu.bakasu",
            GhostlockManagerScanner.resolvePackages("org.bakasu.bakasu").first(),
        )
        assertEquals(
            "com.zsu.zsu",
            GhostlockManagerScanner.resolvePackages("org.bakasu.bakasu")[1],
        )
    }

    @Test
    fun duplicateManagersAreRemoved() {
        val packages = GhostlockManagerScanner.resolvePackages("com.zsu.zsu")
        assertEquals(packages.size, packages.distinct().size)
    }

    @Test
    fun shizukuFailureNeverFallsBackToDirect() {
        val source = File("src/main/java/com/rifsxd/ksunext/ghostlock/GhostlockShizukuRunner.kt").readText()
        assertFalse(source.contains("GhostlockRunner.run"))
        assertTrue(source.contains("GhostlockOperationLock.tryAcquire()"))
    }

    @Test
    fun completionPreservesNativeTimeout() {
        val source = File("src/main/aidl/com/rifsxd/ksunext/ghostlock/IGhostlockCallback.aidl").readText()
        assertTrue(source.contains("onComplete(int exitCode, boolean timedOut)"))
    }
}
