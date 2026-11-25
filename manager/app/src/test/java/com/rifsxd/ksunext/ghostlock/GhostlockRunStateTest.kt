package com.rifsxd.ksunext.ghostlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GhostlockRunStateTest {
    @Test
    fun stuckStepUsesUpstreamOrder() {
        val file = createTempFile("ghostlock-state", ".json")
        val state = GhostlockRunState(file)
        state.reset()
        state.apply("w2a", "in_progress")
        state.apply("w1a", "in_progress")
        assertEquals("w1a", state.stuckStep())
        file.delete()
    }

    @Test
    fun clearDeletesPersistedState() {
        val file = createTempFile("ghostlock-state", ".json")
        val state = GhostlockRunState(file)
        state.reset()
        state.apply("w1a", "in_progress")
        state.clear()
        assertFalse(file.exists())
        assertNull(state.stuckStep())
    }
}
