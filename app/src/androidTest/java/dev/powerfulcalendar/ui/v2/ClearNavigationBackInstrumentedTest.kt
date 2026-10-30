package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.PowerStore
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
}
