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

    @Test fun aiAttachmentPickerActionsArePayloadFreeAndClosedSchema() {
        fun decode(payload: String) = decodeBridgeAction(
            """{"version":1,"type":"action","payload":$payload}""")

        assertEquals(WebSkinAction.PickAiFile, decode("""{"name":"pickAiFile"}"""))
        assertEquals(WebSkinAction.PickAiImage, decode("""{"name":"pickAiImage"}"""))
        assertNull(decode("""{"name":"pickAiFile","uri":"content://private"}"""))
        assertNull(decode("""{"name":"pickAiImage","mime":"image/png"}"""))
    }

    @Test fun sendChatUsesTheNativeUtf16LengthBoundary() {
        fun decodeChat(text: String) = decodeBridgeAction(
            JSONObject().put("version", 1).put("type", "action")
                .put("payload", JSONObject().put("name", "sendChat").put("text", text))
                .toString()
        )

        val nativeLimitAscii = "x".repeat(6000)
        val nativeLimitSupplementary = "\uD83D\uDE00".repeat(3000)
        assertEquals(6000, nativeLimitSupplementary.length)
        assertEquals(WebSkinAction.SendChat(nativeLimitAscii), decodeChat(nativeLimitAscii))
        assertEquals(WebSkinAction.SendChat(nativeLimitSupplementary), decodeChat(nativeLimitSupplementary))
        assertNull(decodeChat("x".repeat(6001)))
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
        val selectedDate = Instant.ofEpochMilli(noon).atZone(zone).toLocalDate()
        val month = WebSkinMonthProjection.build(listOf(daily), selectedDate, zone).getJSONArray("items")
        val sameDay = (0 until month.length()).map { month.getJSONObject(it) }
            .single { it.getString("date") == "2026-10-22" }
        assertEquals("2026-10-22T09:00", sameDay.getString("originalOccurrenceKey"))
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
        assertEquals(WebSkinAction.CreateMemo,
            decode("""{"name":"createMemo"}"""))
        assertEquals(WebSkinAction.CreateDraft("ACTIVITY"),
            decode("""{"name":"createDraft","kind":"ACTIVITY"}"""))
        assertEquals(WebSkinAction.BrowseMonth(-1),
            decode("""{"name":"browseMonth","deltaMonths":-1}"""))
        assertNull(decode("""{"name":"browseMonth","deltaMonths":0}"""))
        assertNull(decode("""{"name":"browseMonth","deltaMonths":50}"""))
        assertEquals(WebSkinAction.SelectMonth("2030-02"),
            decode("""{"name":"selectMonth","yearMonth":"2030-02"}"""))
        assertEquals(WebSkinAction.SelectMonthDate("2030-02-14"),
            decode("""{"name":"selectMonthDate","date":"2030-02-14"}"""))
        assertEquals(WebSkinAction.TodayInMonth,
            decode("""{"name":"todayInMonth"}"""))
        assertNull(decode("""{"name":"selectMonth","yearMonth":"1899-12"}"""))
        assertNull(decode("""{"name":"selectMonth","yearMonth":"2101-01"}"""))
        assertNull(decode("""{"name":"selectMonth","yearMonth":"2030-13"}"""))
        assertNull(decode("""{"name":"selectMonth","yearMonth":"2030-2"}"""))
        assertNull(decode("""{"name":"selectMonth","yearMonth":"2030-02","day":1}"""))
        assertNull(decode("""{"name":"selectMonthDate","date":"2030-02-30"}"""))
        assertNull(decode("""{"name":"todayInMonth","date":"2030-02-01"}"""))
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
        val selectedDate = Instant.ofEpochMilli(noon).atZone(zone).toLocalDate()
        val output = WebSkinMonthProjection.build(listOf(activity(71)), selectedDate, zone)
        assertEquals("2026-10", output.getString("month"))
        assertEquals("2026-09-28", output.getString("gridFrom"))
        val first = output.getJSONArray("items").getJSONObject(0)
        assertEquals("2026-10-22", first.getString("date"))
        assertEquals(71, first.getInt("eventSeq"))
        assertEquals(2, first.getInt("revision"))
        assertFalse(output.getBoolean("truncated"))
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

    @Test fun reminderPermissionProjectionContainsOnlyHostPermissionBooleans() {
        val projection = ReminderSettingsProjection.toJson(
            exactAlarmAllowed = false,
            notificationsAllowed = true,
            notificationsRuntimePermissionRequired = false
        )
        assertEquals(3, projection.length())
        assertFalse(projection.getBoolean("exactAlarmAllowed"))
        assertTrue(projection.getBoolean("notificationsAllowed"))
        assertFalse(projection.getBoolean("notificationsRuntimePermissionRequired"))
        assertFalse(projection.has("eventSeq"))
        assertFalse(projection.has("apiKey"))
    }

    @Test fun reminderHostOptionsAreWhitelistedAndReflectPermissionAvailability() {
        val ids = SemanticOptions.groups.flatMap { it.items }.map { it.id }.toSet()
        assertTrue("reminders.exactAlarmSettings" in ids)
        assertTrue("reminders.notificationPermission" in ids)
        assertTrue("reminders.reconcile" in ids)
        assertTrue("calendar.export.ics" in ids)
        assertTrue("data.backup.export" in ids)
        assertTrue("data.backup.restore" in ids)
        assertTrue("skin.import" in ids)
        assertTrue("skin.exportClear" in ids)
        assertFalse(SemanticOptions.supports("reminders.grantSilently"))
        assertFalse(SemanticOptions.supports("data.backup.readRaw"))
        assertFalse(SemanticOptions.supports("skin.select"))

        val group = SemanticOptions.groups.first { it.id == "reminders.permissions" }
        val disabled = SemanticOptions.toJson(
            collection = listOf(group),
            disabledIds = setOf("reminders.exactAlarmSettings")
        ).getJSONObject(0).getJSONArray("items")
        assertFalse(disabled.getJSONObject(0).getBoolean("enabled"))
        assertTrue(disabled.getJSONObject(1).getBoolean("enabled"))

        val generalOptions = SemanticOptions.toJson()
        val generalIds = buildSet {
            for (groupIndex in 0 until generalOptions.length()) {
                val items = generalOptions.getJSONObject(groupIndex).getJSONArray("items")
                for (itemIndex in 0 until items.length()) add(items.getJSONObject(itemIndex).getString("id"))
            }
        }
        assertFalse("skin.import" in generalIds)
        assertFalse("skin.exportClear" in generalIds)
        val skinSettingsOptions = SemanticOptions.toJson(includeSkinPackages = true)
        assertTrue(skinSettingsOptions.toString().contains("skin.import"))
        assertTrue(skinSettingsOptions.toString().contains("skin.exportClear"))

        val backup = SemanticOptions.groups.first { it.id == "data.backup" }
        val backupItems = SemanticOptions.toJson(collection = listOf(backup))
            .getJSONObject(0).getJSONArray("items")
        assertTrue(backupItems.getJSONObject(0).getString("detail").contains("明文 JSON"))
        assertTrue(backupItems.getJSONObject(1).getString("detail").contains("空数据库"))
    }

    @Test fun installedSkinProjectionContainsOnlyBoundedCatalogueMetadata() {
        val longName = "Visible skin " + "x".repeat(160)
        val output = WebSkinInstalledSkinsProjection.build(
            listOf(
                InstalledSkin("powercal-default", "PowerCal fallback", builtIn = true),
                InstalledSkin("custom-skin", longName)
            ),
            selectedId = "custom-skin",
            actionsEnabled = false
        )
        assertEquals(2, output.length())
        val fallback = output.getJSONObject(0)
        assertEquals("powercal-default", fallback.getString("id"))
        assertTrue(fallback.getBoolean("builtIn"))
        assertFalse(fallback.getBoolean("selected"))
        assertFalse(fallback.getBoolean("actionsEnabled"))

        val custom = output.getJSONObject(1)
        assertEquals("custom-skin", custom.getString("id"))
        assertEquals(100, custom.getString("name").length)
        assertTrue(custom.getBoolean("selected"))
        assertFalse(custom.getBoolean("builtIn"))
        assertFalse(custom.has("path"))
        assertFalse(custom.has("uri"))
        assertFalse(custom.has("source"))
        assertFalse(custom.has("manifest"))

        val tooMany = WebSkinInstalledSkinsProjection.build(
            (0..20).map { InstalledSkin("skin-$it", "Skin $it") },
            selectedId = "skin-0",
            actionsEnabled = true
        )
        assertEquals(14, tooMany.length())
    }
}
