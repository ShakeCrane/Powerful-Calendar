package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

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

    @Test fun skinResourceMimeTypesIgnoreExtensionCaseLikeStudioPreview() {
        assertEquals("font/woff2", webSkinMimeType("web/fonts/Probe.WOFF2"))
        assertEquals("font/woff", webSkinMimeType("web/fonts/Probe.WOFF"))
        assertEquals("font/ttf", webSkinMimeType("assets/Probe.TTF"))
        assertEquals("application/javascript", webSkinMimeType("web/App.JS"))
        assertEquals("application/octet-stream", webSkinMimeType("assets/Probe.bin"))
    }

    @Test fun skinSuccessAndFailureResponsesSharePreviewSecurityHeaders() {
        assertEquals(
            mapOf(
                "Content-Security-Policy" to WEB_SKIN_CSP,
                "X-Content-Type-Options" to "nosniff",
                "Cache-Control" to "no-store",
                "Content-Length" to "123"
            ),
            webSkinResponseHeaders(123)
        )
        assertEquals("0", webSkinResponseHeaders(0)["Content-Length"])
    }

    @Test fun aiAttachmentPickerActionsArePayloadFreeAndClosedSchema() {
        fun decode(payload: String) = decodeBridgeAction(
            """{"version":1,"type":"action","payload":$payload}""")

        assertEquals(WebSkinAction.PickAiFile, decode("""{"name":"pickAiFile"}"""))
        assertEquals(WebSkinAction.PickAiImage, decode("""{"name":"pickAiImage"}"""))
        assertNull(decode("""{"name":"pickAiFile","uri":"content://private"}"""))
        assertNull(decode("""{"name":"pickAiImage","mime":"image/png"}"""))
    }

    @Test fun exposesTrueIntervalsOwnerRevisionAndNoPrivilegedContent() {
        val payload = project(listOf(activity(7)))
        val row = payload.getJSONArray("items").getJSONObject(0)
        assertEquals(7L, row.getLong("eventSeq"))
        assertEquals(2L, row.getLong("revision"))
        assertEquals(start, row.getLong("startMillis"))
        assertEquals(start + 3_600_000L, row.getLong("endMillis"))
        assertEquals("RIGHT", row.getString("side"))
        assertEquals("INSTANT", row.getString("precision"))
        assertTrue(row.isNull("originalOccurrenceKey"))
        assertEquals(24.0, payload.getDouble("hours"), 0.0)
        assertEquals(zone.id, payload.getString("zoneId"))
        assertFalse(payload.toString().contains("private-note-should-not-leak"))
    }

    @Test fun deadlineAndAlarmStayLeftWhileActivityStaysRight() {
        val deadline = TemporalRecord(
            eventSeq = 21, revision = 1, kind = TemporalKind.DEADLINE,
            title = "Deadline", deadlineMillis = start, zoneId = zone.id
        )
        val alarm = TemporalRecord(
            eventSeq = 22, revision = 1, kind = TemporalKind.ALARM,
            title = "Alarm", startMillis = start, zoneId = zone.id
        )
        val entries = project(listOf(activity(7), deadline, alarm))
            .getJSONArray("items")
        val sides = (0 until entries.length()).associate { i ->
            val row = entries.getJSONObject(i)
            row.getLong("eventSeq") to row.getString("side")
        }
        assertEquals("LEFT", sides[21L])
        assertEquals("LEFT", sides[22L])
        assertEquals("RIGHT", sides[7L])
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
        assertEquals("2026-10-22T09:00", occurrence.getString("originalOccurrenceKey"))
        val month = WebSkinMonthProjection.build(
            listOf(daily), WebSkinMonthProjection.initial(noon, zone), zone
        ).getJSONArray("items")
        val sameDay = (0 until month.length()).map { month.getJSONObject(it) }
            .single { it.getString("date") == "2026-10-22" }
        assertEquals("2026-10-22T09:00", sameDay.getString("originalOccurrenceKey"))
    }

    @Test fun memoProjectionSeparatesToggleIdentityFromOptionalOriginalSlot() {
        val repeatedMemo = TemporalRecord(
            eventSeq = 41, revision = 3, kind = TemporalKind.MEMO,
            title = "Daily memo", memoStartDate = "2026-10-20",
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 4),
            zoneId = zone.id
        )
        val undatedMemo = TemporalRecord(
            eventSeq = 42, revision = 1, kind = TemporalKind.MEMO,
            title = "No date", zoneId = zone.id
        )
        val entries = TodoProjection.entries(listOf(repeatedMemo, undatedMemo), noon)
        val rows = WebSkinMemoProjection.build(entries)
        val selected = (0 until rows.length()).map(rows::getJSONObject)
            .single { it.getLong("eventSeq") == repeatedMemo.eventSeq &&
                it.getString("occurrenceKey") == "2026-10-22T00:00" }
        assertEquals("2026-10-22T00:00", selected.getString("originalOccurrenceKey"))

        val single = (0 until rows.length()).map(rows::getJSONObject)
            .single { it.getLong("eventSeq") == undatedMemo.eventSeq }
        assertEquals("single", single.getString("occurrenceKey"))
        assertTrue(single.isNull("originalOccurrenceKey"))
    }

    @Test fun memoPagingUsesStableBoundedPrefixesAndNeverShrinksTheLimit() {
        val pageSize = MemoProjectionPaging.PAGE_SIZE
        assertEquals(249, MemoProjectionPaging.visibleCount(249, pageSize))
        assertEquals(250, MemoProjectionPaging.visibleCount(250, pageSize))
        assertEquals(250, MemoProjectionPaging.visibleCount(251, pageSize))
        assertEquals(0, MemoProjectionPaging.visibleCount(-1, pageSize))
        assertEquals(0, MemoProjectionPaging.visibleCount(10, -1))

        assertEquals(250, MemoProjectionPaging.nextLimit(250, 249))
        assertEquals(250, MemoProjectionPaging.nextLimit(250, 250))
        assertEquals(251, MemoProjectionPaging.nextLimit(250, 251))
        assertEquals(500, MemoProjectionPaging.nextLimit(250, 501))
        assertEquals(501, MemoProjectionPaging.nextLimit(500, 501))
        assertEquals(501, MemoProjectionPaging.nextLimit(501, 501))
        assertEquals(Int.MAX_VALUE,
            MemoProjectionPaging.nextLimit(Int.MAX_VALUE - 100, Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE,
            MemoProjectionPaging.nextLimit(Int.MAX_VALUE, Int.MAX_VALUE))
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
        assertEquals(WebSkinAction.ZoomTimeline(TimelineZoomDirection.IN),
            decode("""{"name":"zoomTimeline","direction":"in"}"""))
        assertEquals(WebSkinAction.ZoomTimeline(TimelineZoomDirection.OUT),
            decode("""{"name":"zoomTimeline","direction":"out"}"""))
        assertEquals(WebSkinAction.ResetTimelineZoom,
            decode("""{"name":"resetTimelineZoom"}"""))
        assertEquals(WebSkinAction.JumpTimelineDay("2026-10-22"),
            decode("""{"name":"jumpTimelineDay","date":"2026-10-22"}"""))
        assertEquals(WebSkinAction.Routed("today"), decode("""{"name":"today"}"""))
        assertEquals(WebSkinAction.Routed("memo"), decode("""{"name":"memo"}"""))
        assertEquals(WebSkinAction.CreateMemo,
            decode("""{"name":"createMemo"}"""))
        assertEquals(WebSkinAction.CreateDraft("ACTIVITY"),
            decode("""{"name":"createDraft","kind":"ACTIVITY"}"""))
        assertEquals(WebSkinAction.BrowseMonth(-1),
            decode("""{"name":"browseMonth","deltaMonths":-1}"""))
        assertEquals(WebSkinAction.SelectMonthDate(LocalDate.parse("2026-10-23")),
            decode("""{"name":"selectMonthDate","date":"2026-10-23"}"""))
        assertEquals(WebSkinAction.SelectMonth(YearMonth.of(2027, 4)),
            decode("""{"name":"selectMonth","month":"2027-04"}"""))
        assertNull(decode("""{"name":"browseMonth","deltaMonths":0}"""))
        assertNull(decode("""{"name":"browseMonth","deltaMonths":50}"""))
        assertNull(decode("""{"name":"selectMonthDate","date":"2026-02-30"}"""))
        assertNull(decode("""{"name":"selectMonthDate","date":"2026-10-23","month":"2026-10"}"""))
        assertNull(decode("""{"name":"selectMonth","month":"2101-01"}"""))
        assertNull(decode("""{"name":"selectMonth","month":"2026-13"}"""))
        assertEquals(WebSkinAction.SendChat("你好"),
            decode("""{"name":"sendChat","text":"你好"}"""))
        assertEquals(WebSkinAction.ToggleMemo(31,2,"single",false),
            decode("""{"name":"toggleMemo","eventSeq":31,"expectedRevision":2,
                "occurrenceKey":"single","completed":false}"""))
        assertNull(decode("""{"name":"createMemo","force":true}"""))
        assertNull(decode("""{"name":"createDraft","kind":"RAW_SQL"}"""))
        assertNull(decode("""{"name":"sendChat","text":""}"""))
        assertNull(decode("""{"name":"sendChat","text":123}"""))
        assertNull(decode("""{"name":"toggleMemo","eventSeq":31,"expectedRevision":2,
                "occurrenceKey":"single","completed":"true"}"""))
        assertNull(decode("""{"name":"toggleMemo","eventSeq":31,"expectedRevision":2,
                "occurrenceKey":"../../secret","completed":false}"""))
        assertNull(decode("""{"name":"panTimeline","deltaMinutes":5000}"""))
        assertNull(decode("""{"name":"panTimeline","deltaMinutes":2.5}"""))
        assertNull(decode("""{"name":"panTimeline","deltaMinutes":"60"}"""))
        assertNull(decode("""{"name":"panTimeline","deltaMinutes":60,"hours":24}"""))
        assertNull(decode("""{"name":"zoomTimeline","direction":"middle"}"""))
        assertNull(decode("""{"name":"zoomTimeline","direction":1}"""))
        assertNull(decode("""{"name":"zoomTimeline","direction":"in","hours":1}"""))
        assertNull(decode("""{"name":"resetTimelineZoom","hours":24}"""))
        assertNull(decode("""{"name":"jumpTimelineDay","date":"2026-10-32"}"""))
        assertNull(decode("""{"name":"jumpTimelineDay","date":"2500-01-01"}"""))
    }

    @Test fun aiProposalReviewProjectionKeepsTargetRevisionAndOmitsRawModelReply() {
        val target = activity(47)
        val interpretation = AiInterpretation(
            proposals = listOf(
                AiProposal(DraftOperation.REPLACE, 47, DraftForm(
                    kind = TemporalKind.ACTIVITY,
                    title = "更新课程",
                    startText = "2026-10-22 10:00",
                    endText = "2026-10-22 11:00",
                    location = "新教室",
                    notes = "new note",
                    zoneId = zone.id
                ), rationale = "明确的时间调整"),
                AiProposal(DraftOperation.CREATE, null,
                    DraftForm(kind = TemporalKind.ALARM, title = "Blocked"),
                    blocked = "缺少提醒时刻")
            ),
            warnings = listOf("请核对时间"),
            rawReply = "MODEL RAW SECRET"
        )
        val session = WebAiProposalReviewSession.create(interpretation, listOf(target))
        val output = WebAiProposalProjection.project(session)
        val items = output.getJSONArray("proposals")
        val replace = items.getJSONObject(0)
        assertEquals(session.reviewId, output.getString("reviewId"))
        assertEquals(2L, replace.getLong("targetRevision"))
        assertEquals("REPLACE", replace.getString("operation"))
        assertEquals("更新课程", replace.getString("title"))
        assertEquals("新教室", replace.getString("location"))
        assertEquals("new note", replace.getString("notes"))
        assertEquals("Asia/Shanghai", replace.getString("zoneId"))
        assertTrue(items.getJSONObject(1).getString("blocked").contains("提醒时刻"))
        assertFalse(output.toString().contains("MODEL RAW SECRET"))
        assertEquals(listOf(0), WebAiProposalProjection.validSelection(session, listOf(0)))
        assertNull(WebAiProposalProjection.validSelection(session, listOf(1)))
        assertNull(WebAiProposalProjection.validSelection(session, listOf(0, 0)))
    }

    @Test fun aiProposalActionRequiresCanonicalReviewIdAndUniqueBoundedSelection() {
        val id = "e4c9a290-dcf8-4e18-97e0-18f72e53e8dd"
        fun decode(payload: String) = decodeBridgeAction(
            """{"version":1,"type":"action","payload":$payload}""")
        assertEquals(WebSkinAction.ResolveAiProposal(id, AiProposalDecision.PUBLISH, listOf(0, 2)),
            decode("""{"name":"resolveAiProposal","reviewId":"$id","decision":"publish","indices":[0,2]}"""))
        assertEquals(WebSkinAction.ResolveAiProposal(id, AiProposalDecision.SUPPLEMENT, emptyList()),
            decode("""{"name":"resolveAiProposal","reviewId":"$id","decision":"supplement","indices":[]}"""))
        assertEquals(WebSkinAction.ResolveAiProposal(id, AiProposalDecision.STAGE, listOf(1)),
            decode("""{"name":"resolveAiProposal","reviewId":"$id","decision":"stage","indices":[1]}"""))
        assertEquals(WebSkinAction.ResolveAiProposal(id, AiProposalDecision.REJECT, listOf(1)),
            decode("""{"name":"resolveAiProposal","reviewId":"$id","decision":"reject","indices":[1]}"""))
        assertNull(decode("""{"name":"resolveAiProposal","reviewId":"not-a-uuid","decision":"publish","indices":[0]}"""))
        assertNull(decode("""{"name":"resolveAiProposal","reviewId":"$id","decision":"publish","indices":[0,0]}"""))
        assertNull(decode("""{"name":"resolveAiProposal","reviewId":"$id","decision":"publish","indices":[-1]}"""))
        assertNull(decode("""{"name":"resolveAiProposal","reviewId":"$id","decision":"publish","indices":[0.5]}"""))
        assertNull(decode("""{"name":"resolveAiProposal","reviewId":"$id","decision":"supplement","indices":[0]}"""))
        assertNull(decode("""{"name":"resolveAiProposal","reviewId":"$id","decision":"publish","indices":[0],"authorized":true}"""))
    }

    @Test fun monthProjectionUsesRealHostOccurrencesAndExactDateWindow() {
        val view = WebSkinMonthProjection.initial(noon, zone)
        val output = WebSkinMonthProjection.build(listOf(activity(71)), view, zone)
        assertEquals("2026-10", output.getString("month"))
        assertEquals("2026-09-28", output.getString("gridFrom"))
        assertEquals("2026-10-22", output.getString("selectedDate"))
        val first = output.getJSONArray("items").getJSONObject(0)
        assertEquals("2026-10-22", first.getString("date"))
        assertEquals(71, first.getInt("eventSeq"))
        assertEquals(2, first.getInt("revision"))
        assertFalse(output.getBoolean("truncated"))
    }

    @Test fun monthSelectionNavigationTodayAndProjectionStayIndependentFromTimelineAnchor() {
        val initial = WebSkinMonthProjection.initial(noon, zone)
        val selected = WebSkinMonthProjection.selectDate(initial, LocalDate.of(2026, 10, 23))
        assertEquals(LocalDate.of(2026, 10, 23), selected?.selectedDate)
        assertEquals(initial.month, selected?.month)
        assertNull(WebSkinMonthProjection.selectDate(initial, LocalDate.of(2026, 11, 1)))

        val next = WebSkinMonthProjection.browseMonth(requireNotNull(selected), 1)
        assertEquals(YearMonth.of(2026, 11), next?.month)
        assertEquals(LocalDate.of(2026, 11, 1), next?.selectedDate)
        assertNull(WebSkinMonthProjection.browseMonth(initial, 0))
        assertNull(WebSkinMonthProjection.browseMonth(initial, 13))

        val chosen = WebSkinMonthProjection.selectMonth(YearMonth.of(2027, 4))
        assertEquals(LocalDate.of(2027, 4, 1), chosen?.selectedDate)
        assertNull(WebSkinMonthProjection.selectMonth(YearMonth.of(2101, 1)))

        val today = LocalDate.of(2026, 10, 29)
        assertEquals(WebSkinMonthViewState(YearMonth.of(2026, 10), today),
            WebSkinMonthProjection.today(today))
        assertEquals(YearMonth.of(2026, 10), initial.month)
        assertEquals(LocalDate.of(2026, 10, 22), initial.selectedDate)
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
