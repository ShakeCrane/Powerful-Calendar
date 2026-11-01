package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.PowerStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ClearNavigationBackInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val value = AtomicReference<String>()
        val complete = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) {
                value.set(it)
                complete.countDown()
            }
        }
        assertTrue("WebView JavaScript callback timed out: $expression",
            complete.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun tapWebSelector(web: WebView, selector: String) {
        val probe = JSONObject(evaluate(web, """(()=>{
            const target=document.querySelector(${JSONObject.quote(selector)});
            if(!target)return {found:false};
            target.scrollIntoView({block:'center',inline:'nearest'});
            const rect=target.getBoundingClientRect();
            return {found:true,x:rect.left+rect.width/2,y:rect.top+rect.height/2,
              cssWidth:innerWidth,cssHeight:innerHeight,
              visible:rect.width>0&&rect.height>0&&rect.bottom>0&&rect.top<innerHeight};
        })()"""))
        assertTrue("Clear touch target is not visible: $selector $probe",
            probe.optBoolean("found") && probe.optBoolean("visible"))
        val location = IntArray(2)
        val size = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            size.set(web.width to web.height)
        }
        val (width, height) = requireNotNull(size.get())
        val scaleX = width / probe.getDouble("cssWidth")
        val scaleY = height / probe.getDouble("cssHeight")
        assertEquals("WebView touch coordinates must preserve scale", scaleX, scaleY, 0.03)
        val x = location[0] + (probe.getDouble("x") * scaleX).toFloat()
        val y = location[1] + (probe.getDouble("y") * scaleY).toFloat()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(downTime, downTime + 70, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            assertTrue("Clear touch down must be injected", automation.injectInputEvent(down, true))
            SystemClock.sleep(70)
            assertTrue("Clear touch up must be injected", automation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    @Test
    fun androidBackDismissesSkinDialogReturnsFromSettingsAndRetainsNativeRecovery() {
        assumeTrue("run against an isolated Android emulator", Build.HARDWARE == "ranchu" ||
            Build.HARDWARE == "goldfish")

        val context = compose.activity.applicationContext
        val databaseName = "clear-back-dialog-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val manager = SkinPackageManager(context)
        val skinPreferences = context.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
        val hadSkinSelection = skinPreferences.contains("selected")
        val originalSkin = skinPreferences.getString("selected", manager.currentId())
            ?: manager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)

        try {
            check(onboarding.edit().putBoolean("system-calendar-import-asked", true).commit())
            PowerCalSkinRegistry.reset()
            manager.select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin?.receive && " +
                    "!!document.querySelector('[data-action=\"createDraft\"]')") == "true"
            }

            evaluate(view, "document.querySelector('[data-action=\"createDraft\"]').click(); true")
            compose.waitUntil(10_000) {
                evaluate(view, "!!document.querySelector('#timelineCreateDialog[open]')") == "true"
            }
            assertEquals("TIMELINE", WebSkinBridgeRuntime.state().optString("page"))
            assertEquals(0L, store.draftCount())
            assertEquals(0L, store.identityCount())
            assertEquals(1L, store.nextSequencePreview())

            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

            compose.waitUntil(10_000) {
                WebSkinBridgeRuntime.state().optString("page") in setOf("TIMELINE", "TOOLS")
            }
            val actualPage = WebSkinBridgeRuntime.state().optString("page")
            assertEquals("Android Back should dismiss the skin-owned chooser and remain on Clear",
                "TIMELINE", actualPage)
            assertEquals("the native chooser must no longer be open", "true",
                evaluate(view, "!document.querySelector('#timelineCreateDialog')?.open"))
            assertEquals(0L, store.draftCount())
            assertEquals(0L, store.identityCount())
            assertEquals(1L, store.nextSequencePreview())

            evaluate(view,
                "document.querySelector('.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(10_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS" &&
                    evaluate(view, "!document.querySelector('#settingsPage').hidden") == "true"
            }
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            val settingsBackReturned = runCatching {
                compose.waitUntil(10_000) {
                    WebSkinBridgeRuntime.state().optString("page") != "SETTINGS"
                }
            }.isSuccess
            val afterSettingsBack = WebSkinBridgeRuntime.state()
            assertTrue("Settings Back stayed on its page: host=$afterSettingsBack; " +
                "skinBackHandlerInstalled=${WebSkinBridgeRuntime.skinBackHandler != null}; " +
                "skinBackResult=${runCatching {
                    evaluate(view, "window.PowerCalSkin.handleHostBack()")
                }.getOrDefault("unavailable")}", settingsBackReturned)
            assertEquals("Android Back from Clear Settings returns to the Clear timeline",
                "TIMELINE", afterSettingsBack.optString("page"))
            assertEquals("Clear timeline should be visible after settings Back", "true",
                evaluate(view, "!document.querySelector('#timelinePage').hidden"))
            assertEquals("Clear has no skin modal to consume Back", "false",
                evaluate(view, "window.PowerCalSkin.handleHostBack()"))
            assertEquals(0L, store.draftCount())
            assertEquals(0L, store.identityCount())
            assertEquals(1L, store.nextSequencePreview())

            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            val fallbackReached = runCatching {
                compose.waitUntil(10_000) {
                    WebSkinBridgeRuntime.state().optString("page") != "TIMELINE"
                }
            }.isSuccess
            val afterFallback = WebSkinBridgeRuntime.state()
            assertTrue("Back did not route to Native recovery: host=$afterFallback; " +
                "skinBackHandlerInstalled=${WebSkinBridgeRuntime.skinBackHandler != null}",
                fallbackReached)
            assertEquals("Back without a skin-owned modal must retain the Native recovery route",
                "TOOLS", afterFallback.optString("page"))
            assertEquals(0L, store.draftCount())
            assertEquals(0L, store.identityCount())
            assertEquals(1L, store.nextSequencePreview())
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            if (hadSkinSelection) {
                manager.select(originalSkin)
            } else {
                check(skinPreferences.edit().remove("selected").commit())
                PowerCalSkinRegistry.reset()
            }
            val restoreOnboarding = onboarding.edit()
            if (hadOnboardingFlag) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", originalOnboardingFlag)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            check(restoreOnboarding.commit())
        }
    }

    @Test
    fun androidBackDismissesImeBeforeNativeAndClearUntouchedEditors() {
        assumeTrue("run against an isolated Android emulator", Build.HARDWARE == "ranchu" ||
            Build.HARDWARE == "goldfish")

        val context = compose.activity.applicationContext
        val nativeDatabase = "back-ime-native-${UUID.randomUUID()}.db"
        val clearDatabase = "back-ime-clear-${UUID.randomUUID()}.db"
        val nativeStore = PowerStore(context, nativeDatabase)
        val clearStore = PowerStore(context, clearDatabase)
        val manager = SkinPackageManager(context)
        val skinPreferences = context.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
        val hadSkinSelection = skinPreferences.contains("selected")
        val originalSkin = skinPreferences.getString("selected", manager.currentId()) ?: manager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadOnboardingFlag = onboarding.contains("system-calendar-import-asked")
        val originalOnboardingFlag = onboarding.getBoolean("system-calendar-import-asked", false)
        val nativeTitle = androidx.compose.ui.test.hasSetTextAction() and hasText("标题")

        fun imeVisible(): Boolean = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true

        fun assertNoWrites(store: PowerStore, renderer: String) {
            assertTrue("$renderer editor remains unpersisted", store.all().isEmpty())
            assertEquals(0L, store.draftCount())
            assertEquals(0, store.pendingCount())
            assertEquals(0L, store.commitCount())
            assertEquals(0L, store.identityCount())
            assertEquals(1L, store.nextSequencePreview())
        }

        try {
            check(onboarding.edit().putBoolean("system-calendar-import-asked", true).commit())
            manager.selectNativeForEmergencyRecovery()
            PowerCalSkinRegistry.reset()
            compose.runOnUiThread {
                compose.activity.setContent {
                    androidx.compose.runtime.key("sep12-native-ime-back") {
                        PowerCalTheme { PowerCalApp(nativeStore) }
                    }
                }
            }
            compose.waitUntil(20_000) {
                compose.onAllNodesWithContentDescription("Memo 未完成 0")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Memo 未完成 0").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("新增无日期 Memo")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("新增无日期 Memo").performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(nativeTitle).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(nativeTitle).performClick()
            compose.waitUntil(15_000) { imeVisible() }
            assertNoWrites(nativeStore, "Native")

            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(15_000) { !imeVisible() }
            assertTrue("first Back must leave the Native editor open",
                compose.onAllNodes(nativeTitle).fetchSemanticsNodes().isNotEmpty())
            assertNoWrites(nativeStore, "Native after first Back")
            println("SEP12_IME_BACK_RENDERER=Native imeDismissed=true editorRetained=true " +
                "formal=0 drafts=0 commits=0 identities=0 sequence=1")

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
                manager.select("powercal-clear-default")
                compose.activity.setContent {
                    androidx.compose.runtime.key("sep12-clear-ime-back") {
                        PowerCalTheme { PowerCalApp(clearStore) }
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
            compose.waitUntil(20_000) {
                evaluate(view, "!!window.PowerCalSkin?.receive") == "true"
            }
            tapWebSelector(view, ".clear-tabbar [data-action=memo]")
            compose.waitUntil(15_000) { evaluate(view, "!document.querySelector('#memoPage').hidden") == "true" }
            tapWebSelector(view, "#memoPage [data-action=createMemo]")
            compose.waitUntil(15_000) {
                evaluate(view, "!document.querySelector('#skinSheet').hidden && " +
                    "!!document.querySelector('#sheetFields [data-field=title]')") == "true"
            }
            tapWebSelector(view, "#sheetFields [data-field=title]")
            compose.waitUntil(15_000) { imeVisible() }
            assertEquals("Clear editor baseline is transient", "true",
                evaluate(view, "document.querySelector('#sheetFields [data-field=title]')?.value === ''"))
            assertNoWrites(clearStore, "Clear")

            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(15_000) { !imeVisible() }
            assertEquals("first Back must leave the Clear-owned editor sheet open", "true",
                evaluate(view, "!document.querySelector('#skinSheet').hidden && " +
                    "!!document.querySelector('#sheetFields [data-field=title]')"))
            assertTrue("Clear Host still projects the no-diff deck as open",
                WebSkinBridgeRuntime.state().optJSONObject("editing")
                    ?.optJSONObject("deck")?.optBoolean("open") == true)
            assertNoWrites(clearStore, "Clear after first Back")
            println("SEP12_IME_BACK_RENDERER=Clear imeDismissed=true editorRetained=true " +
                "formal=0 drafts=0 commits=0 identities=0 sequence=1")
            println("SEP12_NATIVE_CLEAR_IME_BACK_PARITY imeDismissedFirst=true " +
                "editorRetained=true noDiffWrites=0 api=35")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            nativeStore.close()
            clearStore.close()
            context.deleteDatabase(nativeDatabase)
            context.deleteDatabase(clearDatabase)
            if (hadSkinSelection) {
                manager.select(originalSkin)
            } else {
                check(skinPreferences.edit().remove("selected").commit())
                PowerCalSkinRegistry.reset()
            }
            val restoreOnboarding = onboarding.edit()
            if (hadOnboardingFlag) restoreOnboarding.putBoolean("system-calendar-import-asked", originalOnboardingFlag)
            else restoreOnboarding.remove("system-calendar-import-asked")
            check(restoreOnboarding.commit())
        }
    }
}
