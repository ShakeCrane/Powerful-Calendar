package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.MainActivity
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class NativeRecoveryGestureCheckpointInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val value = AtomicReference<String>()
        val complete = CountDownLatch(1)
        instrumentation.runOnMainSync {
            web.evaluateJavascript(expression) {
                value.set(it)
                complete.countDown()
            }
        }
        assertTrue("WebView evaluation timed out", complete.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(50)
        }
        return condition()
    }

    @Test
    fun emergencyRecoveryCheckpointsDirtyEditorBeforeSwitching() {
        val databaseName = "r07-emergency-gesture-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val manager = SkinPackageManager(context)
        val preferences = context.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
        val hadSelection = preferences.contains("selected")
        val originalSelection = preferences.getString("selected", null)
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboarding = onboarding.contains("system-calendar-import-asked")
        val originalOnboarding = onboarding.getBoolean("system-calendar-import-asked", false)
        val title = "R07_EMERGENCY_GESTURE_DRAFT_${UUID.randomUUID()}"
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                PowerCalSkinRegistry.reset()
                manager.select("powercal-clear-default")
                activity.setContent {
                    PowerCalTheme {
                        PowerCalApp(
                            store,
                            activity.nativeRecoveryRequestSignal,
                            activity::onNativeRecoveryHandled
                        )
                    }
                }
            }

            val activityRef = AtomicReference<MainActivity>()
            val webRef = AtomicReference<WebView>()
            assertTrue("factory WebView was not attached", waitUntil(30_000) {
                scenario.onActivity { activity ->
                    activityRef.set(activity)
                    findWebView(activity.findViewById(android.R.id.content))?.let(webRef::set)
                }
                val web = webRef.get() ?: return@waitUntil false
                evaluate(web, "!!window.PowerCalSkin && !!document.querySelector('[data-action=\\\"createDraft\\\"]')") == "true"
            })

            val web = webRef.get()
            evaluate(web, "document.querySelector('[data-action=\\\"createDraft\\\"]').click();true")
            assertTrue("create dialog did not open", waitUntil(10_000) {
                evaluate(web, "!!document.querySelector('#timelineCreateDialog[open]')") == "true"
            })
            evaluate(web, "document.querySelector('#timelineCreateDialog [data-timeline-create-kind=\\\"ACTIVITY\\\"]').click();true")
            assertTrue("ACTIVITY editor did not open", waitUntil(15_000) {
                evaluate(web, "!!document.querySelector('#sheetFields [data-field=\\\"title\\\"]')") == "true"
            })
            evaluate(web, "(()=>{const f=document.querySelector('#sheetFields [data-field=\\\"title\\\"]');f.value='$title';f.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
            assertTrue("Host did not receive the dirty editor form", waitUntil(15_000) {
                WebSkinBridgeRuntime.state().getJSONObject("editing")
                    .getJSONObject("deck").optJSONObject("form")?.optString("title") == title
            })
            assertTrue(store.all().isEmpty())
            assertEquals(0L, store.draftCount())
            assertEquals(0L, store.commitCount())

            val activity = activityRef.get()
            val density = activity.resources.displayMetrics.density
            val y = activity.resources.displayMetrics.heightPixels * 0.5f
            val firstX = 30f * density
            val secondX = 250f * density
            val firstTapAt = SystemClock.uptimeMillis() + 100L
            scenario.onActivity { target ->
                repeat(8) { tap ->
                    val x = if (tap % 2 == 0) firstX else secondX
                    val downTime = firstTapAt + tap * 160L
                    val down = MotionEvent.obtain(downTime, downTime,
                        MotionEvent.ACTION_DOWN, x, y, 0)
                    val up = MotionEvent.obtain(downTime, downTime + 60L,
                        MotionEvent.ACTION_UP, x, y, 0)
                    try {
                        target.dispatchTouchEvent(down)
                        target.dispatchTouchEvent(up)
                    } finally {
                        down.recycle()
                        up.recycle()
                    }
                }
            }

            assertTrue("emergency gesture did not enter Native recovery", waitUntil(10_000) {
                manager.currentId() == "powercal-default"
            })
            val draft = store.pendingDrafts().single()
            assertEquals(title, draft.form.title)
            assertEquals(TemporalKind.ACTIVITY, draft.form.kind)
            assertTrue(store.all().isEmpty())
            assertEquals(0L, store.commitCount())
            assertEquals(1L, store.draftCount())
            assertTrue("Native Host was not recomposed after the checkpoint", waitUntil(5_000) {
                PowerCalSkinRegistry.active.key == "powercal-default"
            })
            assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
            assertEquals(null, PowerCalSkinRegistry.active.web)
            println("R07_EMERGENCY_RECOVERY_CHECKPOINT=PASS; draft=${draft.draftId}; formal=0; commits=0; native=true")
        } finally {
            scenario?.onActivity { it.setContent { } }
            scenario?.close()
            PowerCalSkinRegistry.reset()
            store.close()
            context.deleteDatabase(databaseName)
            val restore = preferences.edit()
            if (hadSelection) restore.putString("selected", originalSelection)
            else restore.remove("selected")
            check(restore.commit())
            SkinPackageManager(context).restoreSelection()
            val restoreOnboarding = onboarding.edit()
            if (hadOnboarding) restoreOnboarding.putBoolean("system-calendar-import-asked", originalOnboarding)
            else restoreOnboarding.remove("system-calendar-import-asked")
            check(restoreOnboarding.commit())
        }
    }
}
