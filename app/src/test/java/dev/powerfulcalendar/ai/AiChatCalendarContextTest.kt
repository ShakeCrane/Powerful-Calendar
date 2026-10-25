package dev.powerfulcalendar.ai

import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiChatCalendarContextTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val event = TemporalRecord(
        eventSeq = 72, revision = 1, kind = TemporalKind.ACTIVITY,
        title = "School course", startMillis = 1780200000000L,
        endMillis = 1780203600000L, zoneId = zone.id,
        notes = "private note must not be shared"
    )

    @Test fun ordinaryChatSharesNothingUnlessOptedIn() {
        assertNull(AiChatCalendarContext.payload(listOf(event), zone, allowed = false))
    }

    @Test fun optedInContextIncludesReadOnlyCalendarIdentityWithoutPrivateNotes() {
        val serialized = requireNotNull(AiChatCalendarContext.payload(
            listOf(event), zone, allowed = true
        ))
        assertTrue(serialized.contains("UNTRUSTED"))
        assertFalse(serialized.contains("private note"))
        val root = JSONObject(serialized.substring(serialized.indexOf('{')))
        assertTrue(root.getBoolean("readOnly"))
        assertEquals(1, root.getInt("sharedEventCount"))
        val obj = root.getJSONArray("events").getJSONObject(0)
        assertEquals(72L, obj.getLong("eventSeq"))
        assertEquals("School course", obj.getString("title"))
        assertEquals("ACTIVITY", obj.getString("kind"))
        assertFalse(obj.has("notes"))
    }

    @Test fun boundedContextReportsTruncationWithoutInventingMissingEvents() {
        val items = (1L..70L).map { event.copy(eventSeq = it) }
        val serialized = requireNotNull(AiChatCalendarContext.payload(
            items, zone, allowed = true
        ))
        val obj = JSONObject(serialized.substring(serialized.indexOf('{')))
        assertEquals(60, obj.getInt("sharedEventCount"))
        assertTrue(obj.getBoolean("truncatedAt60"))
        assertEquals(60, obj.getJSONArray("events").length())
    }
}
