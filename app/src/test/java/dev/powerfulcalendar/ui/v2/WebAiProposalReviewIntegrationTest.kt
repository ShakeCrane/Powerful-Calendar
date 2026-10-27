package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.PowerStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebAiProposalReviewIntegrationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private lateinit var storage: Context
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var namespace: String
    private lateinit var databaseName: String

    @Before fun setUp() {
        storage = ApplicationProvider.getApplicationContext()
        namespace = "pc-web-ai-review-${UUID.randomUUID()}"
        databaseName = "$namespace.db"
        context = IsolatedContext(ui.activity, storage, namespace)
        store = PowerStore(storage, databaseName)
        store.readableDatabase
        PowerCalSkinRegistry.reset()
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

    @Test
    fun explicitHostActionPublishesOnlyAfterFullTrustedConfirmation() {
        val zone = ZoneId.of("Asia/Shanghai")
        val tomorrow = LocalDate.now(zone).plusDays(1)
        val date = tomorrow.toString()
        val userText = "明天14:00到15:00创建一个活动，标题是自动提案测试。"
        val structured = JSONObject().put("events", JSONArray().put(JSONObject()
            .put("operation", "CREATE")
            .put("kind", "ACTIVITY")
            .put("title", "自动提案测试")
            .put("start", "$date 14:00")
            .put("end", "$date 15:00")
            .put("zoneId", zone.id))).toString()
        val requests = CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val served = CountDownLatch(2)
        val before = counts()

        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 15_000
            AiSettingsStore.save(context, AiSettings(
                endpoint = "http://127.0.0.1:${server.localPort}/v1",
                model = "local-stub:free",
                apiKey = "LOCAL_TEST_ONLY",
                enabled = true,
                shareContext = false
            ))
            val responses = listOf(
                "我会先生成一项待核对提案。" +
                    "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
                structured
            )
            val serverThread = Thread {
                try {
                    repeat(responses.size) { index ->
                        server.accept().use { socket ->
                            socket.soTimeout = 10_000
                            requests += readRequest(socket)
                            writeReply(socket, responses[index])
                        }
                        served.countDown()
                    }
                } catch (error: Throwable) {
                    serverFailure.set(error)
                    served.countDown()
                }
            }.apply { name = "pc-web-ai-review-stub"; isDaemon = true; start() }

            ui.activity.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
            ui.waitForIdle()
            ui.onNodeWithText("AI 助手").performClick()
            ui.onNodeWithText("发送消息…").performTextInput(userText)
            ui.onNodeWithText("发送").performClick()

            var proposalState: JSONObject? = null
            ui.waitUntil(20_000) {
                val review = WebSkinBridgeRuntime.state().optJSONObject("aiProposalReview")
                if (review?.optJSONArray("proposals")?.length() == 1) {
                    proposalState = review
                    true
                } else serverFailure.get() != null
            }
            assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
            assertNotNull("AI interpretation did not produce the review projection", proposalState)
            val review = requireNotNull(proposalState)
            val proposal = review.getJSONArray("proposals").getJSONObject(0)
            assertEquals("自动提案测试", proposal.getString("title"))
            assertEquals(before, counts())
            assertTrue("both AI requests must finish", served.await(1, TimeUnit.SECONDS))
            serverThread.join(2_000)
            assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
            assertEquals(2, requests.size)

            val publishAction = WebSkinAction.ResolveAiProposal(
                reviewId = review.getString("reviewId"),
                decision = AiProposalDecision.PUBLISH,
                indices = listOf(0)
            )
            ui.runOnIdle { WebSkinBridgeRuntime.onAction(publishAction) }
            ui.onNodeWithText("确认 AI 批量正式操作").assertExists()
            ui.onNodeWithText("开始：（空） → $date 14:00").assertExists()
            assertEquals("the proposal remains review-only before Host confirmation", before, counts())

            ui.onNodeWithText("取消").performClick()
            ui.waitForIdle()
            assertTrue(ui.onAllNodesWithText("确认 AI 批量正式操作")
                .fetchSemanticsNodes().isEmpty())
            assertEquals("dismissal must preserve the proposal and all store counters", before, counts())
            assertEquals(review.getString("reviewId"), WebSkinBridgeRuntime.state()
                .getJSONObject("aiProposalReview").getString("reviewId"))

            ui.runOnIdle { WebSkinBridgeRuntime.onAction(publishAction) }
            ui.onNodeWithText("确认 AI 批量正式操作").assertExists()
            assertEquals("retry still does not publish before confirmation", before, counts())
            ui.onNodeWithText("确认并提交 1 项").performClick()
            ui.waitUntil(15_000) { store.all().size == 1 }
            val committed = store.all().single()
            assertEquals("自动提案测试", committed.title)
            assertEquals("formal AI publish retains one resolved audit draft", 1L, store.draftCount())
            assertEquals(0, store.pendingCount())
            assertEquals(1L, store.commitCount())
            assertEquals(1L, store.identityCount())
        }
    }

    private data class Counts(
        val drafts: Long,
        val formalEvents: Int,
        val identities: Long,
        val commits: Long
    )

    private fun counts() = Counts(store.draftCount(), store.all().size,
        store.identityCount(), store.commitCount())

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
        require(line().startsWith("POST /v1/chat/completions HTTP/1.1"))
        var contentLength = -1
        while (true) {
            val header = line()
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true))
                contentLength = header.substringAfter(':').trim().toInt()
        }
        require(contentLength in 1..100_000)
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < body.size) {
            val count = input.read(body, offset, body.size - offset)
            require(count > 0) { "truncated HTTP request body" }
            offset += count
        }
        return String(body, Charsets.UTF_8)
    }

    private fun writeReply(socket: java.net.Socket, content: String) {
        val body = JSONObject().put("choices", JSONArray().put(JSONObject()
            .put("message", JSONObject().put("role", "assistant").put("content", content))
            .put("finish_reason", "stop"))).toString().toByteArray(Charsets.UTF_8)
        val headers = ("HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
        socket.getOutputStream().apply { write(headers); write(body); flush() }
    }

    private class IsolatedContext(
        target: Context,
        private val backing: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(backing.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }
        private fun databaseAlias(name: String) = "$namespace-$name"
        private fun databasePath(name: String): File = backing.getDatabasePath(databaseAlias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            backing.getSharedPreferences("$namespace-$name", mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = databasePath(name)
        override fun openOrCreateDatabase(name: String, mode: Int,
            factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(databaseAlias(name), mode, factory)
        }
        override fun openOrCreateDatabase(name: String, mode: Int,
            factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(databaseAlias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = backing.deleteDatabase(databaseAlias(name))
    }
}
