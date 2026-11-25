package com.rifsxd.ksunext.ghostlock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GhostlockUiContractTest {
    @Test
    fun automaticAndDirectActionsRemainDistinctFromShizuku() {
        val source = File("src/main/java/com/rifsxd/ksunext/ui/screen/Ghostlock.kt").readText()
        assertTrue(source.contains("GhostlockShizukuRunner"))
        assertTrue(source.contains("GhostlockRunner.run"))
        assertFalse(Regex("onFailure[\\s\\S]{0,200}GhostlockRunner\\.run").containsMatchIn(source))
    }

    @Test
    fun manualRunResolvesOneProfileBeforeEitherMode() {
        val source = File("src/main/java/com/rifsxd/ksunext/ui/screen/Ghostlock.kt").readText()
        val start = source.substringAfter("fun start(context: Context, mode: GhostlockExecutionMode)")
        assertTrue(start.contains("GhostlockProfileConfiguration.resolve"))
        assertTrue(start.contains("GhostlockRunner.run(appContext, profile)"))
        assertTrue(start.contains("runInterruptible"))
    }
}
