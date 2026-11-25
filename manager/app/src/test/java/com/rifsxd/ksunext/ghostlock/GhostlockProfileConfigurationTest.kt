package com.rifsxd.ksunext.ghostlock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ghostlock.app.data.NativeProfileDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
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
    fun resolverDoesNotUseLegacyJsonAssets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThrows(FileNotFoundException::class.java) {
            context.assets.open("kernel_profiles/index.json")
        }
    }
}
