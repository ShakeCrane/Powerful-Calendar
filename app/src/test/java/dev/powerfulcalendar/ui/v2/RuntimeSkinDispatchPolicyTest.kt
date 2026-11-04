package dev.powerfulcalendar.ui.v2

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The legacy parser remains testable while no v2 screen is reachable from product dispatch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RuntimeSkinDispatchPolicyTest {
    @Before fun reset() {
        RuntimeSkinSurfaces.resetV2CompatibilityForTests()
        PowerCalSkinRegistry.reset()
    }

    @After fun cleanup() {
        RuntimeSkinSurfaces.resetV2CompatibilityForTests()
        PowerCalSkinRegistry.reset()
    }

    @Test fun v2PackageIsParsedButDailyDispatchRequiresExplicitTestOptIn() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val sample = listOf(
            File(root, "tools/skin-studio/examples/Nebula-Flux-v2.pcskin"),
            File(root, "../tools/skin-studio/examples/Nebula-Flux-v2.pcskin")
        ).firstOrNull { it.isFile }
        assertTrue("Missing checked-in v2 compatibility fixture", sample?.isFile == true)
        val manager = SkinPackageManager(ApplicationProvider.getApplicationContext())
        val decoded = manager.decode(sample!!.readBytes(), false)
        assertEquals(10, decoded.pack.runtime?.screens?.size)
        PowerCalSkinRegistry.activate(decoded.pack)

        for (screen in listOf("timeline", "month", "memo", "ai", "settings", "deck",
            "navigation", "overlay", "chrome", "picker")) {
            assertFalse("v2 $screen must not be a normal product surface",
                RuntimeSkinSurfaces.enabled(screen))
        }

        RuntimeSkinSurfaces.enableV2CompatibilityForTests()
        assertTrue(RuntimeSkinSurfaces.enabled("timeline"))
        assertTrue(RuntimeSkinSurfaces.enabled("picker"))
    }
}
