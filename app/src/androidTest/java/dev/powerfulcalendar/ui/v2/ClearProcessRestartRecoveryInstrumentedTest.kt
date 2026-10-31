package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val CLEAR_PROCESS_RECOVERY_PREFIX = "pc-clear-process-recovery"
private const val CLEAR_PROCESS_RECOVERY_TITLE = "PC_CLEAR_PROCESS_RECOVERY_DIRTY_TITLE"
private const val CLEAR_BUILT_IN_ID = "powercal-clear-default"

private object ClearRecoveryProcessIdentity {
    val token: String = UUID.randomUUID().toString()
}

@RunWith(AndroidJUnit4::class)
class ClearProcessRestartRecoveryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun runId(): String = requireNotNull(
        InstrumentationRegistry.getArguments().getString("clearProcessRecoveryRunId")
    ).also { require(it.matches(Regex("[a-f0-9-]{36}"))) }

    private fun databaseName(id: String) = "$CLEAR_PROCESS_RECOVERY_PREFIX-$id.db"

    private fun markerDirectory(context: Context, id: String) =
        File(context.noBackupFilesDir, "$CLEAR_PROCESS_RECOVERY_PREFIX-$id")

    private fun evaluate(web: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) {
                result.set(it)
                completed.countDown()
            }
        }
        assertTrue("WebView JavaScript response timed out: $expression",
            completed.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun readyWebView(): WebView {
        var web: WebView? = null
        compose.waitUntil(30_000) {
            compose.runOnUiThread {
                web = findWebView(compose.activity.findViewById(android.R.id.content))
            }
            web != null && evaluate(requireNotNull(web),
                "!!window.PowerCalSkin && !!window.PowerCalSkin.receive && " +
                    "!!document.querySelector('[data-action=\\\"createDraft\\\"]')") == "true"
        }
        return requireNotNull(web)
    }

    private fun awaitJs(web: WebView, expression: String) {
        compose.waitUntil(30_000) { evaluate(web, expression) == "true" }
    }

    private fun assertRestoredMemoProjection(
        web: WebView,
        eventSeq: Long,
        occurrenceKey: String,
        revision: Long
    ) {
        val target = JSONObject.quote(
            "#memoList [data-action='toggleMemo'][data-event-seq='$eventSeq']" +
                "[data-occurrence-key='$occurrenceKey']"
        )
        evaluate(web, "document.querySelector('.clear-tabbar [data-action=memo]').click();true")
        awaitJs(web, "!document.querySelector('#memoPage').hidden && " +
            "document.querySelector('#memoCounter')?.textContent==='2' && " +
            "document.querySelectorAll('#memoList .memo-row').length===2 && " +
            "!document.querySelector($target)")
        evaluate(web, "document.querySelector('.tabs [data-filter=done]').click();true")
        awaitJs(web, "document.querySelectorAll('#memoList .memo-row').length===1 && " +
            "document.querySelector($target)?.dataset.revision==='${revision}' && " +
            "document.querySelector($target)?.getAttribute('aria-label')==='恢复未完成'")
        evaluate(web, "document.querySelector('.tabs [data-filter=open]').click();true")
        awaitJs(web, "document.querySelectorAll('#memoList .memo-row').length===2 && " +
            "!document.querySelector($target)")
    }

    private fun attachClear(store: PowerStore, manager: SkinPackageManager) {
        compose.runOnUiThread {
            manager.restoreSelection()
            compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
        }
        readyWebView()
    }

    private fun openDirtyActivity(web: WebView, title: String) {
        evaluate(web, "document.querySelector('[data-action=\\\"createDraft\\\"]').click();true")
        awaitJs(web, "!!document.querySelector('#timelineCreateDialog[open]')")
        evaluate(web,
            "document.querySelector('[data-timeline-create-kind=\\\"ACTIVITY\\\"]').click();true")
        awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
            "!!document.querySelector('#sheetFields [data-field=\\\"title\\\"]')")
        evaluate(web,
            "(()=>{const field=document.querySelector('#sheetFields [data-field=\\\"title\\\"]');" +
                "field.value='$title';field.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
        compose.waitUntil(15_000) {
            WebSkinBridgeRuntime.state().getJSONObject("editing").getJSONObject("deck")
                .optJSONObject("form")?.optString("title") == title
        }
    }

    private fun saveMetadata(file: File, metadata: JSONObject) {
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        FileOutputStream(file).use { output ->
            output.write(metadata.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    private fun restorePreferences(context: Context, metadata: JSONObject) {
        val skinPrefs = context.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
        val originalSkin = metadata.optString("originalSkin", "powercal-default")
        if (metadata.optBoolean("hadSkinSelection")) {
            SkinPackageManager(context).select(originalSkin)
            check(skinPrefs.edit().putString("selected", originalSkin).commit()) {
                "failed to persist the original skin selection"
            }
            check(skinPrefs.getString("selected", null) == originalSkin) {
                "original skin selection did not restore"
            }
        } else {
            check(skinPrefs.edit().remove("selected").commit()) {
                "failed to restore the original empty skin selection"
            }
            check(!skinPrefs.contains("selected")) { "skin selection preference remains" }
            PowerCalSkinRegistry.reset()
        }

        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val editor = onboarding.edit()
        if (metadata.optBoolean("hadOnboardingFlag")) {
            editor.putBoolean("system-calendar-import-asked", metadata.optBoolean("onboardingFlag"))
        } else {
            editor.remove("system-calendar-import-asked")
        }
        check(editor.commit()) { "failed to restore onboarding test preference" }
    }

    @Test
    fun dirtyClearEditorCheckpointsOnStopForColdProcessRecovery() {
        assumeTrue("run the first process with -e clearProcessRecoveryPhase prepare",
            InstrumentationRegistry.getArguments().getString("clearProcessRecoveryPhase") == "prepare")
        val id = runId()
        val context = compose.activity.applicationContext
        val dbName = databaseName(id)
        val databaseFile = context.getDatabasePath(dbName)
        val markerDir = markerDirectory(context, id)
        val markerFile = File(markerDir, "session.json")
        assertFalse("UUID-scoped recovery database must not pre-exist", databaseFile.exists())
        check(markerDir.mkdirs() || markerDir.isDirectory)

        val skinPrefs = context.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadSkinSelection = skinPrefs.contains("selected")
        val originalSkin = skinPrefs.getString("selected", "powercal-default") ?: "powercal-default"
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)
        val metadata = JSONObject()
            .put("runId", id)
            .put("databaseName", dbName)
            .put("originalSkin", originalSkin)
            .put("hadSkinSelection", hadSkinSelection)
            .put("hadOnboardingFlag", hadOnboardingFlag)
            .put("onboardingFlag", originalOnboardingFlag)
        saveMetadata(markerFile, metadata)

        val store = PowerStore(context, dbName)
        var prepared = false
        try {
            val memoStart = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1)
            val memoOccurrenceKey = "${memoStart}T00:00"
            val memoCreated = store.createFormalEvent(
                DraftForm(
                    kind = TemporalKind.MEMO,
                    title = "Clear formal Memo survives cold process recovery $id",
                    startText = memoStart.toString(),
                    zoneId = "Asia/Shanghai",
                    trackingEnabled = true,
                    recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
                ),
                "clear-cold-memo-$id"
            ) as PowerStore.CommitOutcome.Committed
            assertEquals(1L, memoCreated.eventSeq)
            assertTrue(store.setCompleted(memoCreated.eventSeq, memoOccurrenceKey, true,
                memoCreated.revision))
            val completedMemo = requireNotNull(store.get(memoCreated.eventSeq))
            assertEquals(2L, completedMemo.revision)
            assertEquals(setOf(memoOccurrenceKey), completedMemo.completedKeys)
            assertEquals(listOf("CREATE", "COMPLETE"),
                store.history(memoCreated.eventSeq).asReversed().map { it.operation })

            assertEquals(listOf(completedMemo), store.all())
            assertEquals(0L, store.draftCount())
            assertEquals(1, store.identityCount())
            assertEquals(2L, store.nextSequencePreview())
            assertEquals(2L, store.commitCount())
            metadata.put("memoEventSeq", memoCreated.eventSeq)
                .put("memoOccurrenceKey", memoOccurrenceKey)
                .put("memoRevision", completedMemo.revision)

            check(onboarding.edit().putBoolean("system-calendar-import-asked", true).commit())
            val manager = SkinPackageManager(context)
            PowerCalSkinRegistry.reset()
            manager.select(CLEAR_BUILT_IN_ID)
            check(skinPrefs.edit().putString("selected", CLEAR_BUILT_IN_ID).commit())
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            val web = readyWebView()
            val title = "$CLEAR_PROCESS_RECOVERY_TITLE-$id"
            openDirtyActivity(web, title)

            assertEquals("new form must remain transient before ON_STOP", listOf(completedMemo),
                store.all())
            assertEquals(0L, store.draftCount())
            assertEquals(0, store.pendingCount())
            assertEquals(2L, store.commitCount())
            assertEquals(1, store.identityCount())
            assertEquals(2L, store.nextSequencePreview())

            val movedToBackground = AtomicReference<Boolean?>(null)
            compose.runOnUiThread { movedToBackground.set(compose.activity.moveTaskToBack(true)) }
            assertTrue("test Activity must move to background and receive ON_STOP",
                movedToBackground.get() == true)
            compose.waitUntil(15_000) {
                compose.activity.lifecycle.currentState == Lifecycle.State.CREATED
            }
            compose.waitUntil(20_000) { store.pendingCount() == 1 }

            val draft = store.pendingDrafts().single()
            assertEquals(title, draft.form.title)
            assertEquals(2L, store.commitCount())
            assertEquals("ON_STOP must preserve the formal Memo snapshot", listOf(completedMemo),
                store.all())
            assertEquals(2, store.identityCount())
            assertEquals(3L, store.nextSequencePreview())
            assertEquals(2L, store.nextQueueOrderPreview())
            metadata.put("title", title)
                .put("processPid", android.os.Process.myPid())
                .put("processToken", ClearRecoveryProcessIdentity.token)
                .put("draftId", draft.draftId)
                .put("eventSeq", draft.eventSeq)
            saveMetadata(markerFile, metadata)
            prepared = true
            println("CLEAR_COLD_RECOVERY_PREPARED=pid=${android.os.Process.myPid()}; " +
                "draft=${draft.draftId}; title=${draft.form.title}; onStop=true; " +
                "memoEventSeq=${memoCreated.eventSeq}; memoRevision=${completedMemo.revision}; " +
                "memoOccurrence=$memoOccurrenceKey; formal=1; commits=${store.commitCount()}")
        } finally {
            if (!prepared) {
                compose.runOnUiThread {
                    compose.activity.setContent { }
                    PowerCalSkinRegistry.reset()
                }
                compose.waitForIdle()
                store.close()
                context.deleteDatabase(dbName)
                restorePreferences(context, metadata)
                markerDir.deleteRecursively()
            }
        }
    }

    @Test
    fun coldProcessRestartRestoresTheDirtyClearEditorDraft() {
        assumeTrue("run the second process with -e clearProcessRecoveryPhase restore",
            InstrumentationRegistry.getArguments().getString("clearProcessRecoveryPhase") == "restore")
        val id = runId()
        val context = compose.activity.applicationContext
        val dbName = databaseName(id)
        val markerDir = markerDirectory(context, id)
        val markerFile = File(markerDir, "session.json")
        assumeTrue("prepare phase must leave a durable recovery marker", markerFile.isFile)
        val metadata = JSONObject(markerFile.readText(Charsets.UTF_8))
        assertEquals(id, metadata.getString("runId"))
        assertEquals(dbName, metadata.getString("databaseName"))

        val store = PowerStore(context, dbName)
        val memoEventSeq = metadata.getLong("memoEventSeq")
        val memoOccurrenceKey = metadata.getString("memoOccurrenceKey")
        val expectedMemoRevision = metadata.getLong("memoRevision")
        var verified = false
        try {
            assertNotEquals("the app process must be new after force-stop",
                metadata.getInt("processPid"), android.os.Process.myPid())
            assertNotEquals("the process token must be new after force-stop",
                metadata.getString("processToken"), ClearRecoveryProcessIdentity.token)

            val manager = SkinPackageManager(context)
            assertEquals("Clear selection must survive process death", CLEAR_BUILT_IN_ID,
                manager.currentId())
            manager.restoreSelection()
            val completedMemo = requireNotNull(store.get(memoEventSeq))
            assertEquals(expectedMemoRevision, completedMemo.revision)
            assertEquals(setOf(memoOccurrenceKey), completedMemo.completedKeys)
            assertEquals(listOf("CREATE", "COMPLETE"),
                store.history(memoEventSeq).asReversed().map { it.operation })
            assertEquals(listOf(completedMemo), store.all())
            assertEquals(2L, store.commitCount())
            val draft = store.pendingDrafts().single()
            assertEquals(metadata.getLong("draftId"), draft.draftId)
            assertEquals(metadata.getLong("eventSeq"), draft.eventSeq)
            assertEquals(metadata.getString("title"), draft.form.title)
            assertEquals(2, store.identityCount())
            assertEquals(3L, store.nextSequencePreview())
            assertEquals(2L, store.nextQueueOrderPreview())

            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            val web = readyWebView()
            compose.waitUntil(20_000) {
                val host = WebSkinBridgeRuntime.state()
                host.optBoolean("hostDataReady") &&
                    host.getJSONObject("editing").getJSONObject("deck")
                        .optJSONArray("cards")?.length() == 1 &&
                    evaluate(web, "document.querySelector('#pendingCounter')?.textContent==='1'") == "true"
            }
            assertRestoredMemoProjection(web, memoEventSeq, memoOccurrenceKey, expectedMemoRevision)
            evaluate(web, "document.querySelector('.clear-tabbar [data-action=timeline]').click();true")
            awaitJs(web, "!document.querySelector('#timelinePage').hidden")
            val instrumentation = evaluate(web,
                "(()=>{window.__pcColdReplies=[];window.__pcColdCommands=[];" +
                    "const edit=window.PowerCalEdit,skin=window.PowerCalSkin;" +
                    "if(!edit||!skin)return 'bridge-missing';" +
                    "const oldRoute=edit.routeAction,oldResult=edit.onResult,oldSend=skin.sendCommand;" +
                    "edit.routeAction=function(button,host){const routed=oldRoute(button,host);" +
                    "window.__pcColdRoute={name:button?.dataset?.action||'',routed,hasEditing:!!host?.editing};" +
                    "return routed};" +
                    "edit.onResult=function(reply){window.__pcColdReplies.push(reply);return oldResult(reply)};" +
                    "skin.sendCommand=function(name,requestId,args){window.__pcColdCommands.push({name,requestId,args});" +
                    "return oldSend(name,requestId,args)};return true})()")
            assertEquals("true", instrumentation)
            val clickResult = evaluate(web,
                "(()=>{const button=document.querySelector('[data-action=\\\"openDeck\\\"]');" +
                    "if(!button)return 'missing';button.click();return 'clicked'})()")
            try {
                compose.waitUntil(10_000) {
                    evaluate(web, "window.__pcColdReplies?.length===1") == "true"
                }
                assertEquals("deck.open command must be accepted by Host", "true",
                    evaluate(web, "window.__pcColdReplies[0]?.status==='opened'"))
                compose.waitUntil(20_000) {
                    val deck = WebSkinBridgeRuntime.state().getJSONObject("editing")
                        .getJSONObject("deck")
                    deck.optBoolean("open") && deck.optJSONArray("cards")?.length() == 1 &&
                        deck.getJSONArray("cards").getJSONObject(0).optString("title") ==
                        metadata.getString("title") &&
                        deck.optJSONObject("form")?.optString("title") == metadata.getString("title")
                }
            } catch (failure: Throwable) {
                val deck = runCatching {
                    WebSkinBridgeRuntime.state().getJSONObject("editing").getJSONObject("deck").toString()
                }.getOrDefault("unavailable")
                val button = runCatching {
                    evaluate(web, "(()=>{const b=document.querySelector('[data-action=\\\"openDeck\\\"]');" +
                        "return b?JSON.stringify({text:b.textContent,disabled:b.disabled,hidden:b.hidden}):'missing'})()")
                }.getOrDefault("unavailable")
                val bridgeTrace = runCatching {
                    evaluate(web, "JSON.stringify({route:window.__pcColdRoute,commands:window.__pcColdCommands," +
                        "replies:window.__pcColdReplies})")
                }.getOrDefault("unavailable")
                println("CLEAR_COLD_RECOVERY_DECK_TIMEOUT=click=$clickResult; host=$deck; button=$button; " +
                    "bridge=$bridgeTrace; skin=${PowerCalSkinRegistry.active.key}")
                throw failure
            }

            assertEquals(1L, store.draftCount())
            assertEquals(1, store.pendingCount())
            assertEquals(2L, store.commitCount())
            assertEquals(listOf(completedMemo), store.all())
            assertEquals(2, store.identityCount())
            assertEquals(3L, store.nextSequencePreview())
            assertEquals(2L, store.nextQueueOrderPreview())
            val projection = WebSkinBridgeRuntime.state().getJSONObject("editing")
                .getJSONObject("deck")
            println("CLEAR_COLD_MEMO_VERIFIED=pid=${android.os.Process.myPid()}; " +
                "eventSeq=$memoEventSeq; revision=${completedMemo.revision}; " +
                "occurrence=$memoOccurrenceKey; open=2; done=1; commits=${store.commitCount()}")
            println("CLEAR_COLD_RECOVERY_VERIFIED=pid=${android.os.Process.myPid()}; " +
                "draft=${draft.draftId}; title=${projection.getJSONObject("form").optString("title")}; " +
                "formal=1; commits=${store.commitCount()}; sameIdentity=true; clear=true")
            verified = true
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            restorePreferences(context, metadata)
            if (verified) {
                check(context.deleteDatabase(dbName)) { "recovery fixture database was not deleted" }
                check(markerDir.deleteRecursively()) { "recovery marker directory was not deleted" }
            }
        }
    }
}
