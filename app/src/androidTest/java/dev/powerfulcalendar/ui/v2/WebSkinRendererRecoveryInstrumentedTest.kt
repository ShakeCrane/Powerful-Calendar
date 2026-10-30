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
}
