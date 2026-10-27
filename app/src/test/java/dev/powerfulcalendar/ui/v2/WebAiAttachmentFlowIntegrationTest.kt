package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.DatabaseErrorHandler
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.ai.AiFileFallbackConsent
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.PowerStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
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
class WebAiAttachmentFlowIntegrationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private lateinit var storage: Context
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var namespace: String
    private lateinit var databaseName: String
    private val resultRegistry = RecordingActivityResultRegistry()
    private val resultRegistryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = resultRegistry
    }

    @Before fun setUp() {
        storage = ApplicationProvider.getApplicationContext()
        namespace = "waif-" + UUID.randomUUID()
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
        listOf(
            "powercal-ai", "powercal-file-ai-consent", "powercal-onboarding",
            "powercal-ui", "powercal-skin-choice", "powercal-profile"
        ).forEach { storage.deleteSharedPreferences("$namespace-$it") }
        File(storage.cacheDir, namespace).deleteRecursively()
        PowerCalSkinRegistry.reset()
    }

    @Test
    fun skinSelectedFileCanBeRedactedBeforeAiProposalAndDoesNotWriteCalendarData() {
        val zone = ZoneId.of("Asia/Shanghai")
        val tomorrow = LocalDate.now(zone).plusDays(1).toString()
        val sensitiveSource = "PRIVATE_ONLY=REMOVE_ME; Study group tomorrow at 14:00."
        val reviewedText = "Study group tomorrow at 14:00."
        val uri = Uri.parse("content://powercal-test-" + namespace + "/agenda.txt")
        val requests = CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val served = CountDownLatch(1)
        val before = counts()
        val proposal = JSONObject().put("events", JSONArray().put(JSONObject()
            .put("operation", "CREATE")
            .put("kind", "ACTIVITY")
            .put("title", "Study group")
            .put("start", tomorrow + " 14:00")
            .put("end", tomorrow + " 15:00")
            .put("zoneId", zone.id))).toString()

        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 15_000
            val settings = AiSettings(
                endpoint = "http://127.0.0.1:" + server.localPort + "/v1",
                model = "local-stub:free",
                apiKey = "LOCAL_TEST_ONLY",
                enabled = true,
                shareContext = false
            )
            AiSettingsStore.save(context, settings)
            val serverThread = Thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        requests += readRequest(socket)
                        writeReply(socket, proposal)
                    }
                    served.countDown()
                } catch (error: Throwable) {
                    serverFailure.set(error)
                    served.countDown()
                }
            }.apply { name = "pc-skin-attachment-stub"; isDaemon = true; start() }

            ui.activity.setContent {
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalActivityResultRegistryOwner provides resultRegistryOwner
                ) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
            ui.waitForIdle()
            ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("ai")) }
            ui.waitForIdle()

            val contentResolver = shadowOf(ui.activity.contentResolver)
            val sourceBytes = sensitiveSource.toByteArray(Charsets.UTF_8)
            ShadowContentResolver.registerProviderInternal(
                requireNotNull(uri.authority), TestAttachmentProvider("agenda.txt")
            )
            contentResolver.registerInputStreamSupplier(uri) { sourceBytes.inputStream() }

            ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.PickAiFile) }
            ui.waitForIdle()
            assertTrue("Host must mark its picker active before returning a result",
                WebSkinBridgeRuntime.state().getBoolean("chatAttachmentBusy"))
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, resultRegistry.lastIntent?.action)
            assertEquals("*/*", resultRegistry.lastIntent?.type)

            ui.runOnIdle { assertTrue(resultRegistry.dispatch(uri)) }
            fun reviewVisible() = ui.onAllNodesWithText("核对文件 · agenda.txt")
                .fetchSemanticsNodes().isNotEmpty()
            ui.waitUntil(10_000) {
                reviewVisible() || !WebSkinBridgeRuntime.state()
                    .optBoolean("chatAttachmentBusy")
            }
            assertTrue("selected-file review was not shown; state=" +
                WebSkinBridgeRuntime.state().toString(), reviewVisible())
            assertTrue(WebSkinBridgeRuntime.state().getBoolean("chatAttachmentBusy"))
            assertEquals("selection and extraction must not write before consent", before, counts())

            ui.onNode(hasText(sensitiveSource, substring = false) and hasSetTextAction())
                .performTextClearance()
            ui.onNodeWithText("本次将发送的提取文本（可删除隐私内容）")
                .performTextInput(reviewedText)
            ui.onNodeWithText("交给 AI 分析并澄清").performClick()

            var review: JSONObject? = null
            ui.waitUntil(20_000) {
                val candidate = WebSkinBridgeRuntime.state().optJSONObject("aiProposalReview")
                if (candidate?.optJSONArray("proposals")?.length() == 1) {
                    review = candidate
                    true
                } else serverFailure.get() != null
            }
            assertNull("local AI stub failed", serverFailure.get())
            assertNotNull("reviewed file text did not produce an AI proposal", review)
            assertTrue("local stub did not receive the request", served.await(1, TimeUnit.SECONDS))
            serverThread.join(2_000)

            assertEquals(1, requests.size)
            assertTrue("the edited, user-approved text should reach the parser",
                requests.single().contains(reviewedText))
            assertFalse("redacted source text must never reach the provider",
                requests.single().contains("PRIVATE_ONLY=REMOVE_ME"))
            val item = requireNotNull(review).getJSONArray("proposals").getJSONObject(0)
            assertEquals("Study group", item.getString("title"))
            assertEquals("formal data remains unchanged until proposal review",
                before, counts())
            assertFalse("one-time approval must not create remembered provider consent",
                AiFileFallbackConsent.allowed(context, AiSettingsStore.load(context)))
            assertFalse("an unresolved proposal must keep attachment controls locked",
                WebSkinBridgeRuntime.state().getBoolean("chatAttachmentAvailable"))
        }
    }

    private data class Counts(
        val drafts: Long,
        val formalEvents: Int,
        val identities: Long,
        val commits: Long
    )

    private fun counts() = Counts(
        store.draftCount(), store.all().size, store.identityCount(), store.commitCount()
    )

    private fun readRequest(socket: java.net.Socket): String {
        val input = socket.getInputStream().buffered()
        fun line(): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val byte = input.read()
                require(byte >= 0) { "truncated HTTP header" }
                if (byte == 10) break
                if (byte != 13) bytes.write(byte)
                require(bytes.size() <= 8_192) { "oversized HTTP header" }
            }
            return bytes.toString(Charsets.US_ASCII.name())
        }
        require(line().startsWith("POST /v1/chat/completions HTTP/1.1"))
        var contentLength = -1
        while (true) {
            val header = line()
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
            require(count > 0) { "truncated HTTP request body" }
            offset += count
        }
        return String(body, Charsets.UTF_8)
    }

    private fun writeReply(socket: java.net.Socket, content: String) {
        val body = JSONObject().put("choices", JSONArray().put(JSONObject()
            .put("message", JSONObject().put("role", "assistant").put("content", content))
            .put("finish_reason", "stop"))).toString().toByteArray(Charsets.UTF_8)
        val newline = 13.toChar().toString() + 10.toChar()
        val headers = ("HTTP/1.1 200 OK" + newline +
            "Content-Type: application/json; charset=utf-8" + newline +
            "Content-Length: " + body.size + newline +
            "Connection: close" + newline + newline).toByteArray(Charsets.US_ASCII)
        socket.getOutputStream().apply { write(headers); write(body); flush() }
    }

    private class RecordingActivityResultRegistry : ActivityResultRegistry() {
        var requestCode: Int? = null
            private set
        var lastIntent: Intent? = null
            private set

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            this.requestCode = requestCode
            lastIntent = contract.createIntent(
                ApplicationProvider.getApplicationContext(), input
            )
        }

        fun dispatch(uri: Uri): Boolean = dispatchResult(
            requireNotNull(requestCode) { "No Host picker launch was recorded" }, uri
        )
    }

    private class TestAttachmentProvider(private val filename: String) : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun getType(uri: Uri): String = "text/plain"
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME)
            return MatrixCursor(columns).apply {
                addRow(Array<Any?>(columns.size) { index ->
                    if (columns[index] == OpenableColumns.DISPLAY_NAME) filename else null
                })
            }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
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
        override fun deleteDatabase(name: String): Boolean =
            backing.deleteDatabase(databaseAlias(name))
    }
}
