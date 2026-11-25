package com.rifsxd.ksunext.ghostlock

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ghostlock.app.data.HoconSupport
import com.ghostlock.app.data.NativeProfileDocument
import com.ghostlock.app.data.asValueMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GhostlockProfileConfigurationTest {
    @Test
    fun exactReleaseProducesVersionTwoGlk1WithSelectedCpus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val release = "6.12.23-android16-5-gf1bdb13583da-ab13761046-4k"
        val resolved = GhostlockProfileConfiguration.resolve(context, release, 4, 5)
        val decoded = NativeProfileDocument.fromBinary(resolved.bytes)!!
        assertEquals(release, decoded.release)
        assertEquals(4u, decoded.execution.recommendedMainCpu)
        assertEquals(5u, decoded.execution.recommendedConsumerCpu)
        assertEquals(0x0D000721.toInt(), ByteBuffer.wrap(resolved.bytes).order(ByteOrder.LITTLE_ENDIAN).int)
        assertEquals(2.toShort(), ByteBuffer.wrap(resolved.bytes).order(ByteOrder.LITTLE_ENDIAN).getShort(4))
    }

    @Test
    fun unknownAndTemplateReleasesFailClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(GhostlockProfileConfiguration.hasProfile(context, "6.12-template"))
        assertFalse(GhostlockProfileConfiguration.hasProfile(context, "not-a-kernel"))
    }

    @Test
    fun everyBundledDeviceProfilePassesGeometryValidation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val indexText = context.assets.open("kernel_profiles/index.conf").bufferedReader().use {
            it.readText()
        }
        val index = HoconSupport.parseValue(indexText).asValueMap()!!
        val releases = (index["profiles"] as? List<*>).orEmpty().mapNotNull {
            it.asValueMap()?.get("release") as? String
        }.filterNot { it.endsWith("-template") }
        assertEquals(79, releases.size)
        for (release in releases) {
            try {
                GhostlockProfileConfiguration.resolve(context, release, 4, 5)
            } catch (error: Exception) {
                throw AssertionError("Bundled profile failed to resolve: $release", error)
            }
        }
    }

    @Test
    fun malformedSelectedImportDoesNotFallBackToBuiltin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = GhostlockProfileStore(
            File(context.filesDir, "ghostlock/user_profiles"),
            GhostlockAssetLoader(context),
        )
        val name = store.save("broken.conf", "route {\n")
        store.select(context, name)
        try {
            assertThrows(Exception::class.java) {
                GhostlockProfileConfiguration.resolve(
                    context,
                    "6.12.23-android16-5-gf1bdb13583da-ab13761046-4k",
                    4,
                    5,
                )
            }
        } finally {
            store.select(context, null)
        }
    }

    @Test
    fun selectedImportWithoutRunningReleaseDoesNotFallBackToBuiltin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = GhostlockProfileStore(
            File(context.filesDir, "ghostlock/user_profiles"),
            GhostlockAssetLoader(context),
        )
        val name = store.save("other-release.conf", "release = \"other\"\nschema_version = 1\n")
        store.select(context, name)
        try {
            assertThrows(Exception::class.java) {
                GhostlockProfileConfiguration.resolve(
                    context,
                    "6.12.23-android16-5-gf1bdb13583da-ab13761046-4k",
                    4,
                    5,
                )
            }
        } finally {
            store.select(context, null)
        }
    }

    @Test
    fun outOfRangeRouteGeometryFailsClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val release = "6.12.23-android16-5-gf1bdb13583da-ab13761046-4k"
        val loader = GhostlockAssetLoader(context)
        val text = loader.load("kernel_profiles/$release.conf")
            .replace("waiter_shift = 0", "waiter_shift = 2147483648")
        val store = GhostlockProfileStore(
            File(context.filesDir, "ghostlock/user_profiles"),
            loader,
        )
        val name = store.save("bad-geometry.conf", text)
        store.select(context, name)
        try {
            assertThrows(Exception::class.java) {
                GhostlockProfileConfiguration.resolve(context, release, 4, 5)
            }
        } finally {
            store.select(context, null)
        }
    }

    @Test
    fun resolverDoesNotUseLegacyJsonAssets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThrows(FileNotFoundException::class.java) {
            context.assets.open("kernel_profiles/index.json")
        }
    }
}
