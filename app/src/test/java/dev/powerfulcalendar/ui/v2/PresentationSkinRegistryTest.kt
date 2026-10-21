package dev.powerfulcalendar.ui.v2

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * Full registration matrix: every top-level visual route can be swapped without changing
 * the event store, AI credentials, reminder scheduler or application host.
 */
class PresentationSkinRegistryTest {
    @After fun reset() { PresentationSkinRegistry.reset() }

    @Test fun productionDefaultsUseAllNativeScreens() {
        ActiveVisualSkin.install()
        assertNull(CompiledSkinRuntime.timeline)
        assertNull(PresentationSkinRegistry.month)
        assertNull(PresentationSkinRegistry.memo)
        assertNull(PresentationSkinRegistry.ai)
        assertNull(PresentationSkinRegistry.settings)
        assertNull(PresentationSkinRegistry.navigation)
        assertNull(DeckPresentationSkinRegistry.active)
        assertNull(PresentationSkinRegistry.overlay)
        assertNull(PresentationSkinRegistry.picker)
        assertNull(PresentationSkinRegistry.chrome)
    }

    @Test fun alternateSourceSkinRegistersEveryRouteAndRestoresThemAll() {
        ActiveVisualSkin.installExample()
        assertNotNull(CompiledSkinRuntime.timeline)
        assertNotNull(PresentationSkinRegistry.month)
        assertNotNull(PresentationSkinRegistry.memo)
        assertNotNull(PresentationSkinRegistry.ai)
        assertNotNull(PresentationSkinRegistry.settings)
        assertNotNull(PresentationSkinRegistry.navigation)
        assertNotNull(DeckPresentationSkinRegistry.active)
        assertNotNull(PresentationSkinRegistry.overlay)
        assertNotNull(PresentationSkinRegistry.picker)
        assertNotNull(PresentationSkinRegistry.chrome)

        val ids = listOfNotNull(
            CompiledSkinRuntime.timeline?.rendererId,
            PresentationSkinRegistry.month?.id,
            PresentationSkinRegistry.memo?.id,
            PresentationSkinRegistry.ai?.id,
            PresentationSkinRegistry.settings?.id,
            PresentationSkinRegistry.navigation?.id,
            DeckPresentationSkinRegistry.active?.id,
            PresentationSkinRegistry.overlay?.id,
            PresentationSkinRegistry.picker?.id,
            PresentationSkinRegistry.chrome?.id
        )
        assertEquals(10, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("studio-example/") })

        PresentationSkinRegistry.reset()
        assertNull(CompiledSkinRuntime.timeline)
        assertNull(PresentationSkinRegistry.month)
        assertNull(PresentationSkinRegistry.memo)
        assertNull(PresentationSkinRegistry.ai)
        assertNull(PresentationSkinRegistry.settings)
        assertNull(PresentationSkinRegistry.navigation)
        assertNull(DeckPresentationSkinRegistry.active)
        assertNull(PresentationSkinRegistry.overlay)
        assertNull(PresentationSkinRegistry.picker)
        assertNull(PresentationSkinRegistry.chrome)
    }

    @Test fun normalSkinPackageRegistryIsIndependentOfCompiledRenderers() {
        val before = PowerCalSkinRegistry.active
        ActiveVisualSkin.installExample()
        assertSame(before, PowerCalSkinRegistry.active)
        PresentationSkinRegistry.reset()
        assertSame(before, PowerCalSkinRegistry.active)
    }
}
