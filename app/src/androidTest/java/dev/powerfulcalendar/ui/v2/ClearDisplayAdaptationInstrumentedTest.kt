package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class ClearDisplayAdaptationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, script: String): String {
        val value = AtomicReference<String>()
        val complete = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(script) {
                value.set(it)
                complete.countDown()
            }
        }
        assertTrue("WebView callback timed out: $script", complete.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    @Test
    fun clearRuntimeAdaptsToSystemFontMotionAndAccessibilityTree() {
        assumeTrue("run against an isolated Android emulator", Build.HARDWARE == "ranchu" ||
            Build.HARDWARE == "goldfish")

        val context = compose.activity.applicationContext
        val database = "clear-display-${UUID.randomUUID()}.db"
        val store = PowerStore(context, database)
        val manager = SkinPackageManager(context)
        val skinPreferences = context.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
        val hadSkinSelection = skinPreferences.contains("selected")
        val originalSkin = skinPreferences.getString("selected", manager.currentId()) ?: manager.currentId()
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
                evaluate(view, "!!window.PowerCalSkin?.receive") == "true"
            }

            val configuredFontScale = compose.activity.resources.configuration.fontScale
            val systemFontScale = Settings.System.getFloat(
                context.contentResolver, Settings.System.FONT_SCALE, 1f
            )
            val reducedMotionExpected = Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
            val textZoom = compose.runOnUiThread { view.settings.textZoom }
            val webMetrics = JSONObject(evaluate(view, """(()=>{
              const visible=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);
                return !e.hidden&&s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0};
              const controls=[...document.querySelectorAll('button,input,textarea,select,[role="button"],[tabindex]')]
                .filter(visible);
              const unnamed=controls.filter(e=>!(e.getAttribute('aria-label')||e.getAttribute('aria-labelledby')||
                e.innerText?.trim()||e.value||e.title));
              const nav=[...document.querySelectorAll('.clear-tabbar button')].map(e=>
                e.getAttribute('aria-label')||e.innerText.trim());
              const title=document.querySelector('#dayTitle').getBoundingClientRect();
              const tab=document.querySelector('.clear-tabbar').getBoundingClientRect();
              const button=document.querySelector('.timeline-view-controls button').getBoundingClientRect();
              return {viewportWidth:innerWidth,viewportHeight:innerHeight,
                documentWidth:document.documentElement.scrollWidth,
                bodyFont:getComputedStyle(document.body).fontSize,
                reducedMotion:matchMedia('(prefers-reduced-motion: reduce)').matches,
                transitionDuration:getComputedStyle(document.querySelector('.clear-tabbar button')).transitionDuration,
                unnamedControls:unnamed.length,nav,
                titleVisible:title.width>0&&title.left>=0&&title.right<=innerWidth,
                tabVisible:tab.width>0&&tab.left>=0&&tab.right<=innerWidth,
                timelineControlVisible:button.width>0&&button.left>=0&&button.right<=innerWidth};
            })()"""))

            println("F09_CLEAR_DISPLAY fontScale=$systemFontScale textZoom=$textZoom " +
                "orientation=${compose.activity.resources.configuration.orientation} " +
                "reducedMotionExpected=$reducedMotionExpected metrics=$webMetrics")
            assertEquals("WebView activity must use the current Android font scale",
                systemFontScale, configuredFontScale, 0.01f)
            assertTrue("WebView text zoom must follow Android font scaling: fontScale=$systemFontScale textZoom=$textZoom",
                textZoom >= (systemFontScale * 100f).toInt())
            assertEquals("this acceptance run is portrait", android.content.res.Configuration.ORIENTATION_PORTRAIT,
                compose.activity.resources.configuration.orientation)
            val nav = webMetrics.getJSONArray("nav")
            assertEquals("bottom navigation actions remain in the expected reading order",
                listOf("时间轴", "月历", "Memo", "AI", "设置"),
                (0 until nav.length()).map { nav.getString(it) })
            assertEquals("visible controls need an accessible name", 0,
                webMetrics.getInt("unnamedControls"))
            assertTrue("Clear content must reflow without root horizontal clipping: $webMetrics",
                webMetrics.getInt("documentWidth") <= webMetrics.getInt("viewportWidth"))
            assertTrue("Clear title stays in viewport: $webMetrics", webMetrics.getBoolean("titleVisible"))
            assertTrue("Clear navigation stays in viewport: $webMetrics", webMetrics.getBoolean("tabVisible"))
            assertTrue("timeline control stays in viewport: $webMetrics",
                webMetrics.getBoolean("timelineControlVisible"))
            if (reducedMotionExpected) {
                assertTrue("WebView should honor Android reduced motion: $webMetrics",
                    webMetrics.getBoolean("reducedMotion"))
                assertTrue("CSS animations/transitions should be suppressed: $webMetrics",
                    webMetrics.getString("transitionDuration").split(',').all { it.trim() in setOf("0s", "0ms") })
            }

            println("F09_CLEAR_DISPLAY fontScale=$systemFontScale textZoom=$textZoom " +
                "orientation=${compose.activity.resources.configuration.orientation} " +
                "reducedMotionExpected=$reducedMotionExpected metrics=$webMetrics")
            println("F09_CLEAR_DOM_ACCESSIBILITY navOrder=${nav} " +
                "visibleUnnamedControls=${webMetrics.getInt("unnamedControls")}")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
            if (hadSkinSelection) manager.select(originalSkin)
            else {
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
