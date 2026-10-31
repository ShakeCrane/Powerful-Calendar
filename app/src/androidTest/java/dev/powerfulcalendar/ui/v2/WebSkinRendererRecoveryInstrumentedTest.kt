package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class WebSkinRendererRecoveryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val complete = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { value ->
                result.set(value)
                complete.countDown()
            }
        }
        assertTrue("WebView JavaScript response timed out", complete.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun awaitJs(web: WebView, condition: String) {
        compose.waitUntil(30_000) { evaluate(web, condition) == "true" }
    }

    @Test
    fun clearRendererExitPersistsNativeRecoveryAndPreservesStore() {
        val context = compose.activity.applicationContext
        val databaseName = "skin-renderer-recovery-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skins = SkinPackageManager(context)
        val originalSkin = skins.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)
        store.createFormalEvent(
            DraftForm(kind = TemporalKind.MEMO, title = "renderer recovery baseline"),
            idempotencyKey = "renderer-recovery-${UUID.randomUUID()}"
        )
        val formalBefore = store.all()
        val pendingBefore = store.pendingCount()
        val commitsBefore = store.commitCount()
        val identitiesBefore = store.identityCount()
        val sequenceBefore = store.nextSequencePreview()

        try {
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            compose.runOnUiThread {
                PowerCalSkinRegistry.reset()
                skins.select("powercal-clear-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            compose.waitUntil(30_000) {
                web?.let { evaluate(it, "!!window.PowerCalSkin && !!window.PowerCalSkin.receive") == "true" } == true
            }

            val terminateResult = AtomicReference<Boolean?>(null)
            compose.runOnUiThread {
                val renderer = requireNotNull(web).webViewRenderProcess
                assertNotNull("API 35 WebView must expose its renderer process", renderer)
                terminateResult.set(renderer!!.terminate())
            }
            assertTrue("Renderer process termination should be accepted", terminateResult.get() == true)

            compose.waitUntil(20_000) {
                skins.currentId() == "powercal-default" &&
                    compose.onAllNodesWithText(
                        "皮肤加载失败，编辑已存档并恢复安全界面。"
                    ).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("工具与设置")
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle {
                assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
                assertEquals(null, PowerCalSkinRegistry.active.web)
            }

            assertEquals(formalBefore, store.all())
            assertEquals(pendingBefore, store.pendingCount())
            assertEquals(commitsBefore, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(sequenceBefore, store.nextSequencePreview())
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            runCatching { skins.select(originalSkin) }
                .onFailure { skins.select("powercal-default") }
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", askedForCalendarImport)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            restoreOnboarding.commit()
        }
    }

    @Test
    fun clearDirtyTransientEditorIsCheckpointedWhenRendererProcessTerminates() {
        val context = compose.activity.applicationContext
        val databaseName = "skin-renderer-dirty-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skins = SkinPackageManager(context)
        val originalSkin = skins.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)
        val title = "RENDERER_DIRTY_EDITOR_CHECKPOINT"

        try {
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            compose.runOnUiThread {
                PowerCalSkinRegistry.reset()
                skins.select("powercal-clear-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null && evaluate(requireNotNull(web),
                    "!!window.PowerCalSkin && !!window.PowerCalSkin.receive && " +
                        "!!document.querySelector('[data-action=\\\"createDraft\\\"]')") == "true"
            }
            val view = requireNotNull(web)
            evaluate(view, "document.querySelector('[data-action=\\\"createDraft\\\"]').click();true")
            awaitJs(view, "!!document.querySelector('#timelineCreateDialog[open]')")
            evaluate(view, "document.querySelector('[data-timeline-create-kind=\\\"ACTIVITY\\\"]').click();true")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "!!document.querySelector('#sheetFields [data-field=\\\"title\\\"]')")
            evaluate(view, "(()=>{const field=document.querySelector('#sheetFields [data-field=\\\"title\\\"]');" +
                "field.value='$title';field.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().getJSONObject("editing")
                    .getJSONObject("deck").optJSONObject("form")?.optString("title") == title
            }

            assertTrue("dirty editor is still transient before renderer failure", store.all().isEmpty())
            assertEquals(0L, store.draftCount())
            assertEquals(0, store.pendingCount())
            assertEquals(0L, store.commitCount())
            assertEquals(0L, store.identityCount())
            assertEquals(1L, store.nextSequencePreview())

            val terminateResult = AtomicReference<Boolean?>(null)
            compose.runOnUiThread {
                val renderer = requireNotNull(view.webViewRenderProcess) {
                    "API 35 WebView must expose its renderer process"
                }
                terminateResult.set(renderer.terminate())
            }
            assertTrue("Renderer process termination should be accepted", terminateResult.get() == true)

            compose.waitUntil(20_000) {
                skins.currentId() == "powercal-default" &&
                    compose.onAllNodesWithText(
                        "皮肤加载失败，编辑已存档并恢复安全界面。"
                    ).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("工具与设置")
                        .fetchSemanticsNodes().isNotEmpty() &&
                    store.pendingCount() == 1
            }
            compose.runOnIdle {
                assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
                assertEquals(null, PowerCalSkinRegistry.active.web)
            }

            assertTrue(store.all().isEmpty())
            assertEquals("renderer failure checkpoints only the changed form", 1L, store.draftCount())
            assertEquals(1, store.pendingCount())
            assertEquals(0L, store.commitCount())
            assertEquals(1L, store.identityCount())
            assertEquals(2L, store.nextSequencePreview())
            assertEquals(2L, store.nextQueueOrderPreview())
            val checkpoint = store.pendingDrafts().single()
            assertEquals(title, checkpoint.form.title)
            assertEquals(TemporalKind.ACTIVITY, checkpoint.form.kind)
            println("CLEAR_DIRTY_RENDERER_RECOVERY_VERIFIED=draft=${checkpoint.draftId}; " +
                "title=${checkpoint.form.title}; formal=${store.all().size}; commits=${store.commitCount()}; " +
                "identities=${store.identityCount()}; sequence=${store.nextSequencePreview()}; native=true")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            runCatching { skins.select(originalSkin) }
                .onFailure { skins.select("powercal-default") }
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", askedForCalendarImport)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            restoreOnboarding.commit()
        }
    }

    @Test
    fun uncaughtSkinRuntimeErrorCheckpointsDirtyEditorAndFallsBackToNative() {
        val context = compose.activity.applicationContext
        val databaseName = "skin-js-error-recovery-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skins = SkinPackageManager(context)
        val originalSkin = skins.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)
        val title = "UNCAUGHT_SKIN_RUNTIME_ERROR_CHECKPOINT"
        val clearArchive = context.assets.open("powercal-clear-default.pcskin").use { it.readBytes() }
        val baselinePack = skins.decode(clearArchive, loadImages = true).pack
        val baselineWeb = requireNotNull(baselinePack.web)
        val modifiedFiles = baselineWeb.files.toMutableMap()
        val appSource = String(requireNotNull(modifiedFiles["web/app.js"]), Charsets.UTF_8)
        modifiedFiles["web/app.js"] = (appSource + "\n" +
            "window.__pcSep14TriggerUncaughtError=()=>setTimeout(" +
            "()=>{throw new Error('SEP14_UNCAUGHT_SKIN_RUNTIME_ERROR')},0);\n")
            .toByteArray(Charsets.UTF_8)
        val faultyPack = baselinePack.copy(
            key = "sep14-runtime-error-${UUID.randomUUID().toString().take(8)}",
            web = baselineWeb.copy(files = modifiedFiles)
        )

        try {
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            compose.runOnUiThread {
                PowerCalSkinRegistry.reset()
                PowerCalSkinRegistry.activate(faultyPack)
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null && evaluate(requireNotNull(web),
                    "!!window.PowerCalSkin && !!window.__pcSep14TriggerUncaughtError && " +
                        "!!document.querySelector('[data-action=createDraft]')") == "true"
            }
            val view = requireNotNull(web)
            evaluate(view, "document.querySelector('[data-action=createDraft]').click(); true")
            awaitJs(view, "!!document.querySelector('#timelineCreateDialog[open]')")
            evaluate(view,
                "document.querySelector('#timelineCreateDialog [data-timeline-create-kind=ACTIVITY]').click(); true")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "!!document.querySelector('#sheetFields [data-field=title]')")
            evaluate(view, "(()=>{const input=document.querySelector('#sheetFields [data-field=title]');" +
                "input.value='$title'; input.dispatchEvent(new Event('change',{bubbles:true})); return true})()")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optJSONObject("editing")?.optJSONObject("deck")
                    ?.optJSONObject("form")?.optString("title") == title
            }

            assertTrue("dirty editor remains transient before the runtime error", store.all().isEmpty())
            assertEquals(0L, store.draftCount())
            assertEquals(0, store.pendingCount())
            assertEquals(0L, store.commitCount())
            assertEquals(0L, store.identityCount())
            assertEquals(1L, store.nextSequencePreview())

            evaluate(view, "window.__pcSep14TriggerUncaughtError(); true")
            try {
                compose.waitUntil(20_000) {
                    skins.currentId() == "powercal-default" && store.pendingCount() == 1 &&
                        compose.onAllNodesWithText(
                            "皮肤加载失败，编辑已存档并恢复安全界面。"
                        ).fetchSemanticsNodes().isNotEmpty() &&
                        compose.onAllNodesWithText("工具与设置")
                            .fetchSemanticsNodes().isNotEmpty()
                }
            } catch (error: Throwable) {
                val webState = runCatching {
                    evaluate(view, "JSON.stringify({title:document.querySelector('#sheetFields [data-field=title]')?.value," +
                        "body:document.body.innerText.slice(0,300)})")
                }.getOrDefault("WebView unavailable")
                throw AssertionError("uncaught skin JavaScript error did not recover to Native; " +
                    "skin=${skins.currentId()}; pending=${store.pendingCount()}; web=$webState", error)
            }

            compose.runOnIdle {
                assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
                assertEquals(null, PowerCalSkinRegistry.active.web)
            }
            assertTrue(store.all().isEmpty())
            assertEquals("runtime failure must checkpoint the modified form exactly once", 1L,
                store.draftCount())
            assertEquals(1, store.pendingCount())
            assertEquals(0L, store.commitCount())
            assertEquals(1L, store.identityCount())
            assertEquals(2L, store.nextSequencePreview())
            assertEquals(title, store.pendingDrafts().single().form.title)
            assertEquals(TemporalKind.ACTIVITY, store.pendingDrafts().single().form.kind)
            println("SEP14_SKIN_JS_ERROR_RECOVERY_VERIFIED=title=$title; pending=1; commits=0; " +
                "formal=0; identities=${store.identityCount()}; native=true")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            runCatching { skins.select(originalSkin) }
                .onFailure { skins.select("powercal-default") }
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", askedForCalendarImport)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            restoreOnboarding.commit()
        }
    }
}
