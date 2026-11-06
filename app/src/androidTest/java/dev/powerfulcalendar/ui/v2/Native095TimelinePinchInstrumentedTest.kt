package dev.powerfulcalendar.ui.v2

import android.content.Intent
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Runs only on isolated Native-reference or Web-QA AVDs; it never opens an editor or Store action. */
@RunWith(AndroidJUnit4::class)
class Native095TimelinePinchInstrumentedTest {
    @Test
    fun symmetricVerticalPinchChangesTimelineSpan() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val packageName = target.packageName

        val launch = target.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("PowerCal launch activity is unavailable")
        val activity = instrumentation.startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val webReference = AtomicReference<WebView?>()
        instrumentation.runOnMainSync {
            webReference.set(findWebView(activity.findViewById(android.R.id.content)))
        }
        val web = webReference.get()

        // The same visible destination and 24-hour viewport are used on the raw Native APK and Web APK.
        tap(instrumentation, 120f, 2250f)
        SystemClock.sleep(350)
        // Double-tapping the central ruler is the frozen 24-hour reset gesture.
        tap(instrumentation, 540f, 1250f)
        SystemClock.sleep(110)
        tap(instrumentation, 540f, 1250f)
        val initialHours = awaitStableHours(instrumentation, web) { abs(it - 24.0) < 0.01 }
        assertTrue("The timeline should begin at 24 hours per screen: $initialHours", abs(initialHours - 24.0) < 0.01)
        println("NATIVE095_PINCH_DEBUG stage=initial ${webDebug(instrumentation, web)}")

        val centerX = 540f
        val centerY = 1250f
        val startedAt = SystemClock.uptimeMillis()
        injectPinch(instrumentation, startedAt, centerX, centerY, 100f, 250f)

