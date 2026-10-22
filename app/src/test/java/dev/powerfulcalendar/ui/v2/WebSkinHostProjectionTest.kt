package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebSkinHostProjectionTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val noon = Instant.parse("2026-10-22T04:00:00Z").toEpochMilli()
    private val start = Instant.parse("2026-10-22T01:00:00Z").toEpochMilli()
    private fun activity(id: Long, starts: Long = start) = TemporalRecord(
        eventSeq = id, revision = 2L, kind = TemporalKind.ACTIVITY,
        title = "课程 $id", notes = "private-note-should-not-leak",
        startMillis = starts, endMillis = starts + 3_600_000L, zoneId = zone.id
    )
    private fun project(records: List<TemporalRecord>) = WebSkinTimelineProjection.build(
        records, TimelineCoordinate.Camera(noon, 24.0), noon, zone
    )

    @Test fun exposesTrueIntervalsOwnerRevisionAndNoPrivilegedContent() {
        val payload = project(listOf(activity(7)))
        val row = payload.getJSONArray("items").getJSONObject(0)
        assertEquals(7L, row.getLong("eventSeq"))
        assertEquals(2L, row.getLong("revision"))
        assertEquals(start, row.getLong("startMillis"))
        assertEquals(start + 3_600_000L, row.getLong("endMillis"))
        assertEquals("RIGHT", row.getString("side"))
        assertEquals("INSTANT", row.getString("precision"))
        assertEquals(24.0, payload.getDouble("hours"), 0.0)
        assertEquals(zone.id, payload.getString("zoneId"))
        assertFalse(payload.toString().contains("private-note-should-not-leak"))
    }

    @Test fun recurrenceUsesOccurrenceStartNotOriginalSeriesStart() {
        val startYesterday = start - 86_400_000L
        val daily = activity(9, startYesterday).copy(
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 4))
        val rows = project(listOf(daily)).getJSONArray("items")
        assertEquals(1, rows.length())
        val occurrence = rows.getJSONObject(0)
        assertEquals(start, occurrence.getLong("startMillis"))
        assertEquals(9L, occurrence.getLong("eventSeq"))
        assertTrue(occurrence.getString("occurrenceKey").startsWith("9:"))
    }

    @Test fun boundedProjectionStatesWhenClipped() {
        val events = (1..340).map { index ->
            activity(index.toLong(), start - 3_600_000L + index * 60_000L)
                .copy(endMillis = start - 3_600_000L + index * 60_000L + 30_000L)
        }
        val payload = project(events)
        assertEquals(300, payload.getJSONArray("items").length())
        assertTrue(payload.getBoolean("truncated"))
        assertEquals(300, payload.getInt("itemLimit"))
    }

    @Test fun stateFeedCoalescesAndResendsLatestOnlyOnReconnect() {
        val frames = mutableListOf<String>()
        val feed = WebSkinStateFeed()
        feed.publish(JSONObject().put("page", "timeline"))
        assertTrue(frames.isEmpty())
        feed.connect { frames += it }
        feed.publish(JSONObject().put("page", "timeline"))
        assertEquals(1, frames.size)
        feed.publish(JSONObject().put("page", "month"))
        assertEquals(2, frames.size)
        feed.disconnect()
        feed.publish(JSONObject().put("page", "ai"))
        assertEquals(2, frames.size)
        feed.connect { frames += it }
        assertEquals(3, frames.size)
        assertEquals("ai", JSONObject(frames.last()).getJSONObject("payload").getString("page"))
    }

    @Test fun rejectForgedTimelineActionsAndKeepLegacyNavigation() {
        fun decode(payload: String) = decodeBridgeAction(
            """{"version":1,"type":"action","payload":$payload}""")
        assertEquals(WebSkinAction.PanTimeline(-45),
            decode("""{"name":"panTimeline","deltaMinutes":-45}"""))
        assertEquals(WebSkinAction.JumpTimelineDay("2026-10-22"),
            decode("""{"name":"jumpTimelineDay","date":"2026-10-22"}"""))
        assertEquals(WebSkinAction.Routed("today"), decode("""{"name":"today"}"""))
        assertEquals(WebSkinAction.Routed("memo"), decode("""{"name":"memo"}"""))
        assertNull(decode("""{"name":"panTimeline","deltaMinutes":5000}"""))
        assertNull(decode("""{"name":"panTimeline","deltaMinutes":2.5}"""))
        assertNull(decode("""{"name":"panTimeline","deltaMinutes":"60"}"""))
        assertNull(decode("""{"name":"panTimeline","deltaMinutes":60,"hours":24}"""))
        assertNull(decode("""{"name":"jumpTimelineDay","date":"2026-10-32"}"""))
        assertNull(decode("""{"name":"jumpTimelineDay","date":"2500-01-01"}"""))
    }

    @Test fun siblingOptionsInheritGroupStateAndHostDisablesThemDuringLocks() {
        val sibling = SemanticOption("calendar.import.future", "新增导入", "", "table")
        val parent = SemanticOptions.groups.first()
        val group = parent.copy(items = parent.items + sibling)
        val output = SemanticOptions.toJson(enabled = false, collection = listOf(group))
        val entries = output.getJSONObject(0).getJSONArray("items")
        assertEquals(3, entries.length())
        assertEquals("calendar.import.future", entries.getJSONObject(2).getString("id"))
        assertFalse(entries.getJSONObject(2).getBoolean("enabled"))
        assertFalse(SemanticOptions.supports("calendar.import.future"))
    }
}
