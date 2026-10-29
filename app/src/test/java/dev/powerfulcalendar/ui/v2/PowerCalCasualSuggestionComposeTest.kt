package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.ai.AiChatActionParser
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TimelineCoordinate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PowerCalCasualSuggestionComposeTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private lateinit var storage: Context
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var databaseName: String
    private lateinit var namespace: String

    @Before fun setUp() {
        storage = ApplicationProvider.getApplicationContext()
        namespace = "pc-casual-chat-${UUID.randomUUID()}"
        context = IsolatedAppContext(ui.activity, storage, namespace)
        // Robolectric includes the full test method in its Windows temp path; keep this short.
        databaseName = "p.db"
        store = PowerStore(storage, databaseName)
        store.readableDatabase
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
    }

    @After fun tearDown() {
        ui.activity.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
        store.close()
        storage.deleteDatabase(databaseName)
        listOf("powercal-ai", "powercal-onboarding", "powercal-ui", "powercal-skin-choice",
            "powercal-profile").forEach { storage.deleteSharedPreferences("$namespace-$it") }
        File(storage.cacheDir, namespace).deleteRecursively()
        PowerCalSkinRegistry.reset()
    }

    private data class TimelineCameraSnapshot(
        val anchorMillis: Long,
        val hours: Double,
        val nowMillis: Long,
        val zoneId: String
    )

    private fun timelineCameraSnapshot(): TimelineCameraSnapshot {
        var snapshot: TimelineCameraSnapshot? = null
        ui.runOnIdle {
            val timeline = WebSkinBridgeRuntime.state().getJSONObject("timeline")
            snapshot = TimelineCameraSnapshot(
                anchorMillis = timeline.getLong("anchorMillis"),
                hours = timeline.getDouble("hours"),
                nowMillis = timeline.getLong("nowMillis"),
                zoneId = timeline.getString("zoneId")
            )
        }
        return requireNotNull(snapshot)
    }

    private fun assertLocalNoonAnd24Hours(camera: TimelineCameraSnapshot) {
        val zone = ZoneId.of(camera.zoneId)
        val expectedNoon = Instant.ofEpochMilli(camera.nowMillis).atZone(zone)
            .toLocalDate().atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expectedNoon, camera.anchorMillis)
        assertEquals(24.0, camera.hours, 0.0)
    }

    @Test
    fun nativeAndWebTodayActionsBothReturnToLocalNoonAnd24Hours() {
        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitForIdle()

        fun moveAwayFromTodayCamera() {
            ui.onNodeWithContentDescription("缩小，显示更长时间").performClick()
            ui.onNodeWithTag("timeline-viewport").performTouchInput { swipeUp() }
            ui.waitForIdle()
        }

        moveAwayFromTodayCamera()
        val beforeNative = timelineCameraSnapshot()
        assertEquals(36.0, beforeNative.hours, 0.0)
        assertNotEquals(
            Instant.ofEpochMilli(beforeNative.nowMillis).atZone(ZoneId.of(beforeNative.zoneId))
                .toLocalDate().atTime(12, 0).atZone(ZoneId.of(beforeNative.zoneId))
                .toInstant().toEpochMilli(),
            beforeNative.anchorMillis
        )

        ui.onNodeWithContentDescription("今日 · 复位为全天").performClick()
        ui.waitForIdle()
        assertLocalNoonAnd24Hours(timelineCameraSnapshot())

        moveAwayFromTodayCamera()
        val beforeWeb = timelineCameraSnapshot()
        assertEquals(36.0, beforeWeb.hours, 0.0)
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("today"))
        }
        ui.waitForIdle()
        assertLocalNoonAnd24Hours(timelineCameraSnapshot())
    }

    @Test
    fun webTimelineZoomMatchesNativeScaleBoundsAndResetPreservesCenterWithoutWrites() {
        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitForIdle()

        fun dispatch(action: WebSkinAction) {
            ui.runOnIdle { WebSkinBridgeRuntime.onAction(action) }
            ui.waitForIdle()
        }

        val before = timelineCameraSnapshot()
        val storeBefore = StoreCounts.read(store)
        assertEquals(24.0, before.hours, 0.0)

        dispatch(WebSkinAction.ZoomTimeline(TimelineZoomDirection.OUT))
        assertEquals(36.0, timelineCameraSnapshot().hours, 0.0)
        assertEquals(before.anchorMillis, timelineCameraSnapshot().anchorMillis)

        dispatch(WebSkinAction.ZoomTimeline(TimelineZoomDirection.IN))
        assertEquals(24.0, timelineCameraSnapshot().hours, 0.0)
        repeat(8) { dispatch(WebSkinAction.ZoomTimeline(TimelineZoomDirection.OUT)) }
        assertEquals(48.0, timelineCameraSnapshot().hours, 0.0)
        repeat(20) { dispatch(WebSkinAction.ZoomTimeline(TimelineZoomDirection.IN)) }
        assertEquals(TimelineCoordinate.MIN_HOURS, timelineCameraSnapshot().hours, 0.0)

        dispatch(WebSkinAction.ResetTimelineZoom)
        assertEquals(24.0, timelineCameraSnapshot().hours, 0.0)
        assertEquals(before.anchorMillis, timelineCameraSnapshot().anchorMillis)

        dispatch(WebSkinAction.ZoomTimeline(TimelineZoomDirection.OUT))
        dispatch(WebSkinAction.Routed("month"))
        dispatch(WebSkinAction.ZoomTimeline(TimelineZoomDirection.IN))
        dispatch(WebSkinAction.ResetTimelineZoom)
        assertEquals("non-timeline pages cannot mutate the timeline scale", 36.0,
            timelineCameraSnapshot().hours, 0.0)
        assertEquals(before.anchorMillis, timelineCameraSnapshot().anchorMillis)
        assertEquals(storeBefore, StoreCounts.read(store))
    }

    @Test
    fun timelineCreateCommandUsesCurrentCameraAndZoneForAllKindsWithoutWrites() {
        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitForIdle()

        fun runWebEdit(command: WebEditCommand): JSONObject {
            val response = AtomicReference<JSONObject?>()
            val failure = AtomicReference<Throwable?>()
            Thread({
                try {
                    response.set(runBlocking { WebSkinBridgeRuntime.onEdit(command) })
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }, "powercal-web-edit-compose-test").apply {
                isDaemon = true
                start()
            }
            ui.waitUntil(10_000) { response.get() != null || failure.get() != null }
            failure.get()?.let { throw AssertionError("Web edit command failed", it) }
            return requireNotNull(response.get())
        }

        val before = StoreCounts.read(store)

        WebSkinBridgeRuntime.onAction(WebSkinAction.PanTimeline(137))
        ui.waitForIdle()
        val timeline = WebSkinBridgeRuntime.state().getJSONObject("timeline")
        val camera = TimelineCoordinate.Camera(
            timeline.getLong("anchorMillis"), timeline.getDouble("hours")
        )
        val zone = ZoneId.of(timeline.getString("zoneId"))
        val expectedStart = TimelineCoordinate.snap(camera.anchorMillis, camera.hours, zone)

        for (kind in TemporalKind.entries) {
            val opened = runWebEdit(
                WebEditCommand.CreateDeck("timeline-create-" + kind.name, kind)
            )
            assertEquals("created", opened.getString("status"))
            ui.waitForIdle()
            val form = WebSkinBridgeRuntime.state().getJSONObject("editing")
                .getJSONObject("deck").getJSONObject("form")
            assertEquals(kind.name, form.getString("kind"))
            assertEquals(zone.id, form.getString("zoneId"))
            when (kind) {
                TemporalKind.ALARM -> assertEquals(
                    DraftForm.formatInstant(expectedStart, zone), form.getString("startText")
                )
                TemporalKind.ACTIVITY -> {
                    assertEquals(
                        DraftForm.formatInstant(expectedStart, zone), form.getString("startText")
                    )
                    assertEquals(
                        DraftForm.formatInstant(expectedStart + 3_600_000L, zone),
                        form.getString("endText")
                    )
                }
                TemporalKind.DEADLINE -> {
                    assertEquals(
                        DraftForm.formatInstant(expectedStart, zone), form.getString("endText")
                    )
                    assertEquals(60, form.getInt("reminderMinutes"))
                }
                TemporalKind.MEMO -> assertEquals(
                    Instant.ofEpochMilli(expectedStart).atZone(zone).toLocalDate().toString(),
                    form.getString("startText")
                )
            }
            val closed = runWebEdit(
                WebEditCommand.CloseDeck("timeline-close-" + kind.name)
            )
            assertEquals("closed", closed.getString("status"))
            ui.waitForIdle()
        }

        WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("month"))
        ui.waitForIdle()
        val monthState = WebSkinBridgeRuntime.state().getJSONObject("timeline")
        val monthZone = ZoneId.of(monthState.getString("zoneId"))
        val selectedMonthDate = Instant.ofEpochMilli(monthState.getLong("anchorMillis"))
            .atZone(monthZone).toLocalDate()
        val monthCreate = runWebEdit(
            WebEditCommand.CreateDeck("month-create-activity", TemporalKind.ACTIVITY)
        )
        assertEquals("created", monthCreate.getString("status"))
        ui.waitForIdle()
        val monthForm = WebSkinBridgeRuntime.state().getJSONObject("editing")
            .getJSONObject("deck").getJSONObject("form")
        assertEquals(selectedMonthDate.toString(), monthForm.getString("startText").take(10))
        assertEquals(monthZone.id, monthForm.getString("zoneId"))
        val monthClose = runWebEdit(
            WebEditCommand.CloseDeck("month-close-activity")
        )
        assertEquals("closed", monthClose.getString("status"))
        ui.waitForIdle()
        assertEquals(before, StoreCounts.read(store))
    }

    @Test
    fun webMonthSelectionNavigationAndTodayStayIndependentFromTimelineCamera() {
        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitForIdle()
        val before = StoreCounts.read(store)

        fun dispatch(action: WebSkinAction) {
            ui.runOnIdle { WebSkinBridgeRuntime.onAction(action) }
            ui.waitForIdle()
        }
        fun runWebEdit(command: WebEditCommand): JSONObject {
            val response = AtomicReference<JSONObject?>()
            val failure = AtomicReference<Throwable?>()
            Thread({
                try { response.set(runBlocking { WebSkinBridgeRuntime.onEdit(command) }) }
                catch (error: Throwable) { failure.set(error) }
            }, "powercal-web-month-compose-test").apply {
                isDaemon = true
                start()
            }
            ui.waitUntil(10_000) { response.get() != null || failure.get() != null }
            failure.get()?.let { throw AssertionError("Web month edit command failed", it) }
            return requireNotNull(response.get())
        }

        dispatch(WebSkinAction.Routed("month"))
        val initial = WebSkinBridgeRuntime.state()
        val initialCamera = initial.getJSONObject("timeline")
        val cameraAnchor = initialCamera.getLong("anchorMillis")
        val cameraHours = initialCamera.getDouble("hours")
        val month = initial.getJSONObject("month")
        val initialMonth = YearMonth.parse(month.getString("month"))
        val initialSelection = LocalDate.parse(month.getString("selectedDate"))
        val chosenDate = if (initialSelection != initialMonth.atDay(1))
            initialMonth.atDay(1) else initialMonth.atDay(2)

        dispatch(WebSkinAction.SelectMonthDate(chosenDate))
        var state = WebSkinBridgeRuntime.state()
        assertEquals("MONTH", state.getString("page"))
        assertEquals(chosenDate.toString(), state.getJSONObject("month").getString("selectedDate"))
        assertEquals(cameraAnchor, state.getJSONObject("timeline").getLong("anchorMillis"))
        assertEquals(cameraHours, state.getJSONObject("timeline").getDouble("hours"), 0.0)

        dispatch(WebSkinAction.BrowseMonth(1))
        state = WebSkinBridgeRuntime.state()
        val nextMonth = initialMonth.plusMonths(1)
        assertEquals(nextMonth.toString(), state.getJSONObject("month").getString("month"))
        assertEquals(nextMonth.atDay(1).toString(), state.getJSONObject("month").getString("selectedDate"))

        dispatch(WebSkinAction.SelectMonth(YearMonth.of(2027, 4)))
        dispatch(WebSkinAction.SelectMonthDate(LocalDate.of(2027, 4, 19)))
        val selected = WebSkinBridgeRuntime.state().getJSONObject("month")
        assertEquals("2027-04", selected.getString("month"))
        assertEquals("2027-04-19", selected.getString("selectedDate"))

        val created = runWebEdit(WebEditCommand.CreateDeck("selected-month-day", TemporalKind.ACTIVITY))
        assertEquals("created", created.getString("status"))
        ui.waitForIdle()
        val form = WebSkinBridgeRuntime.state().getJSONObject("editing")
            .getJSONObject("deck").getJSONObject("form")
        assertEquals("2027-04-19", form.getString("startText").take(10))
        assertEquals("closed", runWebEdit(WebEditCommand.CloseDeck("selected-month-close"))
            .getString("status"))
        ui.waitForIdle()

        dispatch(WebSkinAction.Routed("today"))
        state = WebSkinBridgeRuntime.state()
        val timeline = state.getJSONObject("timeline")
        val zone = ZoneId.of(timeline.getString("zoneId"))
        val today = Instant.ofEpochMilli(timeline.getLong("nowMillis")).atZone(zone).toLocalDate()
        assertEquals("MONTH", state.getString("page"))
        assertEquals(YearMonth.from(today).toString(), state.getJSONObject("month").getString("month"))
        assertEquals(today.toString(), state.getJSONObject("month").getString("selectedDate"))
        assertEquals(cameraAnchor, timeline.getLong("anchorMillis"))
        assertEquals(cameraHours, timeline.getDouble("hours"), 0.0)
        assertEquals(before, StoreCounts.read(store))
    }

    @Test
    fun webMemoToggleIsOccurrenceScopedRevisionGuardedAndStableAcrossRestart() {
        val zone = ZoneId.of("Asia/Shanghai")
        val firstDate = LocalDate.now(zone).plusDays(1)
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = "重复 Memo 完成状态",
            startText = firstDate.toString(),
            zoneId = zone.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        ))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        val owner = requireNotNull(store.get(card.eventSeq))
        val selectedKey = "${firstDate}T00:00"
        val nextKey = "${firstDate.plusDays(1)}T00:00"

        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitForIdle()
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("memo")) }
        ui.waitUntil(10_000) {
            val entries = WebSkinBridgeRuntime.state().optJSONArray("memoEntries")
                ?: return@waitUntil false
            (0 until entries.length()).any { index ->
                entries.getJSONObject(index).optString("occurrenceKey") == selectedKey
            }
        }
        val projected = WebSkinBridgeRuntime.state().getJSONArray("memoEntries")
            .let { entries -> (0 until entries.length()).map(entries::getJSONObject) }
            .single { it.getString("occurrenceKey") == selectedKey }
        assertEquals(owner.eventSeq, projected.getLong("eventSeq"))
        assertEquals(owner.revision, projected.getLong("revision"))
        assertEquals(selectedKey, projected.getString("originalOccurrenceKey"))
        assertFalse(projected.getBoolean("completed"))
        val countsBeforeToggle = StoreCounts.read(store)
        val toggle = WebSkinAction.ToggleMemo(
            owner.eventSeq, owner.revision, selectedKey, completed = false
        )

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(toggle) }
        ui.waitUntil(10_000) {
            val current = store.get(owner.eventSeq)
            current?.revision == owner.revision + 1 && selectedKey in current.completedKeys
        }
        val completed = requireNotNull(store.get(owner.eventSeq))
        assertEquals(owner.revision + 1, completed.revision)
        assertTrue(selectedKey in completed.completedKeys)
        assertFalse(nextKey in completed.completedKeys)
        val countsAfterToggle = countsBeforeToggle.copy(commits = countsBeforeToggle.commits + 1)
        assertEquals(countsAfterToggle, StoreCounts.read(store))
        ui.waitForIdle()

        var revisionBeforeReplay = 0L
        ui.runOnIdle { revisionBeforeReplay = WebSkinBridgeRuntime.stateRevision }
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(toggle) }
        ui.waitUntil(10_000) {
            var revision = revisionBeforeReplay
            ui.runOnUiThread { revision = WebSkinBridgeRuntime.stateRevision }
            revision > revisionBeforeReplay
        }
        assertEquals("a replay with the old revision/completion snapshot cannot toggle twice",
            completed, store.get(owner.eventSeq))
        assertEquals(countsAfterToggle, StoreCounts.read(store))

        ui.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
        store.close()
        store = PowerStore(storage, databaseName)
        val reopened = requireNotNull(store.get(owner.eventSeq))
        assertTrue(selectedKey in reopened.completedKeys)
        assertFalse(nextKey in reopened.completedKeys)
        assertEquals(completed.revision, reopened.revision)
    }

    @Test
    fun openEventFallbackRetainsTheSelectedMemoOccurrence() {
        val zone = ZoneId.of("Asia/Shanghai")
        val firstDate = LocalDate.now(zone).plusDays(2)
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = "Fallback 重复 Memo 编辑",
            startText = firstDate.toString(),
            zoneId = zone.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        ))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        val owner = requireNotNull(store.get(card.eventSeq))
        val selectedDate = firstDate.plusDays(1)
        val selectedKey = "${selectedDate}T00:00"

        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitForIdle()
        val before = StoreCounts.read(store)
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("memo")) }
        ui.waitUntil(10_000) {
            WebSkinBridgeRuntime.state().optString("page") == "MEMO" &&
                WebSkinBridgeRuntime.state().optJSONArray("memoEntries")?.let { entries ->
                    (0 until entries.length()).any { index ->
                        entries.getJSONObject(index).optString("originalOccurrenceKey") == selectedKey
                    }
                } == true
        }

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.OpenEvent(
                owner.eventSeq, owner.revision, occurrenceKey = selectedKey
            ))
        }
        ui.waitForIdle()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("重复事件编辑范围（必须明确选择）")
                .fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("仅本次").assertIsEnabled().performClick()
        ui.onNodeWithText(selectedDate.toString()).performScrollTo().assertIsDisplayed()
        assertEquals("the fallback open action is read-only", before, StoreCounts.read(store))
        ui.onNodeWithText("存档并退出").performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("重复事件编辑范围（必须明确选择）")
                .fetchSemanticsNodes().isEmpty()
        }
        assertEquals(before, StoreCounts.read(store))
    }

    @Test
    fun casualSuggestionUsesOptionalChatActionWithoutInterpretationOrCalendarWrites() {
        val before = StoreCounts.read(store)
        val userText = "也许下周可以安排一次整理，先随口聊聊，不要现在创建。"
        val visibleReply = "可以考虑安排一次整理；需要时你可以再从这条消息创建草稿。"
        val fakeMarker = """
            ${AiChatActionParser.OPEN}
            {"type":"draft","intent":"suggested","source":"模型伪造的来源文本"}
            ${AiChatActionParser.CLOSE}
        """.trimIndent()
        val requests = CopyOnWriteArrayList<String>()
        val unexpectedRequests = AtomicInteger()
        val serverFailure = AtomicReference<Throwable?>()
        val firstRequestServed = CountDownLatch(1)

        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 15_000
            AiSettingsStore.save(context, AiSettings(
                endpoint = "http://127.0.0.1:${server.localPort}/v1",
                model = "local-stub:free",
                apiKey = "LOCAL_TEST_ONLY",
                enabled = true,
                shareContext = false
            ))

            val serverThread = Thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val request = readRequest(socket)
                        requests += request
                        writeChatReply(socket, "$visibleReply\n$fakeMarker")
                    }
                    firstRequestServed.countDown()

                    // A second request would be the automatic structured interpret call.
                    server.soTimeout = 1_200
                    try {
                        server.accept().use { socket ->
                            unexpectedRequests.incrementAndGet()
                            readRequest(socket)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Expected for a casual suggestion: no interpret request follows chat.
                    }
                } catch (error: Throwable) {
                    serverFailure.set(error)
                    firstRequestServed.countDown()
                }
            }.apply { name = "pc-casual-chat-loopback"; isDaemon = true; start() }

            ui.activity.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
            ui.waitForIdle()
            ui.onNodeWithText("AI 助手").performClick()
            ui.onNodeWithText("发送消息…").performTextInput(userText)
            ui.onNodeWithText("发送").performClick()

            ui.waitUntil(15_000) {
                serverFailure.get() != null || ui.onAllNodesWithText(visibleReply)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertNull("loopback server failed: ${serverFailure.get()}", serverFailure.get())
            ui.onNodeWithText(visibleReply).assertIsDisplayed()
            ui.onNodeWithText("从此消息创建事件草稿").assertIsDisplayed()
            assertEquals(0, ui.onAllNodesWithText("识别到新增 · 是否创建草稿？")
                .fetchSemanticsNodes().size)

            assertEquals(true, firstRequestServed.await(1, TimeUnit.SECONDS))
            serverThread.join(2_000)
            assertEquals(false, serverThread.isAlive)
            assertNull("loopback server failed: ${serverFailure.get()}", serverFailure.get())
            assertEquals("only the ordinary chat request should be sent", 1, requests.size)
            assertEquals("no structured interpret call should follow", 0, unexpectedRequests.get())

            val body = JSONObject(requests.single())
            assertEquals("local-stub:free", body.getString("model"))
            val messages = body.getJSONArray("messages")
            assertEquals(userText, messages.getJSONObject(messages.length() - 1).getString("content"))
            assertEquals(before, StoreCounts.read(store))
        }
    }

    private data class StoreCounts(
        val drafts: Long,
        val pending: Int,
        val formal: Int,
        val identities: Long,
        val nextSequence: Long,
        val nextQueueOrder: Long,
        val commits: Long
    ) {
        companion object {
            fun read(store: PowerStore) = StoreCounts(
                drafts = store.draftCount(),
                pending = store.pendingCount(),
                formal = store.all().size,
                identities = store.identityCount(),
                nextSequence = store.nextSequencePreview(),
                nextQueueOrder = store.nextQueueOrderPreview(),
                commits = store.commitCount()
            )
        }
    }

    private fun readRequest(socket: java.net.Socket): String {
        val input = socket.getInputStream().buffered()
        fun line(): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val byte = input.read()
                require(byte >= 0) { "truncated HTTP header" }
                if (byte == '\n'.code) break
                if (byte != '\r'.code) bytes.write(byte)
                require(bytes.size() <= 8_192) { "oversized HTTP header" }
            }
            return bytes.toString(Charsets.US_ASCII.name())
        }

        require(line().startsWith("POST /v1/chat/completions HTTP/1.1")) {
            "unexpected local endpoint request"
        }
        var contentLength = -1
        while (true) {
            val header = line()
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = header.substringAfter(':').trim().toInt()
            }
        }
        require(contentLength in 1..100_000) { "invalid request size" }
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < body.size) {
            val count = input.read(body, offset, body.size - offset)
            require(count > 0) { "truncated HTTP body" }
            offset += count
        }
        return String(body, Charsets.UTF_8)
    }

    private fun writeChatReply(socket: java.net.Socket, content: String) {
        val body = JSONObject().put("choices", JSONArray().put(
            JSONObject().put("message", JSONObject().put("role", "assistant").put("content", content))
                .put("finish_reason", "stop")
        )).toString().toByteArray(Charsets.UTF_8)
        val headers = ("HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
        socket.getOutputStream().apply { write(headers); write(body); flush() }
    }

    private class IsolatedAppContext(
        target: Context,
        private val backing: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(backing.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory) { "cannot create isolated test root" }
        }

        private fun databaseAlias(name: String) = "$namespace-$name"
        private fun databasePath(name: String): File = backing.getDatabasePath(databaseAlias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true) {
                "cannot create isolated database directory"
            }
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            backing.getSharedPreferences("$namespace-$name", mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = databasePath(name)
        override fun openOrCreateDatabase(
            name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(databaseAlias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(databaseAlias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = backing.deleteDatabase(databaseAlias(name))
    }
}