        val shouldChange = InstrumentationRegistry.getArguments().getString("expectChange")?.toBoolean() ?: true
        val expectedHours = InstrumentationRegistry.getArguments().getString("expectedHours")?.toDoubleOrNull()
        if (shouldChange) {
            val finalHours = awaitStableHours(instrumentation, web) { abs(it - initialHours) > 0.2 }
            assertTrue("A vertical two-finger pinch must change the timeline span: $initialHours -> $finalHours",
                abs(finalHours - initialHours) > 0.2)
            expectedHours?.let {
                assertTrue("Native/Web pinch result must stay within 0.1 hours of the shared reference: $finalHours vs $it",
                    abs(finalHours - it) < 0.1)
            }
            println("NATIVE095_PINCH_DEBUG stage=pinched ${webDebug(instrumentation, web)}")
            tap(instrumentation, 540f, 1250f)
            println("NATIVE095_PINCH_DEBUG stage=reset-tap-1 ${webDebug(instrumentation, web)}")
            SystemClock.sleep(110)
            tap(instrumentation, 540f, 1250f)
            println("NATIVE095_PINCH_DEBUG stage=reset-tap-2 ${webDebug(instrumentation, web)}")
            val resetHours = awaitStableHours(instrumentation, web) { abs(it - 24.0) < 0.01 }
            println("NATIVE095_PINCH_DEBUG stage=reset-settled ${webDebug(instrumentation, web)}")
            assertTrue("Double-tapping the ruler must restore 24 hours per screen: $resetHours",
                abs(resetHours - 24.0) < 0.01)
            injectPinch(instrumentation, SystemClock.uptimeMillis(), centerX, centerY, 100f, 250f)
            val repeatedHours = awaitStableHours(instrumentation, web) { abs(it - resetHours) > 0.2 }
            assertTrue("A new pinch after ruler reset must not inherit an ended pointer: $repeatedHours",
                abs(repeatedHours - resetHours) > 0.2)
            captureScreenIfRequested(instrumentation)
            println("NATIVE095_PINCH_REPEAT_RESULT reset=$resetHours final=$repeatedHours")
        } else {
            val finalHours = awaitStableHours(instrumentation, web) { abs(it - initialHours) < 0.01 }
            assertTrue("The baseline Web Runtime unexpectedly changed the viewport: $finalHours",
                abs(finalHours - initialHours) < 0.01)
        }
        println("NATIVE095_PINCH_RESULT package=$packageName initial=$initialHours")
    }

    private fun hoursLabel(instrumentation: android.app.Instrumentation): String? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        fun search(node: android.view.accessibility.AccessibilityNodeInfo): String? {
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            if (text?.contains("小时") == true && text.contains("屏")) return text
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                search(child)?.let { return it }
            }
            return null
        }
        return search(root)
    }

    private fun awaitStableHours(
        instrumentation: android.app.Instrumentation,
        web: WebView?,
        accepted: (Double) -> Boolean
    ): Double {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var candidate: Double? = null
        var candidateSince = 0L
        while (SystemClock.uptimeMillis() < deadline) {
            val value = currentHours(instrumentation, web)
            val now = SystemClock.uptimeMillis()
            if (value != null && accepted(value)) {
                if (candidate != null && abs(value - candidate) < 0.01) {
                    if (now - candidateSince >= 300) return value
                } else {
                    candidate = value
                    candidateSince = now
                }
            } else candidate = null
            SystemClock.sleep(80)
        }
        error("Timeline viewport did not settle at an accepted hours-per-screen value")
    }

    private fun currentHours(instrumentation: android.app.Instrumentation, web: WebView?): Double? {
        if (web != null) {
            val raw = evaluate(instrumentation, web,
                "Number.parseFloat(document.querySelector('#timeRangeLabel')?.textContent || '')")
            return raw.toDoubleOrNull()
        }
        val label = hoursLabel(instrumentation) ?: return null
        return Regex("[0-9]+(?:\\.[0-9]+)?").find(label)?.value?.toDoubleOrNull()
    }

    private fun captureScreenIfRequested(instrumentation: android.app.Instrumentation) {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString("captureEvidence") != "true") return
        val suffix = when (arguments.getString("captureSuffix")) {
            "native" -> "native"
            "web" -> "web"
            else -> "qa"
        }
        val descriptor = instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /sdcard/powercal-native095-pinch-$suffix.png"
        )
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun webDebug(instrumentation: android.app.Instrumentation, web: WebView?): String {
        if (web == null) return "native"
        return evaluate(instrumentation, web,
            "document.querySelector('#timeRangeLabel')?.textContent || 'missing'")
    }

    private fun evaluate(
        instrumentation: android.app.Instrumentation,
        web: WebView,
        script: String
    ): String {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            web.evaluateJavascript(script) { value -> result.set(value); completed.countDown() }
        }
        check(completed.await(5, TimeUnit.SECONDS)) { "WebView gesture state query timed out" }
        return result.get().orEmpty().removeSurrounding("\"")
    }

    private fun tap(instrumentation: android.app.Instrumentation, x: Float, y: Float) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(
            "input tap ${x.toInt()} ${y.toInt()}"
        )
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }


    private fun injectPinch(
        instrumentation: android.app.Instrumentation,
        downTime: Long,
        centerX: Float,
        centerY: Float,
        initialHalfSpan: Float,
        finalHalfSpan: Float
    ) {
        val automation = instrumentation.uiAutomation
        var eventTime = downTime

        fun send(action: Int, halfSpan: Float, pointerCount: Int) {
            val properties = Array(pointerCount) { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coordinates = Array(pointerCount) { index ->
                MotionEvent.PointerCoords().apply {
                    x = centerX
                    y = centerY + if (index == 0) -halfSpan else halfSpan
                    pressure = 1f
                    size = 1f
                    touchMajor = 18f
                    touchMinor = 18f
                }
            }
            val event = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                pointerCount,
                properties,
                coordinates,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
            try {
                assertTrue("Android must accept injected pointer event action=$action", automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }

        send(MotionEvent.ACTION_DOWN, initialHalfSpan, pointerCount = 1)
        SystemClock.sleep(50)
        eventTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), initialHalfSpan, 2)
        repeat(8) { step ->
            SystemClock.sleep(35)
            eventTime = SystemClock.uptimeMillis()
            val halfSpan = initialHalfSpan + (finalHalfSpan - initialHalfSpan) * (step + 1) / 8f
            send(MotionEvent.ACTION_MOVE, halfSpan, 2)
        }
        SystemClock.sleep(50)
        eventTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), finalHalfSpan, 2)
        SystemClock.sleep(40)
        eventTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_UP, finalHalfSpan, 1)
    }
}
