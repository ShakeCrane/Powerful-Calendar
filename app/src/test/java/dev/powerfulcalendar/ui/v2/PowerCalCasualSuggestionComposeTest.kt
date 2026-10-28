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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.ai.AiChatActionParser
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TimelineCoordinate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
import java.time.ZoneId
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
    fun webMonthNavigationKeepsSelectionSeparateFromTimelineAndCreatesForSelectedDate() {
        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitForIdle()
        val countsBefore = StoreCounts.read(store)
        val initialTimeline = WebSkinBridgeRuntime.state().getJSONObject("timeline")
        val originalAnchor = initialTimeline.getLong("anchorMillis")
        val zone = ZoneId.of(initialTimeline.getString("zoneId"))
        val originalDate = Instant.ofEpochMilli(originalAnchor).atZone(zone).toLocalDate()

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("month")) }
        ui.waitForIdle()
        var state = WebSkinBridgeRuntime.state()
        assertEquals(originalAnchor, state.getJSONObject("timeline").getLong("anchorMillis"))
        assertEquals(originalDate.toString(), state.getJSONObject("month").getString("selectedDate"))

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.SelectMonth("2030-02")) }
        ui.waitForIdle()
        state = WebSkinBridgeRuntime.state()
        assertEquals("2030-02", state.getJSONObject("month").getString("month"))
        assertEquals("2030-02-01", state.getJSONObject("month").getString("selectedDate"))
        assertEquals("2030-01-28", state.getJSONObject("month").getString("gridFrom"))
        assertEquals(originalAnchor, state.getJSONObject("timeline").getLong("anchorMillis"))

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.SelectMonthDate("2030-02-14"))
        }
        ui.waitForIdle()
        assertEquals("2030-02-14", WebSkinBridgeRuntime.state()
            .getJSONObject("month").getString("selectedDate"))

        fun runWebEdit(command: WebEditCommand): JSONObject {
            val response = AtomicReference<JSONObject?>()
            val failure = AtomicReference<Throwable?>()
            Thread({
                try { response.set(runBlocking { WebSkinBridgeRuntime.onEdit(command) }) }
                catch (error: Throwable) { failure.set(error) }
            }, "powercal-web-month-compose-test").apply { isDaemon = true; start() }
            ui.waitUntil(10_000) { response.get() != null || failure.get() != null }
            failure.get()?.let { throw AssertionError("Web month edit command failed", it) }
            return requireNotNull(response.get())
        }

        assertEquals("created", runWebEdit(
            WebEditCommand.CreateDeck("month-selected-date-create", TemporalKind.ACTIVITY)
        ).getString("status"))
        ui.waitForIdle()
        val createdForm = WebSkinBridgeRuntime.state().getJSONObject("editing")
            .getJSONObject("deck").getJSONObject("form")
        assertEquals("2030-02-14", createdForm.getString("startText").take(10))
        assertEquals("closed", runWebEdit(
            WebEditCommand.CloseDeck("month-selected-date-close")
        ).getString("status"))
        ui.waitForIdle()

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.BrowseMonth(1)) }
        ui.waitForIdle()
        state = WebSkinBridgeRuntime.state()
        assertEquals("2030-03", state.getJSONObject("month").getString("month"))
        assertEquals("2030-03-01", state.getJSONObject("month").getString("selectedDate"))
        assertEquals(originalAnchor, state.getJSONObject("timeline").getLong("anchorMillis"))

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.TodayInMonth) }
        ui.waitForIdle()
        state = WebSkinBridgeRuntime.state()
        val today = java.time.LocalDate.now(zone)
        assertEquals(today.toString(), state.getJSONObject("month").getString("selectedDate"))
        assertEquals(originalAnchor, state.getJSONObject("timeline").getLong("anchorMillis"))

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.JumpTimelineDay(today.toString()))
        }
        ui.waitForIdle()
        state = WebSkinBridgeRuntime.state()
        assertEquals("TIMELINE", state.getString("page"))
        assertEquals(today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            state.getJSONObject("timeline").getLong("anchorMillis"))
        assertEquals(countsBefore, StoreCounts.read(store))
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
