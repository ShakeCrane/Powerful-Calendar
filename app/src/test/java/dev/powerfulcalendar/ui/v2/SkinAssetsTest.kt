package dev.powerfulcalendar.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SkinAssetsTest {
    @Test fun defaultSkinDoesNotReplaceExistingUi() {
        val skin = PowerCalSkinPack()
        SkinSlot.entries.forEach { slot ->
            assertNull(skin.artworkFor(false).find(slot))
            assertNull(skin.artworkFor(true).find(slot))
        }
        assertNull(skin.lightColors)
        assertNull(skin.darkColors)
        assertNull(skin.lightAxis)
        assertNull(skin.darkAxis)
    }

    @Test fun allNamedSlotsResolveToIndependentArtwork() {
        val arts = SkinArtwork(
            timelineBackground = SkinArt(101, 0.15f),
            deckBackdrop = SkinArt(102, 0.25f),
            monthBackground = SkinArt(103, 0.30f),
            completionEmblem = SkinArt(104),
            brandMark = SkinArt(105)
        )
        assertEquals(101, arts.find(SkinSlot.TIMELINE_BACKGROUND)?.drawableId)
        assertEquals(102, arts.find(SkinSlot.DECK_BACKDROP)?.drawableId)
        assertEquals(103, arts.find(SkinSlot.MONTH_BACKGROUND)?.drawableId)
        assertEquals(104, arts.find(SkinSlot.COMPLETION_EMBLEM)?.drawableId)
        assertEquals(105, arts.find(SkinSlot.BRAND_MARK)?.drawableId)
        assertEquals(0.15f, arts.find(SkinSlot.TIMELINE_BACKGROUND)!!.alpha)
    }

    @Test fun lightAndDarkArtworkRemainIndependent() {
        val light = SkinArtwork(brandMark=SkinArt(42))
        val dark = SkinArtwork(brandMark=SkinArt(43))
        val pack = PowerCalSkinPack(key="test",lightArtwork=light,darkArtwork=dark)
        assertSame(light,pack.artworkFor(false))
        assertSame(dark,pack.artworkFor(true))
        assertEquals(42,pack.artworkFor(false).brandMark?.drawableId)
        assertEquals(43,pack.artworkFor(true).brandMark?.drawableId)
    }

    @Test fun malformedAssetsAreRejectedAtRegistration() {
        assertThrows(IllegalArgumentException::class.java) { SkinArt(0) }
        assertThrows(IllegalArgumentException::class.java) { SkinArt(1,-0.01f) }
        assertThrows(IllegalArgumentException::class.java) { SkinArt(1,1.01f) }
        assertThrows(IllegalArgumentException::class.java) { PowerCalSkinPack(key="  ".trim()) }
    }

    @Test fun registryCanSwitchWithoutPersistingAnUnsafeExternalPath() {
        val pack = PowerCalSkinPack("claude-sample", lightArtwork=SkinArtwork(brandMark=SkinArt(23)))
        try {
            PowerCalSkinRegistry.activate(pack)
            assertSame(pack, PowerCalSkinRegistry.active)
            assertNotNull(PowerCalSkinRegistry.active.artworkFor(false).brandMark)
        } finally {
            PowerCalSkinRegistry.reset()
        }
        assertSame(PowerCalSkinRegistry.default,PowerCalSkinRegistry.active)
    }
}
