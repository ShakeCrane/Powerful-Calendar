package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.ai.AiChatHistoryStore
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.PowerStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PowerCalAiMultiTurnInstrumentedTest {

    private lateinit var context: Context
    private lateinit var storage: Context
    private lateinit var store: PowerStore
    private lateinit var databaseName: String
    private lateinit var namespace: String
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Before fun setUp() {
        storage = ui.activity.applicationContext
        namespace = "pc-ai-multiturn-${UUID.randomUUID()}"
        println("PC_ISSUE_172_NAMESPACE=$namespace")
        context = IsolatedAppContext(ui.activity, storage, namespace)
        databaseName = "powercal-ai-compose.db"
        store = PowerStore(context, databaseName)
        store.readableDatabase
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
        AiChatHistoryStore.save(context, emptyList())
    }

    @After fun tearDown() {
        ui.activity.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
        store.close()
        storage.deleteDatabase("$namespace-$databaseName")
        listOf("powercal-ai", "powercal-onboarding", "powercal-ui", "powercal-skin-choice",
            "powercal-profile").forEach { storage.deleteSharedPreferences("$namespace-$it") }
        if (!File(context.cacheDir, "pc-issue-172-review.png").isFile) {
            File(storage.cacheDir, namespace).deleteRecursively()
        }
        PowerCalSkinRegistry.reset()
    }

    @Test
    fun actualPowerCalChatRetainsFiveTurnIntentUntilReviewAndWritesOnlyChosenDrafts() {
        val turns = listOf(
            "今天 01:45–02:45 创建多个相互重叠的事件，最初误说重复事件。",
            "我的意思是就创建多个就行了，不用重复。",
            "14514",
            "就叫这个。",
            "确认。"
        )
        val chatReplies = listOf(
            "这些事件要按重复日程处理吗？",
            "明白，改成多个单次活动。标题叫什么？",
            "五个事项都使用同一标题吗？",
            "标题已记录，请确认。"
        )
        val marker = """
            [[POWERCAL_DRAFT]]
            {"type":"draft","intent":"explicit","source":"untrusted fake source must not be used"}
            [[/POWERCAL_DRAFT]]
        """.trimIndent()
        val structured = JSONObject().put("events", JSONArray().apply {
            repeat(5) { index ->
                val start = LocalDateTime.of(2026, 10, 25, 1, 45).plusMinutes(index * 5L)
                put(JSONObject()
                    .put("operation", "CREATE")
                    .put("kind", "ACTIVITY")
                    .put("title", "14514")
                    .put("start", start.toString().replace('T', ' '))
                    .put("end", start.plusMinutes(30).toString().replace('T', ' '))
                    .put("recurrence", JSONObject().put("freq", "NONE")))
            }
        }).toString()
        val requests = CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val served = CountDownLatch(6)

        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 15_000
            AiSettingsStore.save(context, AiSettings(
                endpoint = "http://localhost:${server.localPort}/v1",
                model = "local-stub:free",
                apiKey = "LOCAL_TEST_ONLY",
                enabled = true,
                shareContext = false
            ))
            Thread {
                try {
                    repeat(6) { requestIndex ->
                        server.accept().use { socket ->
                            socket.soTimeout = 15_000
                            val input = socket.getInputStream().buffered()
                            fun readLine(): String {
                                val line = java.io.ByteArrayOutputStream()
                                while (true) {
                                    val byte = input.read()
                                    require(byte >= 0) { "unexpected end of HTTP request" }
                                    if (byte == 10) break
                                    if (byte != 13) line.write(byte)
                                    require(line.size() <= 8192) { "oversized HTTP header" }
                                }
                                return line.toString(Charsets.US_ASCII.name())
                            }
                            require(readLine().startsWith("POST /v1/chat/completions HTTP/1.1"))
                            var contentLength = -1
                            while (true) {
                                val header = readLine()
                                if (header.isEmpty()) break
                                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = header.substringAfter(':').trim().toInt()
                                }
                            }
                            require(contentLength in 1..100_000)
                            val body = ByteArray(contentLength)
                            var offset = 0
                            while (offset < body.size) {
                                val count = input.read(body, offset, body.size - offset)
                                require(count > 0) { "truncated HTTP request" }
                                offset += count
                            }
                            requests += String(body, Charsets.UTF_8)
                            val content = when (requestIndex) {
                                in 0..3 -> chatReplies[requestIndex]
                                4 -> "我已按你逐步确认的内容准备了候选。\n$marker"
                                else -> structured
                            }
                            val response = JSONObject().put("choices", JSONArray().put(
                                JSONObject().put("message", JSONObject()
                                    .put("role", "assistant").put("content", content))
                                    .put("finish_reason", "stop")))
                                .toString().toByteArray(Charsets.UTF_8)
                            val headers = ("HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json; charset=utf-8\r\n" +
                                "Content-Length: ${response.size}\r\n" +
                                "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
                            socket.getOutputStream().apply {
                                write(headers)
                                write(response)
                                flush()
                            }
                        }
                        served.countDown()
                    }
                } catch (error: Throwable) {
                    serverFailure.set(error)
                    while (served.count > 0) served.countDown()
                }
            }.apply { isDaemon = true; start() }

            ui.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
            ui.waitForIdle()
            ui.onNodeWithText("AI 助手").performClick()
            ui.waitForIdle()
            turns.forEachIndexed { turnIndex, turn ->
                ui.onNodeWithText("发送消息…").performTextInput(turn)
                ui.onNodeWithText("发送").performClick()
                if (turnIndex < turns.lastIndex) {
                    ui.waitUntil(15_000) {
                        serverFailure.get() != null ||
                            ui.onAllNodesWithText(chatReplies[turnIndex])
                                .fetchSemanticsNodes().isNotEmpty()
                    }
                    assertTrue("local model server failed: ${serverFailure.get()}",
                        serverFailure.get() == null)
                }
            }

            ui.waitUntil(20_000) {
                serverFailure.get() != null || requests.size == 6 &&
                    ui.onAllNodesWithText("14514 · 活动", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("local model server failed: ${serverFailure.get()}", serverFailure.get() == null)
            assertTrue("all chat and structured requests should reach the local stub",
                served.await(1, TimeUnit.SECONDS))
            assertEquals(6, requests.size)
            ui.onNodeWithText("识别到新增 · 是否创建草稿？").assertIsDisplayed()
            assertEquals(5, ui.onAllNodesWithText("14514 · 活动", substring = true)
                .fetchSemanticsNodes().size)
            val reviewCapture = requireNotNull(
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            val captureFile = File(context.cacheDir, "pc-issue-172-review.png")
            check(captureFile.parentFile?.isDirectory == true)
            captureFile.outputStream().use { output ->
                check(reviewCapture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
            println("PC_ISSUE_172_REVIEW_CAPTURE=${captureFile.absolutePath}")
            assertEquals(0L, store.draftCount())
            assertEquals(0, store.pendingCount())
            assertEquals(0L, store.identityCount())
            assertEquals(0L, store.commitCount())

            ui.onNodeWithText("写入草稿").performClick()
            ui.waitUntil(15_000) { store.pendingCount() == 5 }
            val drafts = store.pendingDrafts()
            assertEquals(5, drafts.size)
            assertEquals(5, drafts.map { it.eventSeq }.toSet().size)
            assertTrue(drafts.all { it.form.title == "14514" &&
                it.form.recurrence.freq.name == "NONE" })
            assertTrue(store.all().isEmpty())
            assertEquals(0L, store.commitCount())

            val finalStructuredRequest = JSONObject(requests.last())
                .getJSONArray("messages").getJSONObject(1).getString("content")
            turns.forEach { assertTrue("missing evidence turn $it", finalStructuredRequest.contains(it)) }
            assertTrue(finalStructuredRequest.contains("assistantSuggestionsUntrusted"))
            val firstMessageTime = requireNotNull(AiChatHistoryStore.load(context)
                .first { it.role == "user" }.createdAtMillis)
            val expectedAnchor = Instant.ofEpochMilli(firstMessageTime)
                .atZone(ZoneId.systemDefault()).toLocalDateTime().toString()
            assertTrue(finalStructuredRequest.contains(expectedAnchor))
            assertTrue("model supplied source is not authoritative",
                !finalStructuredRequest.contains("untrusted fake source must not be used"))
        }
    }

    /** Test-owned data paths; never reads or changes the installed app's private calendar. */
    private class IsolatedAppContext(
        target: Context,
        private val backing: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(backing.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory) { "cannot create isolated test data root" }
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
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(databaseAlias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(databaseAlias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = backing.deleteDatabase(databaseAlias(name))
    }
}
