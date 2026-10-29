package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
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
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.PowerStore.CommitOutcome
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
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
        namespace = "wair-${UUID.randomUUID()}"
        // Robolectric embeds the full method name in its Windows temp path.
        databaseName = "a.db"
        context = IsolatedContext(ui.activity, storage, namespace)
        store = PowerStore(context, databaseName)
        store.readableDatabase
        PowerCalSkinRegistry.reset()
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
    }

    @Test
    fun memoPagingContinuesTheSortedProjectionWithoutStoreWrites() {
        val zone = ZoneId.of("Asia/Shanghai")
        repeat(251) { index ->
            seedMemo(String.format(Locale.ROOT, "Paging Memo %03d", index + 1), zone)
        }
        assertEquals(251, TodoProjection.entries(store.all(), System.currentTimeMillis(),
            includeCompleted = true).size)
        val beforePaging = counts()

        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitForIdle()
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("timeline")) }
        ui.waitForIdle()
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.LoadMoreMemo) }
        ui.waitForIdle()
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("memo")) }
        try {
            ui.waitUntil(60_000) {
                val state = WebSkinBridgeRuntime.state()
                state.optString("page") == "MEMO" && state.optInt("memoCount") == 251 &&
                    state.optJSONArray("memoEntries")?.length() == 250 &&
                    state.optBoolean("memoTruncated") && state.optInt("memoRemainingCount") == 1
            }
        } catch (error: Throwable) {
            throw AssertionError("Memo Host state after navigation: ${WebSkinBridgeRuntime.state()}", error)
        }
        val firstRows = WebSkinBridgeRuntime.state().getJSONArray("memoEntries")
        assertEquals("Paging Memo 001", firstRows.getJSONObject(0).getString("title"))
        assertEquals("Paging Memo 250", firstRows.getJSONObject(249).getString("title"))

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.LoadMoreMemo) }
        ui.waitForIdle()
        ui.waitUntil(20_000) {
            val state = WebSkinBridgeRuntime.state()
            state.optJSONArray("memoEntries")?.length() == 251 &&
                !state.optBoolean("memoTruncated") && state.optInt("memoRemainingCount") == 0
        }
        val allRows = WebSkinBridgeRuntime.state().getJSONArray("memoEntries")
        assertEquals("Paging Memo 251", allRows.getJSONObject(250).getString("title"))
        assertEquals(beforePaging, counts())

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.LoadMoreMemo) }
        ui.waitForIdle()
        assertEquals(251, WebSkinBridgeRuntime.state().getJSONArray("memoEntries").length())
        assertEquals(beforePaging, counts())
    }

    @After fun tearDown() {
        ui.activity.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
        store.close()
        context.deleteDatabase(databaseName)
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
            assertFalse("attachments must wait until the active proposal review is resolved",
                WebSkinBridgeRuntime.state().getBoolean("chatAttachmentAvailable"))
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

    @Test
    fun replaceAndDeleteCandidatesCommitOnlyAfterTrustedHostConfirmation() {
        val zone = ZoneId.of("Asia/Shanghai")
        val replaceTarget = seedMemo("原会议标题", zone)
        val deleteTarget = seedMemo("过期提醒", zone)
        val userText = "帮我创建一个正式事项处理提案，2026-12-01：把备忘 #${replaceTarget.eventSeq} " +
            "标题改成项目回顾，并删除备忘 #${deleteTarget.eventSeq}。"
        val structured = JSONObject().put("events", JSONArray()
            .put(JSONObject()
                .put("operation", "REPLACE")
                .put("eventSeq", replaceTarget.eventSeq)
                .put("kind", "MEMO")
                .put("title", "项目回顾"))
            .put(JSONObject()
                .put("operation", "DELETE")
                .put("eventSeq", deleteTarget.eventSeq)
                .put("kind", "MEMO")
                .put("title", deleteTarget.title))).toString()
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
                shareContext = true
            ))
            val responses = listOf(
                "我会生成两项待核对操作。" +
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
                    while (served.count > 0) served.countDown()
                }
            }.apply { name = "pc-web-ai-replace-delete-stub"; isDaemon = true; start() }

            ui.activity.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
            ui.waitForIdle()
            ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("ai")) }
            ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.SendChat(userText)) }

            var reviewState: JSONObject? = null
            ui.waitUntil(20_000) {
                val state = WebSkinBridgeRuntime.state()
                val review = state.optJSONObject("aiProposalReview")
                if (review?.optJSONArray("proposals")?.length() == 2 &&
                    !state.optBoolean("chatBusy")) {
                    reviewState = review
                    true
                } else serverFailure.get() != null
            }
            assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
            val review = requireNotNull(reviewState) { "Host did not project both operations" }
            val proposals = review.getJSONArray("proposals")
            assertEquals("REPLACE", proposals.getJSONObject(0).getString("operation"))
            assertEquals("DELETE", proposals.getJSONObject(1).getString("operation"))
            assertEquals(replaceTarget.revision, proposals.getJSONObject(0).getLong("targetRevision"))
            assertEquals(deleteTarget.revision, proposals.getJSONObject(1).getLong("targetRevision"))
            assertEquals("proposal review is read-only", before, counts())
            assertTrue(served.await(1, TimeUnit.SECONDS))
            serverThread.join(2_000)
            assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())

            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.ResolveAiProposal(
                    reviewId = review.getString("reviewId"),
                    decision = AiProposalDecision.PUBLISH,
                    indices = listOf(0, 1)
                ))
            }
            ui.onNodeWithText("确认 AI 批量正式操作").assertExists()
            ui.onNodeWithText("确认并提交 2 项").assertExists()
            ui.onNodeWithText("#${replaceTarget.eventSeq} · 版本 ${replaceTarget.revision}",
                substring = true).assertExists()
            ui.onNodeWithText("#${deleteTarget.eventSeq} · 版本 ${deleteTarget.revision}",
                substring = true).assertExists()
            assertEquals("REPLACE and DELETE remain unapplied before Host confirmation", before, counts())

            ui.onNodeWithText("确认并提交 2 项").performClick()
            ui.waitUntil(15_000) {
                store.get(replaceTarget.eventSeq)?.title == "项目回顾" &&
                    store.get(deleteTarget.eventSeq) == null
            }
            assertEquals("the existing event keeps its identity while its revision advances",
                replaceTarget.eventSeq, store.get(replaceTarget.eventSeq)?.eventSeq)
            assertEquals(replaceTarget.revision + 1, store.get(replaceTarget.eventSeq)?.revision)
            assertEquals(1, store.all().size)
            assertEquals(0, store.pendingCount())
            assertEquals(before.commits + 2, store.commitCount())
            assertEquals(before.identities, store.identityCount())
        }
    }

    @Test
    fun replaceConfirmationRejectsTargetChangedAfterReviewWithoutApplyingAiWrite() {
        val zone = ZoneId.of("Asia/Shanghai")
        val target = seedMemo("原有标题", zone)
        val userText = "帮我创建一个事项处理提案，2026-12-01：把备忘 #${target.eventSeq} 标题改成 AI 标题。"
        val structured = JSONObject().put("events", JSONArray().put(JSONObject()
            .put("operation", "REPLACE")
            .put("eventSeq", target.eventSeq)
            .put("kind", "MEMO")
            .put("title", "AI 标题"))).toString()
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
                shareContext = true
            ))
            val responses = listOf(
                "我会生成一项待核对操作。" +
                    "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
                structured
            )
            val serverThread = Thread {
                try {
                    repeat(responses.size) { index ->
                        server.accept().use { socket ->
                            socket.soTimeout = 10_000
                            readRequest(socket)
                            writeReply(socket, responses[index])
                        }
                        served.countDown()
                    }
                } catch (error: Throwable) {
                    serverFailure.set(error)
                    while (served.count > 0) served.countDown()
                }
            }.apply { name = "pc-web-ai-stale-target-stub"; isDaemon = true; start() }

            ui.activity.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
            ui.waitForIdle()
            ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("ai")) }
            ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.SendChat(userText)) }

            var reviewState: JSONObject? = null
            ui.waitUntil(20_000) {
                val state = WebSkinBridgeRuntime.state()
                val review = state.optJSONObject("aiProposalReview")
                if (review?.optJSONArray("proposals")?.length() == 1 &&
                    !state.optBoolean("chatBusy")) {
                    reviewState = review
                    true
                } else serverFailure.get() != null
            }
            assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
            val review = requireNotNull(reviewState) { "Host did not project the REPLACE proposal" }
            assertEquals("review creation does not mutate the formal event", before, counts())
            assertTrue(served.await(1, TimeUnit.SECONDS))
            serverThread.join(2_000)
            assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())

            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.ResolveAiProposal(
                    reviewId = review.getString("reviewId"),
                    decision = AiProposalDecision.PUBLISH,
                    indices = listOf(0)
                ))
            }
            ui.onNodeWithText("确认 AI 批量正式操作").assertExists()
            assertEquals("proposal remains unchanged before a concurrent edit", before, counts())

            val concurrentDraft = store.newDraft(
                DraftForm.from(target).copy(title = "并发更新"),
                eventSeq = target.eventSeq,
                operation = dev.powerfulcalendar.core.DraftOperation.REPLACE,
                actor = "user"
            )
            val concurrentCommit = store.commit(concurrentDraft.draftId, actor = "user")
            assertTrue(concurrentCommit is CommitOutcome.Committed)
            val countAfterConcurrentWrite = counts()
            assertEquals("并发修改建立了新版本", target.revision + 1,
                store.get(target.eventSeq)?.revision)

            ui.onNodeWithText("确认并提交 1 项").performClick()
            ui.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                !WebSkinBridgeRuntime.state().optBoolean("chatBusy")
            }
            val afterStaleConfirm = WebSkinBridgeRuntime.state()
            assertTrue("Host must report the stale target and reject the AI write: $afterStaleConfirm",
                afterStaleConfirm.getJSONArray("chatMessages").toString()
                    .contains("确认期间目标事件已变化"))
            assertEquals("concurrent user version remains authoritative", "并发更新",
                store.get(target.eventSeq)?.title)
            assertEquals("Host conflict handling adds no AI write", countAfterConcurrentWrite, counts())
            assertEquals("the stale proposal remains available for review", review.getString("reviewId"),
                WebSkinBridgeRuntime.state().getJSONObject("aiProposalReview").getString("reviewId"))
        }
    }

    @Test
    fun partialStageKeepsUnselectedProposalAndRejectsStaleReviewActions() {
        val userText = "创建两条独立备忘：第一条和第二条。"
        val structured = JSONObject().put("events", JSONArray()
            .put(JSONObject()
                .put("operation", "CREATE")
                .put("kind", "MEMO")
                .put("title", "第一条备忘"))
            .put(JSONObject()
                .put("operation", "CREATE")
                .put("kind", "MEMO")
                .put("title", "第二条备忘"))).toString()
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
                "我会先生成两项待核对提案。" +
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
            }.apply { name = "pc-web-ai-partial-review-stub"; isDaemon = true; start() }

            ui.activity.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
            ui.waitForIdle()
            ui.onNodeWithText("AI 助手").performClick()
            ui.onNodeWithText("发送消息…").performTextInput(userText)
            ui.onNodeWithText("发送").performClick()

            var initialReview: JSONObject? = null
            ui.waitUntil(20_000) {
                val review = WebSkinBridgeRuntime.state().optJSONObject("aiProposalReview")
                if (review?.optJSONArray("proposals")?.length() == 2) {
                    initialReview = review
                    true
                } else serverFailure.get() != null
            }
            assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
            val reviewBeforeStage = requireNotNull(initialReview) {
                "AI interpretation did not produce two proposal cards"
            }
            assertEquals("第一条备忘", reviewBeforeStage.getJSONArray("proposals")
                .getJSONObject(0).getString("title"))
            assertEquals("第二条备忘", reviewBeforeStage.getJSONArray("proposals")
                .getJSONObject(1).getString("title"))
            assertEquals("review is read-only until an explicit decision", before, counts())
            assertTrue("both AI requests must finish", served.await(1, TimeUnit.SECONDS))
            serverThread.join(2_000)
            assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
            assertEquals(2, requests.size)
            ui.waitUntil(5_000) {
                !WebSkinBridgeRuntime.state().optBoolean("chatBusy")
            }

            val staleReviewId = reviewBeforeStage.getString("reviewId")
            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.ResolveAiProposal(
                    reviewId = staleReviewId,
                    decision = AiProposalDecision.STAGE,
                    indices = listOf(0)
                ))
            }
            val stageCompletion = runCatching {
                ui.waitUntil(15_000) {
                    shadowOf(Looper.getMainLooper()).idle()
                    !WebSkinBridgeRuntime.state().optBoolean("chatBusy")
                }
            }
            assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
            val afterStageState = WebSkinBridgeRuntime.state()
            assertTrue("partial stage did not finish; state=$afterStageState; counts=${counts()}; " +
                "pending=${store.pendingCount()}", stageCompletion.isSuccess)
            val reviewAfterStage = afterStageState.optJSONObject("aiProposalReview")
                ?: error("partial stage removed every proposal: $afterStageState")
            assertEquals("partial stage result state=$afterStageState; counts=${counts()}; " +
                "pending=${store.pendingCount()}", 1,
                reviewAfterStage.getJSONArray("proposals").length())
            assertEquals("第二条备忘", reviewAfterStage.getJSONArray("proposals")
                .getJSONObject(0).getString("title"))
            assertFalse("handled candidates must receive a fresh review identity",
                staleReviewId == reviewAfterStage.getString("reviewId"))
            assertEquals(1L, store.draftCount())
            assertEquals(0, store.all().size)
            assertEquals(1L, store.identityCount())
            assertEquals(0L, store.commitCount())

            val afterStage = counts()
            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.ResolveAiProposal(
                    reviewId = staleReviewId,
                    decision = AiProposalDecision.REJECT,
                    indices = listOf(1)
                ))
            }
            ui.waitForIdle()
            val afterStaleAction = WebSkinBridgeRuntime.state()
                .getJSONObject("aiProposalReview")
            assertEquals("a replay from the replaced review cannot consume the remaining card",
                reviewAfterStage.getString("reviewId"), afterStaleAction.getString("reviewId"))
            assertEquals(1, afterStaleAction.getJSONArray("proposals").length())
            assertEquals(afterStage, counts())

            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.ResolveAiProposal(
                    reviewId = reviewAfterStage.getString("reviewId"),
                    decision = AiProposalDecision.REJECT,
                    indices = listOf(0)
                ))
            }
            ui.waitUntil(5_000) {
                WebSkinBridgeRuntime.state().isNull("aiProposalReview")
            }
            assertEquals("rejecting the remaining candidate must not change the staged draft",
                afterStage, counts())
            assertEquals(1, store.pendingCount())
        }
    }

    @Test
    fun supplementCompletesIncompleteProposalWithoutBypassingHostConfirmation() {
        val firstTurn = "帮我创建一个活动，2026-12-01 19:00 开始，标题是接口评审。"
        val supplement = "结束时间是20:00。"
        val completed = JSONObject().put("events", JSONArray().put(JSONObject()
            .put("operation", "CREATE")
            .put("kind", "ACTIVITY")
            .put("title", "接口评审")
            .put("start", "2026-12-01 19:00")
            .put("end", "2026-12-01 20:00"))).toString()
        val requests = CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val served = CountDownLatch(4)
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
                "还需要活动结束时间，请问几点结束？",
                JSONObject().put("events", JSONArray()).toString(),
                "收到补充信息，我会重新核对候选。" +
                    "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"," +
                    "\"source\":\"untrusted model text\"}[[/POWERCAL_DRAFT]]",
                completed
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
                    while (served.count > 0) served.countDown()
                }
            }.apply { name = "pc-web-ai-supplement-stub"; isDaemon = true; start() }

            ui.activity.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
            ui.waitForIdle()
            ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("ai")) }
            ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.SendChat(firstTurn)) }

            var incompleteReview: JSONObject? = null
            ui.waitUntil(20_000) {
                val state = WebSkinBridgeRuntime.state()
                val review = state.optJSONObject("aiProposalReview")
                if (review?.optJSONArray("proposals")?.length() == 1 &&
                    !state.optBoolean("chatBusy")) {
                    incompleteReview = review
                    true
                } else serverFailure.get() != null
            }
            assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
            val firstReview = requireNotNull(incompleteReview)
            val incomplete = firstReview.getJSONArray("proposals").getJSONObject(0)
            assertFalse("missing activity end must remain unpublishable",
                incomplete.getBoolean("publishable"))
            assertEquals(before, counts())
            assertEquals("the initial prompt should make chat plus one structured review call",
                2, requests.size)

            val oldReviewId = firstReview.getString("reviewId")
            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.ResolveAiProposal(
                    reviewId = oldReviewId,
                    decision = AiProposalDecision.SUPPLEMENT,
                    indices = emptyList()
                ))
            }
            ui.waitForIdle()
            assertEquals("supplement asks for more evidence without changing the current review",
                oldReviewId, WebSkinBridgeRuntime.state().getJSONObject("aiProposalReview")
                    .getString("reviewId"))
            assertEquals(before, counts())

            ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.SendChat(supplement)) }
            var completedReview: JSONObject? = null
            ui.waitUntil(20_000) {
                val state = WebSkinBridgeRuntime.state()
                val review = state.optJSONObject("aiProposalReview")
                if (review?.optJSONArray("proposals")?.length() == 1 &&
                    review.getString("reviewId") != oldReviewId &&
                    !state.optBoolean("chatBusy")) {
                    completedReview = review
                    true
                } else serverFailure.get() != null
            }
            assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
            assertTrue("all four chat/interpretation calls must finish",
                served.await(1, TimeUnit.SECONDS))
            serverThread.join(2_000)
            assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
            assertEquals(4, requests.size)

            val latestReview = requireNotNull(completedReview)
            val ready = latestReview.getJSONArray("proposals").getJSONObject(0)
            assertEquals("接口评审", ready.getString("title"))
            assertEquals("2026-12-01 19:00", ready.getString("startText"))
            assertEquals("2026-12-01 20:00", ready.getString("endText"))
            assertTrue(ready.getBoolean("publishable"))
            assertEquals("supplemented structured request must include both user turns",
                true, requests.last().contains(firstTurn) && requests.last().contains(supplement))
            assertTrue("assistant questions remain explicitly untrusted context",
                requests.last().contains("assistantSuggestionsUntrusted"))
            assertFalse("model-provided source text cannot replace user evidence",
                requests.last().contains("untrusted model text"))
            assertEquals("candidate generation and follow-up are review-only", before, counts())

            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.ResolveAiProposal(
                    reviewId = oldReviewId,
                    decision = AiProposalDecision.PUBLISH,
                    indices = listOf(0)
                ))
            }
            ui.waitForIdle()
            assertEquals("stale incomplete review cannot replace the completed review",
                latestReview.getString("reviewId"), WebSkinBridgeRuntime.state()
                    .getJSONObject("aiProposalReview").getString("reviewId"))
            assertEquals(before, counts())

            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.ResolveAiProposal(
                    reviewId = latestReview.getString("reviewId"),
                    decision = AiProposalDecision.PUBLISH,
                    indices = listOf(0)
                ))
            }
            ui.onNodeWithText("确认 AI 批量正式操作").assertExists()
            assertEquals("a completed proposal still waits for trusted Host confirmation",
                before, counts())
            ui.onNodeWithText("取消").performClick()
            ui.waitForIdle()
            assertEquals(latestReview.getString("reviewId"), WebSkinBridgeRuntime.state()
                .getJSONObject("aiProposalReview").getString("reviewId"))
            assertEquals(before, counts())
        }
    }

    @Test
    fun skinFileAttachmentOpensHostDocumentPickerWithoutWritingCalendarData() {
        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitForIdle()
        val before = counts()

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("ai"))
        }
        ui.waitForIdle()
        assertTrue(WebSkinBridgeRuntime.state().getBoolean("chatAttachmentAvailable"))
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.PickAiFile)
        }
        ui.waitForIdle()

        val intent = shadowOf(ui.activity).nextStartedActivity
        assertNotNull("Skin must route file access through Android's Host picker", intent)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent!!.action)
        assertEquals("*/*", intent.type)
        assertTrue(WebSkinBridgeRuntime.state().getBoolean("chatAttachmentBusy"))
        assertFalse(WebSkinBridgeRuntime.state().getBoolean("chatAttachmentAvailable"))
        assertEquals("opening the picker must not persist an event or draft", before, counts())
    }

    @Test
    fun skinImageAttachmentOpensHostImagePickerWithoutWritingCalendarData() {
        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitForIdle()
        val before = counts()

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("ai"))
        }
        ui.waitForIdle()
        assertTrue(WebSkinBridgeRuntime.state().getBoolean("chatAttachmentAvailable"))
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.PickAiImage)
        }
        ui.waitForIdle()

        val intent = shadowOf(ui.activity).nextStartedActivity
        assertNotNull("Skin must route image access through Android's Host picker", intent)
        assertEquals(Intent.ACTION_GET_CONTENT, intent!!.action)
        assertEquals("image/*", intent.type)
        assertTrue(WebSkinBridgeRuntime.state().getBoolean("chatAttachmentBusy"))
        assertFalse(WebSkinBridgeRuntime.state().getBoolean("chatAttachmentAvailable"))
        assertEquals("opening the picker must not persist an event or draft", before, counts())
    }

    private data class Counts(
        val drafts: Long,
        val formalEvents: Int,
        val identities: Long,
        val commits: Long
    )

    private fun counts() = Counts(store.draftCount(), store.all().size,
        store.identityCount(), store.commitCount())

    private fun seedMemo(title: String, zone: ZoneId): TemporalRecord {
        val outcome = store.createFormalEvent(
            DraftForm(kind = TemporalKind.MEMO, title = title, zoneId = zone.id),
            idempotencyKey = "seed-${UUID.randomUUID()}"
        )
        check(outcome is CommitOutcome.Committed) { "could not seed an isolated formal Memo" }
        return requireNotNull(store.get(outcome.eventSeq))
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
