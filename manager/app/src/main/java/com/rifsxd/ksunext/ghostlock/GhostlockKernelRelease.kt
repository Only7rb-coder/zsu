package com.rifsxd.ksunext.ghostlock

import android.system.Os

/**
 * Returns the release string used by the native payload's uname(2) check.
 *
 * Android's Java `os.version` property is not guaranteed to be the running
 * kernel release (and can be `unknown`), so it must not be used for profile
 * selection. The property is retained only as a best-effort compatibility
 * fallback for unusual test/runtime environments where uname is unavailable.
 */
internal object GhostlockKernelRelease {
    fun current(): String = runCatching { Os.uname().release }
        .getOrElse { System.getProperty("os.version", "unknown").orEmpty() }
        .trim()
        .ifBlank { "unknown" }
}
