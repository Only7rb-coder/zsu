package com.rifsxd.ksunext.ghostlock

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GhostlockProfileStoreTest {
    @Test
    fun importedTextIsStoredVerbatim() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = GhostlockAssetLoader(context)
        val store = GhostlockProfileStore(File(context.cacheDir, "profiles"), loader)
        val text = "release = \"example\"\nschema_version = 1\n"
        val name = store.save("profile.conf", text)
        assertEquals(text, store.rawText(name))
    }

    @Test
    fun unsafeNamesCannotEscapeProfileDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = GhostlockProfileStore(
            File(context.cacheDir, "profiles"),
            GhostlockAssetLoader(context),
        )
        val name = store.save("../../outside.conf", "release = \"example\"\n")
        assertFalse(name.contains("/") || name.contains("\\"))
        assertEquals("example", store.releasesOf(name).single())
    }
}
