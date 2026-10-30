package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.ai.AiChatHistoryStore
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PowerCalAiProposalReviewWebViewInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val value = AtomicReference<String>()
        val completed = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { result ->
                value.set(result)
                completed.countDown()
            }
        }
        assertTrue("WebView JavaScript response timed out: $expression",
            completed.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun awaitJs(web: WebView, expression: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(100)
        }
        val diagnostic = runCatching {
            evaluate(web, "JSON.stringify({page:document.querySelector('.page:not([hidden])')?.id," +
                "items:document.querySelectorAll('.ai-proposal-item').length," +
                "chat:document.querySelector('#chatMessages')?.textContent?.slice(-500)})")
        }.getOrDefault("unavailable")
        throw AssertionError("WebView condition timed out: $expression; state=$diagnostic")
    }

    @Test
    fun actualClearWebViewPublishesReplaceAndDeleteOnlyAfterHostConfirmation() {
        val storage: Context = compose.activity.applicationContext
        val namespace = "pc-ai-review-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "review.db"
        val store = PowerStore(context, database)
        val zone = ZoneId.systemDefault()
        val requestPrefix = "帮我创建一个正式事项处理提案，2026-12-01："

        try {
            store.readableDatabase
            val replaceOutcome = store.createFormalEvent(
                DraftForm(kind = TemporalKind.MEMO, title = "原会议标题", zoneId = zone.id),
                idempotencyKey = "seed-${UUID.randomUUID()}"
            ) as PowerStore.CommitOutcome.Committed
            val deleteOutcome = store.createFormalEvent(
                DraftForm(kind = TemporalKind.MEMO, title = "过期提醒", zoneId = zone.id),
                idempotencyKey = "seed-${UUID.randomUUID()}"
            ) as PowerStore.CommitOutcome.Committed
            val originalReplace = requireNotNull(store.get(replaceOutcome.eventSeq))
            val originalDelete = requireNotNull(store.get(deleteOutcome.eventSeq))
            val userText = "$requestPrefix 把备忘 #${replaceOutcome.eventSeq} " +
                "标题改成项目回顾，并删除备忘 #${deleteOutcome.eventSeq}。"
            val structured = JSONObject().put("events", JSONArray()
                .put(JSONObject()
                    .put("operation", "REPLACE")
                    .put("eventSeq", replaceOutcome.eventSeq)
                    .put("kind", "MEMO")
                    .put("title", "项目回顾"))
                .put(JSONObject()
                    .put("operation", "DELETE")
                    .put("eventSeq", deleteOutcome.eventSeq)
                    .put("kind", "MEMO")
                    .put("title", originalDelete.title))).toString()
            val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
            val serverFailure = AtomicReference<Throwable?>()
            val served = CountDownLatch(2)
            val commitsBeforeReview = store.commitCount()
            val identitiesBeforeReview = store.identityCount()
            val draftsBeforeReview = store.draftCount()
            val pendingBeforeReview = store.pendingCount()

            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 20_000
                AiSettingsStore.save(context, AiSettings(
                    endpoint = "http://127.0.0.1:${server.localPort}/v1",
                    model = "local-stub:free",
                    apiKey = "LOCAL_TEST_ONLY",
                    enabled = true,
                    shareContext = true
                ))
                val responses = listOf(
                    "我会生成两项待审核操作。" +
                        "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
                    structured
                )
                val serverThread = Thread {
                    try {
                        repeat(responses.size) { index ->
                            server.accept().use { socket ->
                                socket.soTimeout = 15_000
                                requests += readRequest(socket)
                                writeReply(socket, responses[index])
                            }
                            served.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (served.count > 0) served.countDown()
                    }
                }.apply { name = "pc-ai-review-webview-local-stub"; isDaemon = true; start() }

                compose.runOnUiThread {
                    SkinPackageManager(context).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides context) {
                            PowerCalTheme { PowerCalApp(store) }
                        }
                    }
                }
                var web: WebView? = null
                compose.waitUntil(30_000) {
                    compose.runOnUiThread {
                        web = findWebView(compose.activity.findViewById(android.R.id.content))
                    }
                    web != null
                }
                val view = requireNotNull(web)
                awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                awaitJs(view, "document.querySelector('#dateSubtitle')?.textContent !== '日历 · Clear'")
                evaluate(view, "document.querySelector('.clear-tabbar [data-action=ai]').click(); true")
                awaitJs(view, "!document.querySelector('#aiPage').hidden")
                evaluate(view, "(()=>{const input=document.querySelector('#chatInput');" +
                    "input.value=${JSONObject.quote(userText)};" +
                    "document.querySelector('#chatForm').requestSubmit();return true})()")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===2")
                awaitJs(view, "Array.from(document.querySelectorAll('.ai-proposal-title')).some(e=>e.textContent.includes('REPLACE')) &&" +
                    "Array.from(document.querySelectorAll('.ai-proposal-title')).some(e=>e.textContent.includes('DELETE'))")
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertTrue("both requests must reach the device-local stub",
                    served.await(1, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertEquals(2, requests.size)
                assertEquals(2, store.all().size)
                assertEquals(commitsBeforeReview, store.commitCount())
                assertEquals(identitiesBeforeReview, store.identityCount())
                assertEquals(draftsBeforeReview, store.draftCount())
                assertEquals(pendingBeforeReview, store.pendingCount())

                val titles = evaluate(view,
                    "Array.from(document.querySelectorAll('.ai-proposal-title')).map(e=>e.textContent).join('\\n')")
                assertTrue("skin must show the target identity/revision in the review: $titles",
                    titles.contains("#${replaceOutcome.eventSeq} · 版本 ${originalReplace.revision}"))
                assertTrue("skin must show the deletion target snapshot: $titles",
                    titles.contains("#${deleteOutcome.eventSeq} · 版本 ${originalDelete.revision}"))

                evaluate(view, "document.querySelector('.ai-proposal-group .ai-proposal-actions button.primary').click(); true")
                compose.onNodeWithText("确认 AI 批量正式操作").assertExists()
                compose.onNodeWithText("确认并提交 2 项").assertExists()
                assertEquals("review is read-only before native Host confirmation",
                    commitsBeforeReview, store.commitCount())
                assertEquals(originalReplace, store.get(replaceOutcome.eventSeq))
                assertEquals(originalDelete, store.get(deleteOutcome.eventSeq))

                compose.onNodeWithText("确认并提交 2 项").performClick()
                compose.waitUntil(30_000) {
                    store.get(replaceOutcome.eventSeq)?.title == "项目回顾" &&
                        store.get(deleteOutcome.eventSeq) == null
                }
                assertEquals(originalReplace.eventSeq, store.get(replaceOutcome.eventSeq)?.eventSeq)
                assertEquals(originalReplace.revision + 1, store.get(replaceOutcome.eventSeq)?.revision)
                assertEquals(1, store.all().size)
                assertEquals(0, store.pendingCount())
                assertEquals(commitsBeforeReview + 2, store.commitCount())
                assertEquals(identitiesBeforeReview, store.identityCount())
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===0")
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                .forEach { name ->
                storage.deleteSharedPreferences("$namespace-$name")
            }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun actualClearWebViewStagesRejectsAndSupplementsThroughHostWithoutUnapprovedWrites() {
        val storage: Context = compose.activity.applicationContext
        val namespace = "pc-ai-decisions-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "decisions.db"
        val store = PowerStore(context, database)
        val beforeDrafts = store.draftCount()
        val beforeIdentities = store.identityCount()
        val beforeCommits = store.commitCount()
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val served = CountDownLatch(6)
        val firstPrompt = "创建两条独立备忘：第一条和第二条。"
        val initialProposals = JSONObject().put("events", JSONArray()
            .put(JSONObject().put("operation", "CREATE").put("kind", "MEMO")
                .put("title", "第一条备忘"))
            .put(JSONObject().put("operation", "CREATE").put("kind", "MEMO")
                .put("title", "第二条备忘"))).toString()
        val firstSupplementPrompt = "帮我创建一个活动，2026-12-01 19:00 开始，标题是接口评审。"
        val completedActivity = JSONObject().put("events", JSONArray().put(JSONObject()
            .put("operation", "CREATE").put("kind", "ACTIVITY")
            .put("title", "接口评审")
            .put("start", "2026-12-01 19:00")
            .put("end", "2026-12-01 20:00"))).toString()

        try {
            store.readableDatabase
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 20_000
                AiSettingsStore.save(context, AiSettings(
                    endpoint = "http://127.0.0.1:${server.localPort}/v1",
                    model = "local-stub:free",
                    apiKey = "LOCAL_TEST_ONLY",
                    enabled = true,
                    shareContext = false
                ))
                val responses = listOf(
                    "我会生成两项待核对提案。" +
                        "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
                    initialProposals,
                    "活动还缺少结束时间，请补充几点结束。",
                    JSONObject().put("events", JSONArray()).toString(),
                    "收到补充信息，我会重新核对候选。" +
                        "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
                    completedActivity
                )
                val serverThread = Thread {
                    try {
                        repeat(responses.size) { index ->
                            server.accept().use { socket ->
                                socket.soTimeout = 15_000
                                requests += readRequest(socket)
                                writeReply(socket, responses[index])
                            }
                            served.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (served.count > 0) served.countDown()
                    }
                }.apply { name = "pc-ai-decisions-webview-local-stub"; isDaemon = true; start() }

                compose.runOnUiThread {
                    SkinPackageManager(context).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides context) {
                            PowerCalTheme { PowerCalApp(store) }
                        }
                    }
                }
                var web: WebView? = null
                compose.waitUntil(30_000) {
                    compose.runOnUiThread {
                        web = findWebView(compose.activity.findViewById(android.R.id.content))
                    }
                    web != null
                }
                val view = requireNotNull(web)
                awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                awaitJs(view, "document.querySelector('#dateSubtitle')?.textContent !== '日历 · Clear'")
                evaluate(view, "document.querySelector('.clear-tabbar [data-action=ai]').click(); true")
                awaitJs(view, "!document.querySelector('#aiPage').hidden")

                fun sendChat(text: String) {
                    val sent = evaluate(view, "(()=>{const input=document.querySelector('#chatInput');" +
                        "if(!input)return false;input.value=${JSONObject.quote(text)};" +
                        "document.querySelector('#chatForm').requestSubmit();return true})()")
                    assertEquals("true", sent)
                }

                fun clickReviewAction(label: String) {
                    val clicked = evaluate(view, "(()=>{const button=Array.from(" +
                        "document.querySelectorAll('.ai-proposal-actions button')).find(" +
                        "item=>item.textContent.trim()===${JSONObject.quote(label)});" +
                        "if(!button||button.disabled)return false;button.click();return true})()")
                    assertEquals("button '$label' must be enabled in Clear", "true", clicked)
                }

                sendChat(firstPrompt)
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===2")
                awaitJs(view, "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='写入草稿'&&!button.disabled)")
                assertNull("local AI stub failed before partial stage: ${serverFailure.get()}",
                    serverFailure.get())
                assertEquals("review must not write before a decision", beforeDrafts, store.draftCount())
                assertEquals(beforeIdentities, store.identityCount())
                assertEquals(beforeCommits, store.commitCount())

                val deselected = evaluate(view, "(()=>{const items=" +
                    "Array.from(document.querySelectorAll('.ai-proposal-item input[type=checkbox]'));" +
                    "if(items.length!==2)return false;if(items[1].checked)items[1].click();" +
                    "return !Array.from(document.querySelectorAll(" +
                    "'.ai-proposal-item input[type=checkbox]'))[1].checked})()")
                assertEquals("the second independent candidate must be deselected before staging", "true", deselected)
                clickReviewAction("写入草稿")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "document.querySelector('.ai-proposal-title')?.textContent.includes('第二条备忘')")
                assertEquals(1L, store.draftCount())
                assertEquals(1, store.pendingCount())
                assertEquals(0, store.all().size)
                assertEquals(beforeIdentities + 1, store.identityCount())
                assertEquals(beforeCommits, store.commitCount())

                clickReviewAction("批量拒绝")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===0")
                assertEquals("reject must leave the staged draft untouched", 1L, store.draftCount())
                assertEquals(1, store.pendingCount())
                assertEquals(0, store.all().size)
                assertEquals(beforeIdentities + 1, store.identityCount())
                assertEquals(beforeCommits, store.commitCount())

                sendChat(firstSupplementPrompt)
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1")
                awaitJs(view, "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='补充细节'&&!button.disabled)")
                val beforeSupplement = store.draftCount()
                clickReviewAction("补充细节")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "document.querySelector('#chatMessages').textContent.includes('请在下方聊天框补充')")
                assertEquals("supplement choice is review-only", beforeSupplement, store.draftCount())
                assertEquals(beforeIdentities + 1, store.identityCount())
                assertEquals(beforeCommits, store.commitCount())

                sendChat("请在 20:00 结束。")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "document.querySelector('.ai-proposal-item-body')?.textContent.includes('2026-12-01 20:00')")
                assertTrue("all six local AI calls must finish", served.await(1, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertEquals(6, requests.size)
                assertTrue("follow-up interpretation must include both user turns",
                    requests.last().contains(firstSupplementPrompt) &&
                        requests.last().contains("请在 20:00 结束。"))
                assertEquals("supplemented candidate remains a proposal until an explicit decision",
                    beforeSupplement, store.draftCount())
                assertEquals(1, store.pendingCount())
                assertEquals(0, store.all().size)
                assertEquals(beforeIdentities + 1, store.identityCount())
                assertEquals(beforeCommits, store.commitCount())

                clickReviewAction("批量拒绝")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===0")
                assertEquals("final rejection must preserve only the explicitly staged draft",
                    1L, store.draftCount())
                assertEquals(1, store.pendingCount())
                assertEquals(0, store.all().size)
                assertEquals(beforeIdentities + 1, store.identityCount())
                assertEquals(beforeCommits, store.commitCount())
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                .forEach { name -> storage.deleteSharedPreferences("$namespace-$name") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun actualClearWebViewPublishesCreateOnlyAfterHostConfirmation() {
        val storage: Context = compose.activity.applicationContext
        val namespace = "pc-ai-create-review-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "create-review.db"
        val store = PowerStore(context, database)
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val served = CountDownLatch(2)
        val title = "可信发布活动"
        val prompt = "请新建活动：2026-12-01 19:00 开始，20:00 结束，标题是可信发布活动。"
        val proposal = JSONObject().put("events", JSONArray().put(JSONObject()
            .put("operation", "CREATE")
            .put("kind", "ACTIVITY")
            .put("title", title)
            .put("start", "2026-12-01 19:00")
            .put("end", "2026-12-01 20:00"))).toString()

        try {
            store.readableDatabase
            val formalBefore = store.all()
            val draftsBefore = store.draftCount()
            val pendingBefore = store.pendingCount()
            val identitiesBefore = store.identityCount()
            val commitsBefore = store.commitCount()
            val sequenceBefore = store.nextSequencePreview()
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 20_000
                AiSettingsStore.save(context, AiSettings(
                    endpoint = "http://127.0.0.1:${server.localPort}/v1",
                    model = "local-stub:free",
                    apiKey = "LOCAL_TEST_ONLY",
                    enabled = true,
                    shareContext = false
                ))
                val responses = listOf(
                    "我会准备一项待核对提案。" +
                        "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
                    proposal
                )
                val serverThread = Thread {
                    try {
                        repeat(responses.size) { index ->
                            server.accept().use { socket ->
                                socket.soTimeout = 15_000
                                requests += readRequest(socket)
                                writeReply(socket, responses[index])
                            }
                            served.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (served.count > 0) served.countDown()
                    }
                }.apply { name = "pc-ai-create-review-webview-local-stub"; isDaemon = true; start() }

                compose.runOnUiThread {
                    SkinPackageManager(context).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides context) {
                            PowerCalTheme { PowerCalApp(store) }
                        }
                    }
                }
                var web: WebView? = null
                compose.waitUntil(30_000) {
                    compose.runOnUiThread {
                        web = findWebView(compose.activity.findViewById(android.R.id.content))
                    }
                    web != null
                }
                val view = requireNotNull(web)
                awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                awaitJs(view, "document.querySelector('#dateSubtitle')?.textContent !== '日历 · Clear'")
                evaluate(view, "document.querySelector('.clear-tabbar [data-action=ai]').click(); true")
                awaitJs(view, "!document.querySelector('#aiPage').hidden")
                evaluate(view, "(()=>{const input=document.querySelector('#chatInput');" +
                    "input.value=${JSONObject.quote(prompt)};" +
                    "document.querySelector('#chatForm').requestSubmit();return true})()")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "document.querySelector('.ai-proposal-title')?.textContent.includes(${JSONObject.quote(title)})")
                awaitJs(view, "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='批量同意'&&!button.disabled)")
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertTrue("both AI requests must reach the local free stub",
                    served.await(1, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertEquals(2, requests.size)

                assertEquals(formalBefore, store.all())
                assertEquals(draftsBefore, store.draftCount())
                assertEquals(pendingBefore, store.pendingCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(commitsBefore, store.commitCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())

                evaluate(view,
                    "document.querySelector('.ai-proposal-actions button.primary').click(); true")
                compose.onNodeWithText("确认 AI 批量正式操作").assertExists()
                compose.onNodeWithText("确认并提交 1 项").assertExists()
                assertEquals("Host review is read-only before approval", formalBefore, store.all())
                assertEquals(commitsBefore, store.commitCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())

                compose.onNodeWithText("取消").performClick()
                compose.waitForIdle()
                assertEquals("cancelling Host review must not publish", formalBefore, store.all())
                assertEquals(draftsBefore, store.draftCount())
                assertEquals(pendingBefore, store.pendingCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(commitsBefore, store.commitCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='批量同意'&&!button.disabled)")

                evaluate(view,
                    "document.querySelector('.ai-proposal-actions button.primary').click(); true")
                compose.onNodeWithText("确认 AI 批量正式操作").assertExists()
                compose.onNodeWithText("确认并提交 1 项").performClick()
                compose.waitUntil(30_000) {
                    store.all().singleOrNull()?.let {
                        it.kind == TemporalKind.ACTIVITY && it.title == title &&
                            it.startMillis == java.time.LocalDateTime.parse("2026-12-01T19:00")
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() &&
                            it.endMillis == java.time.LocalDateTime.parse("2026-12-01T20:00")
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } == true
                }
                // A committed AI CREATE keeps one resolved audit receipt in drafts; it is not pending.
                assertEquals(draftsBefore + 1, store.draftCount())
                assertEquals(pendingBefore, store.pendingCount())
                assertEquals(commitsBefore + 1, store.commitCount())
                assertEquals(identitiesBefore + 1, store.identityCount())
                assertEquals(sequenceBefore + 1, store.nextSequencePreview())
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===0")
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                .forEach { name -> storage.deleteSharedPreferences("$namespace-$name") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun actualClearWebViewConvertsExistingEventOnlyAfterHostConfirmation() {
        val storage: Context = compose.activity.applicationContext
        val namespace = "pc-ai-convert-review-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "convert-review.db"
        val store = PowerStore(context, database)
        val zone = ZoneId.systemDefault()
        val startMillis = java.time.LocalDateTime.parse("2026-12-01T19:00")
            .atZone(zone).toInstant().toEpochMilli()
        val endMillis = java.time.LocalDateTime.parse("2026-12-01T20:00")
            .atZone(zone).toInstant().toEpochMilli()
        val title = "待转换闹钟"
        val prompt = "把闹钟 #1 转换为活动，保留 2026-12-01 19:00 开始，结束时间为 20:00。"
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val served = CountDownLatch(2)

        try {
            val seeded = store.createFormalEvent(
                DraftForm(
                    kind = TemporalKind.ALARM,
                    title = title,
                    startText = DraftForm.formatInstant(startMillis, zone),
                    reminderMinutes = -1,
                    zoneId = zone.id
                ),
                idempotencyKey = "seed-${UUID.randomUUID()}"
            ) as PowerStore.CommitOutcome.Committed
            val original = requireNotNull(store.get(seeded.eventSeq))
            val proposal = JSONObject().put("events", JSONArray().put(JSONObject()
                .put("operation", "CONVERT")
                .put("eventSeq", seeded.eventSeq)
                .put("kind", "ACTIVITY")
                .put("title", title)
                .put("start", "2026-12-01 19:00")
                .put("end", "2026-12-01 20:00"))).toString()
            val formalBefore = store.all()
            val draftsBefore = store.draftCount()
            val pendingBefore = store.pendingCount()
            val commitsBefore = store.commitCount()
            val identitiesBefore = store.identityCount()
            val sequenceBefore = store.nextSequencePreview()
            val userText = prompt.replace("#1", "#${seeded.eventSeq}")

            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())
            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 20_000
                AiSettingsStore.save(context, AiSettings(
                    endpoint = "http://127.0.0.1:${server.localPort}/v1",
                    model = "local-stub:free",
                    apiKey = "LOCAL_TEST_ONLY",
                    enabled = true,
                    shareContext = true
                ))
                val responses = listOf(
                    "我会检查目标事项并准备类型转换提案。" +
                        "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
                    proposal
                )
                val serverThread = Thread {
                    try {
                        repeat(responses.size) { index ->
                            server.accept().use { socket ->
                                socket.soTimeout = 15_000
                                requests += readRequest(socket)
                                writeReply(socket, responses[index])
                            }
                            served.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (served.count > 0) served.countDown()
                    }
                }.apply { name = "pc-ai-convert-review-webview-local-stub"; isDaemon = true; start() }

                compose.runOnUiThread {
                    SkinPackageManager(context).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides context) {
                            PowerCalTheme { PowerCalApp(store) }
                        }
                    }
                }
                var web: WebView? = null
                compose.waitUntil(30_000) {
                    compose.runOnUiThread {
                        web = findWebView(compose.activity.findViewById(android.R.id.content))
                    }
                    web != null
                }
                val view = requireNotNull(web)
                awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                awaitJs(view, "document.querySelector('#dateSubtitle')?.textContent !== '日历 · Clear'")
                evaluate(view, "document.querySelector('.clear-tabbar [data-action=ai]').click(); true")
                awaitJs(view, "!document.querySelector('#aiPage').hidden")
                evaluate(view, "(()=>{const input=document.querySelector('#chatInput');" +
                    "input.value=${JSONObject.quote(userText)};" +
                    "document.querySelector('#chatForm').requestSubmit();return true})()")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "document.querySelector('.ai-proposal-title')?.textContent.includes('CONVERT')")
                awaitJs(view, "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='批量同意'&&!button.disabled)")
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertTrue("both AI requests must reach the local free stub",
                    served.await(1, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertEquals(2, requests.size)
                assertTrue("AI context must include the conversion target",
                    requests.last().contains(title))
                assertEquals(formalBefore, store.all())
                assertEquals(draftsBefore, store.draftCount())
                assertEquals(pendingBefore, store.pendingCount())
                assertEquals(commitsBefore, store.commitCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())

                evaluate(view,
                    "document.querySelector('.ai-proposal-actions button.primary').click(); true")
                compose.onNodeWithText("确认 AI 批量正式操作").assertExists()
                compose.onNodeWithText("确认并提交 1 项").assertExists()
                assertEquals("Host review stays read-only until explicit approval",
                    original, store.get(seeded.eventSeq))
                assertEquals(commitsBefore, store.commitCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())

                compose.onNodeWithText("取消").performClick()
                compose.waitForIdle()
                assertEquals("cancelling Host review must not convert the event",
                    original, store.get(seeded.eventSeq))
                assertEquals(draftsBefore, store.draftCount())
                assertEquals(pendingBefore, store.pendingCount())
                assertEquals(commitsBefore, store.commitCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='批量同意'&&!button.disabled)")

                evaluate(view,
                    "document.querySelector('.ai-proposal-actions button.primary').click(); true")
                compose.onNodeWithText("确认 AI 批量正式操作").assertExists()
                compose.onNodeWithText("确认并提交 1 项").performClick()
                compose.waitUntil(30_000) {
                    val saved = store.get(seeded.eventSeq)
                    saved?.kind == TemporalKind.ACTIVITY && saved.title == title &&
                        saved.startMillis == startMillis && saved.endMillis == endMillis
                }
                val converted = requireNotNull(store.get(seeded.eventSeq))
                assertEquals(seeded.eventSeq, converted.eventSeq)
                assertEquals(original.revision + 1, converted.revision)
                assertEquals(commitsBefore + 1, store.commitCount())
                assertEquals(0, store.pendingCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===0")
                println("CLEAR_AI_CONVERT_VERIFIED=eventSeq=${converted.eventSeq}; revision=${converted.revision}; " +
                    "kind=${converted.kind}; commits=${store.commitCount()}; identities=${store.identityCount()}; " +
                    "sequence=${store.nextSequencePreview()}; cancel-preserved=true")
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                .forEach { name -> storage.deleteSharedPreferences("$namespace-$name") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    private fun readRequest(socket: java.net.Socket): String {
        val input = socket.getInputStream().buffered()
        fun line(): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val byte = input.read()
                require(byte >= 0) { "truncated HTTP request header" }
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

    private class IsolatedAppContext(
        target: Context,
        private val backing: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(backing.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }
        private fun alias(name: String) = "$namespace-$name"
        private fun databasePath(name: String): File = backing.getDatabasePath(alias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            backing.getSharedPreferences(alias(name), mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = databasePath(name)
        override fun openOrCreateDatabase(name: String, mode: Int,
            factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(alias(name), mode, factory)
        }
        override fun openOrCreateDatabase(name: String, mode: Int,
            factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(alias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = backing.deleteDatabase(alias(name))
    }
}
