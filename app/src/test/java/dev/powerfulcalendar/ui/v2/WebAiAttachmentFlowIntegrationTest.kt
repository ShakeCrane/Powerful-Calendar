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
import android.util.AtomicFile
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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.ai.AiChatHistoryStore
import dev.powerfulcalendar.ai.AiFileFallbackConsent
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.interop.IcsImportPlanner
import dev.powerfulcalendar.interop.ImportCheckpoint
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ShadowAtomicFileReplaceOnWindows::class])
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
            "powercal-ui", "powercal-skin-choice", "powercal-profile",
            "powercal-import-checkpoints"
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

    @Test
    fun skinPickedIcsCreatesUserDraftsAndSameFileReimportIsIdempotent() {
        val ics = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//PowerCal Test//ICS Import//EN
BEGIN:VEVENT
UID:powercal-s0-st13-kickoff
DTSTART:20261101T010000Z
DTEND:20261101T020000Z
SUMMARY:Separation kickoff
END:VEVENT
BEGIN:VEVENT
UID:powercal-s0-st13-review
DTSTART:20261102T030000Z
DTEND:20261102T033000Z
SUMMARY:Parity review
END:VEVENT
END:VCALENDAR
"""
        val uri = Uri.parse("content://powercal-ics-test-$namespace/calendar.ics")
        val sourceBytes = ics.toByteArray(Charsets.UTF_8)
        val fingerprint = IcsImportPlanner.prepare(ics, ZoneId.of("UTC")).fingerprint
        assertFalse("this isolated fixture must not configure an AI provider",
            AiSettingsStore.load(context).configured)

        ShadowContentResolver.registerProviderInternal(
            requireNotNull(uri.authority), TestAttachmentProvider("calendar.ics", "text/calendar")
        )
        shadowOf(ui.activity.contentResolver).registerInputStreamSupplier(uri) {
            sourceBytes.inputStream()
        }

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

        fun pickTheSameIcs() {
            ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.PickAiFile) }
            ui.waitForIdle()
            assertTrue("Host must mark the attachment picker active",
                WebSkinBridgeRuntime.state().getBoolean("chatAttachmentBusy"))
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, resultRegistry.lastIntent?.action)
            assertEquals("*/*", resultRegistry.lastIntent?.type)
            ui.runOnIdle { assertTrue(resultRegistry.dispatch(uri)) }
            ui.waitUntil(20_000) {
                store.pendingDrafts().size == 2 &&
                    ui.onAllNodesWithContentDescription("保存并退出待处理")
                        .fetchSemanticsNodes().isNotEmpty()
            }
        }

        fun closeDeck() {
            ui.onNodeWithContentDescription("保存并退出待处理").performClick()
            ui.waitUntil(10_000) {
                ui.onAllNodesWithContentDescription("保存并退出待处理")
                    .fetchSemanticsNodes().isEmpty()
            }
        }

        pickTheSameIcs()

        val firstImport = store.pendingDrafts()
        assertEquals(listOf("Separation kickoff", "Parity review"),
            firstImport.map { it.form.title })
        assertTrue("local ICS import must stage CREATE drafts",
            firstImport.all { it.operation == DraftOperation.CREATE &&
                it.status == DraftStatus.PENDING && it.form.kind == TemporalKind.ACTIVITY &&
                it.form.source == "ics" })
        assertEquals(2L, store.draftCount())
        assertTrue("ICS import must not publish formal events", store.all().isEmpty())
        assertEquals("draft creation must not create formal commit records", 0L, store.commitCount())

        val matchingReceipt = ImportCheckpoint.recent(context)
            .filter { it.fingerprint == fingerprint }
        assertEquals("the local import must persist exactly one matching receipt",
            1, matchingReceipt.size)
        assertEquals(firstImport.map { it.draftId }, matchingReceipt.single().draftIds)
        assertEquals(0, matchingReceipt.single().problemCount)
        val afterFirstImport = counts()
        val nextSequenceAfterFirstImport = store.nextSequencePreview()

        closeDeck()
        pickTheSameIcs()
        closeDeck()

        assertEquals("re-import must not create duplicate drafts", firstImport,
            store.pendingDrafts())
        assertEquals("re-import must not allocate identities or commit events",
            afterFirstImport, counts())
        assertEquals("re-import must not advance permanent event numbering",
            nextSequenceAfterFirstImport, store.nextSequencePreview())
        assertTrue("re-import must still leave formal events untouched", store.all().isEmpty())
        val receiptsAfterRetry = ImportCheckpoint.recent(context)
            .filter { it.fingerprint == fingerprint }
        assertEquals("a retry updates the same receipt instead of appending another",
            1, receiptsAfterRetry.size)
        assertEquals(firstImport.map { it.draftId }, receiptsAfterRetry.single().draftIds)
    }

    @Test
    fun skinPickedOversizedIcsIsRejectedBeforeAnyCalendarPersistence() {
        val uri = Uri.parse("content://powercal-ics-test-$namespace/too-large.ics")
        val sourceBytes = ByteArray(2_000_001) { 'X'.code.toByte() }
        val before = counts()
        val nextSequenceBefore = store.nextSequencePreview()
        assertFalse("oversize fixture must not configure an AI provider",
            AiSettingsStore.load(context).configured)

        ShadowContentResolver.registerProviderInternal(
            requireNotNull(uri.authority), TestAttachmentProvider("too-large.ics", "text/calendar")
        )
        shadowOf(ui.activity.contentResolver).registerInputStreamSupplier(uri) {
            sourceBytes.inputStream()
        }

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
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.PickAiFile) }
        ui.waitForIdle()
        assertTrue(WebSkinBridgeRuntime.state().getBoolean("chatAttachmentBusy"))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, resultRegistry.lastIntent?.action)
        ui.runOnIdle { assertTrue(resultRegistry.dispatch(uri)) }
        ui.waitUntil(20_000) {
            !WebSkinBridgeRuntime.state().getBoolean("chatAttachmentBusy")
        }

        assertEquals("oversize input must not add drafts or formal events", before, counts())
        assertEquals("rejected input must not allocate a permanent event identity",
            nextSequenceBefore, store.nextSequencePreview())
        assertTrue(store.pendingDrafts().isEmpty())
        assertTrue(store.all().isEmpty())
        assertTrue("rejected input must not create an import receipt",
            ImportCheckpoint.recent(context).isEmpty())
    }

    @Test
    fun skinImageRequiresExplicitUploadConsentAndNeverStoresRawImageInChatHistory() {
        val imageBytes = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/nRsAAAAASUVORK5CYII=",
            android.util.Base64.DEFAULT
        )
        val encodedImage = android.util.Base64.encodeToString(
            imageBytes, android.util.Base64.NO_WRAP
        )
        val uri = Uri.parse("content://powercal-image-test-" + namespace + "/tiny.png")
        val requests = CopyOnWriteArrayList<String>()
        val requestReceived = CountDownLatch(1)
        val serverFailure = AtomicReference<Throwable?>()
        val before = counts()

        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 15_000
            AiSettingsStore.save(context, AiSettings(
                endpoint = "http://127.0.0.1:" + server.localPort + "/v1",
                model = "local-stub:free",
                apiKey = "LOCAL_TEST_ONLY",
                enabled = true,
                shareContext = false
            ))
            val serverThread = Thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        requests += readRequest(socket)
                        requestReceived.countDown()
                        writeReply(socket, "图片内容已读取，请核对日期和时间。")
                    }
                } catch (error: Throwable) {
                    serverFailure.set(error)
                    requestReceived.countDown()
                }
            }.apply { name = "pc-skin-image-stub"; isDaemon = true; start() }

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

            shadowOf(ui.activity.contentResolver).registerInputStreamSupplier(uri) {
                imageBytes.inputStream()
            }
            ShadowContentResolver.registerProviderInternal(
                requireNotNull(uri.authority), TestAttachmentProvider("tiny.png", "image/png")
            )

            fun chooseImageAndWaitForConsent() {
                ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.PickAiImage) }
                ui.waitForIdle()
                assertEquals(Intent.ACTION_GET_CONTENT, resultRegistry.lastIntent?.action)
                assertEquals("image/*", resultRegistry.lastIntent?.type)
                assertTrue(WebSkinBridgeRuntime.state().getBoolean("chatAttachmentBusy"))
                ui.runOnIdle { assertTrue(resultRegistry.dispatch(uri)) }
                ui.waitUntil(10_000) {
                    ui.onAllNodesWithText("图片识别方式")
                        .fetchSemanticsNodes().isNotEmpty()
                }
            }

            chooseImageAndWaitForConsent()
            assertEquals("picker result alone must not write calendar data", before, counts())
            assertFalse(
                "the provider must receive no image before consent",
                requestReceived.await(250, TimeUnit.MILLISECONDS)
            )
            assertTrue(AiChatHistoryStore.load(context).all {
                !it.content.contains(encodedImage)
            })

            ui.onNodeWithText("取消").performClick()
            ui.waitUntil(5_000) {
                ui.onAllNodesWithText("图片识别方式")
                    .fetchSemanticsNodes().isEmpty() &&
                    WebSkinBridgeRuntime.state().optBoolean("chatAttachmentAvailable")
            }
            assertFalse(
                "cancel must not upload image bytes",
                requestReceived.await(250, TimeUnit.MILLISECONDS)
            )
            assertEquals("cancel must leave calendar data unchanged", before, counts())
            assertTrue(AiChatHistoryStore.load(context).all {
                !it.content.contains(encodedImage)
            })

            chooseImageAndWaitForConsent()
            ui.onNodeWithText("发送原图给 AI").performClick()
            assertTrue("explicitly authorized image request was not received",
                requestReceived.await(20, TimeUnit.SECONDS))
            assertNull("local image stub failed", serverFailure.get())
            serverThread.join(2_000)

            assertEquals(1, requests.size)
            val request = JSONObject(requests.single())
            val messages = request.getJSONArray("messages")
            val userMessage = (0 until messages.length()).map { messages.getJSONObject(it) }
                .last { it.getString("role") == "user" }
            val userContent = userMessage.getJSONArray("content")
            val imagePart = (0 until userContent.length()).map { userContent.getJSONObject(it) }
                .first { it.optString("type") == "image_url" }
            assertEquals("image upload must carry the selected image data URI",
                "data:image/png;base64,$encodedImage",
                imagePart.getJSONObject("image_url").getString("url"))
            ui.waitUntil(10_000) {
                !WebSkinBridgeRuntime.state().optBoolean("chatBusy")
            }
            val projectedMessages = WebSkinBridgeRuntime.state()
                .optJSONArray("chatMessages") ?: JSONArray()
            assertTrue("image consent marker missing from Host chat projection: " +
                WebSkinBridgeRuntime.state().toString(),
                (0 until projectedMessages.length()).any { index ->
                    projectedMessages.getJSONObject(index).getString("content")
                        .contains("已授权 AI 读取图片")
                })
            val historyFile = File(context.filesDir, "powercal-chat-history.json")
            ui.waitUntil(5_000) {
                AiChatHistoryStore.load(context).any {
                    it.content.contains("已授权 AI 读取图片")
                }
            }
            val history = AiChatHistoryStore.load(context)
            assertTrue("history should retain the consent marker", history.any {
                it.content.contains("已授权 AI 读取图片")
            })
            assertTrue("raw image bytes must never be persisted in chat history",
                history.all { !it.content.contains(encodedImage) })
            val persistedHistory = awaitFileContents(historyFile, 5_000) {
                it.contains("已授权 AI 读取图片") && !it.contains(encodedImage)
            }
            assertFalse("raw image bytes must not appear in the history file",
                persistedHistory.contains(encodedImage))

            ui.onNodeWithText("待处理 0").performClick()
            ui.onNodeWithText("清空聊天（不改变事件）").performClick()
            ui.waitForIdle()
            val clearedProjection = WebSkinBridgeRuntime.state()
                .optJSONArray("chatMessages") ?: JSONArray()
            assertEquals("clear action must update Host projection", 0, clearedProjection.length())
            val clearedHistory = awaitFileContents(historyFile, 5_000) { it == "[]" }
            assertEquals("clearing chat must persist the empty history", "[]", clearedHistory)
            assertEquals("image analysis must not write calendar data", before, counts())
        }
    }

    @Test
    fun skinChatFailureCanBeRetriedWithSafeContext() {
        val firstPrompt = "请帮我概括明天的安排。"
        val retryPrompt = firstPrompt
        val providerSecret = "PROVIDER_SECRET_ECHO_DO_NOT_PERSIST"
        val requests = CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val firstResponseSent = CountDownLatch(1)
        val secondResponseSent = CountDownLatch(1)
        val before = counts()

        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 20_000
            AiSettingsStore.save(context, AiSettings(
                endpoint = "http://127.0.0.1:" + server.localPort + "/v1",
                model = "local-stub:free",
                apiKey = "LOCAL_TEST_ONLY",
                enabled = true,
                shareContext = false
            ))
            val serverThread = Thread {
                try {
                    repeat(2) { index ->
                        server.accept().use { socket ->
                            socket.soTimeout = 10_000
                            requests += readRequest(socket)
                            if (index == 0) {
                                writeErrorReply(socket, 503,
                                    "{\"error\":\"$providerSecret\"}")
                                firstResponseSent.countDown()
                            } else {
                                writeReply(socket, "连接已恢复，我继续回答刚才的问题。")
                                secondResponseSent.countDown()
                            }
                        }
                    }
                } catch (error: Throwable) {
                    serverFailure.set(error)
                    firstResponseSent.countDown()
                    secondResponseSent.countDown()
                }
            }.apply { name = "pc-skin-chat-retry-stub"; isDaemon = true; start() }

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
            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.SendChat(firstPrompt))
            }

            fun chatMessages() = WebSkinBridgeRuntime.state()
                .optJSONArray("chatMessages") ?: JSONArray()
            ui.waitUntil(15_000) {
                !WebSkinBridgeRuntime.state().optBoolean("chatBusy") &&
                    (0 until chatMessages().length()).any { index ->
                        chatMessages().getJSONObject(index).optString("content")
                            .contains("AI 请求未完成")
                    }
            }
            assertTrue("the first failed request did not reach the local provider",
                firstResponseSent.await(1, TimeUnit.SECONDS))
            assertNull("local provider stub failed", serverFailure.get())
            assertEquals("HTTP failure must not trigger a hidden automatic retry", 1, requests.size)
            val failedConversation = WebSkinBridgeRuntime.state()
            val failedMessages = failedConversation.optJSONArray("chatMessages") ?: JSONArray()
            assertTrue((0 until failedMessages.length()).any { index ->
                failedMessages.getJSONObject(index).optString("content")
                    .contains("HTTP 503")
            })
            assertFalse("provider response body must not be shown to the user",
                failedConversation.toString().contains(providerSecret))
            ui.waitUntil(5_000) {
                AiChatHistoryStore.load(context).any {
                    it.content.contains("AI 请求未完成")
                }
            }

            // Re-sending the same user request is an explicit retry with safe local history.
            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.SendChat(retryPrompt))
            }
            assertTrue("explicit retry did not reach the local provider",
                secondResponseSent.await(15, TimeUnit.SECONDS))
            assertNull("local provider stub failed during retry", serverFailure.get())
            ui.waitUntil(15_000) {
                !WebSkinBridgeRuntime.state().optBoolean("chatBusy") &&
                    WebSkinBridgeRuntime.state().toString()
                        .contains("连接已恢复，我继续回答刚才的问题。")
            }
            serverThread.join(2_000)

            assertEquals(2, requests.size)
            val retryMessages = JSONObject(requests[1]).getJSONArray("messages")
            val history = (0 until retryMessages.length()).map { retryMessages.getJSONObject(it) }
            assertTrue("retry must keep the original user turn",
                history.any { it.optString("role") == "user" &&
                    it.optString("content") == firstPrompt })
            assertTrue("retry context must contain the sanitized error, not the provider body",
                history.any { it.optString("role") == "assistant" &&
                    it.optString("content").contains("HTTP 503") })
            assertTrue("retry must include the new user turn",
                history.any { it.optString("role") == "user" &&
                    it.optString("content") == retryPrompt })
            assertFalse("provider error body must not enter the next request",
                requests[1].contains(providerSecret))
            ui.waitUntil(5_000) {
                AiChatHistoryStore.load(context).any {
                    it.content.contains("连接已恢复，我继续回答刚才的问题。")
                }
            }
            assertTrue("provider error body must not persist in chat history",
                AiChatHistoryStore.load(context).all { !it.content.contains(providerSecret) })
            assertEquals("failed chat and retry must not write calendar data", before, counts())
        }
    }

    @Test
    fun chatHistoryStoreReplacesLongerSnapshotWithShorterAndEmptyHistory() {
        val emptyProbeContext = IsolatedContext(
            ui.activity, storage, "$namespace/empty-probe"
        )
        val historyFile = File(emptyProbeContext.filesDir, "powercal-chat-history.json")
        AiChatHistoryStore.save(emptyProbeContext, listOf(
            dev.powerfulcalendar.ai.AiChatMessage(role = "user", content = "before-clear")
        ))
        assertTrue(historyFile.readText().contains("before-clear"))

        AiChatHistoryStore.save(emptyProbeContext, listOf(
            dev.powerfulcalendar.ai.AiChatMessage(role = "user", content = "short")
        ))
        val shorterSnapshot = historyFile.readText()
        assertTrue("shorter snapshot should be written: $shorterSnapshot",
            shorterSnapshot.contains("short"))
        assertFalse("shorter snapshot must remove old content",
            shorterSnapshot.contains("before-clear"))

        AiChatHistoryStore.save(emptyProbeContext, emptyList())

        assertEquals("empty history must replace the previous AtomicFile contents",
            "[]", historyFile.readText())
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

    private fun awaitFileContents(
        file: File,
        timeoutMs: Long,
        matches: (String) -> Boolean
    ): String {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var lastContents: String? = null
        var lastReadError: IOException? = null
        while (System.nanoTime() < deadline) {
            try {
                lastContents = file.readText()
                lastReadError = null
                if (matches(lastContents)) return lastContents
            } catch (error: IOException) {
                lastReadError = error
            }
            Thread.sleep(25)
        }
        val failure = AssertionError(
            "Timed out waiting for ${file.name}; last contents=$lastContents"
        )
        lastReadError?.let(failure::initCause)
        throw failure
    }

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

    private fun writeErrorReply(socket: java.net.Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val newline = 13.toChar().toString() + 10.toChar()
        val headers = ("HTTP/1.1 $status Service Unavailable" + newline +
            "Content-Type: application/json; charset=utf-8" + newline +
            "Content-Length: " + bytes.size + newline +
            "Connection: close" + newline + newline).toByteArray(Charsets.US_ASCII)
        socket.getOutputStream().apply { write(headers); write(bytes); flush() }
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

    private class TestAttachmentProvider(
        private val filename: String,
        private val mimeType: String = "text/plain"
    ) : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun getType(uri: Uri): String = mimeType
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

/**
 * Android AtomicFile commits by renaming its `.new` file over the base file. Java's Windows
 * File.renameTo does not replace an existing target, so Robolectric on Windows otherwise leaves
 * old chat JSON in place. Model Android's replace semantics without changing production code.
 */
@Implements(AtomicFile::class)
class ShadowAtomicFileReplaceOnWindows {
    private lateinit var baseFile: File
    private val newFile: File
        get() = File(baseFile.parentFile, baseFile.name + ".new")
    private fun fileLock(): ReentrantLock =
        fileLocks.computeIfAbsent(baseFile.absolutePath) { ReentrantLock() }

    private class LockedFileInputStream(
        file: File,
        private val lock: ReentrantLock
    ) : FileInputStream(file) {
        private val released = AtomicBoolean(false)

        override fun close() {
            try {
                super.close()
            } finally {
                if (released.compareAndSet(false, true)) lock.unlock()
            }
        }
    }

    private companion object {
        val fileLocks = ConcurrentHashMap<String, ReentrantLock>()
    }

    @Implementation
    fun __constructor__(baseName: File) {
        baseFile = baseName
    }

    @Implementation
    fun startWrite(): FileOutputStream {
        baseFile.parentFile?.mkdirs()
        return FileOutputStream(newFile)
    }

    @Implementation
    fun finishWrite(stream: FileOutputStream?) {
        if (stream == null) return
        val lock = fileLock()
        lock.lock()
        try {
            stream.fd.sync()
            stream.close()
            Files.move(newFile.toPath(), baseFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            lock.unlock()
        }
    }

    @Implementation
    fun failWrite(stream: FileOutputStream?) {
        stream?.close()
        newFile.delete()
    }

    @Implementation
    fun openRead(): FileInputStream {
        val lock = fileLock()
        lock.lock()
        return try {
            // Read the last committed base while keeping any in-flight .new file intact.
            LockedFileInputStream(baseFile, lock)
        } catch (error: Exception) {
            lock.unlock()
            throw error
        }
    }
}
