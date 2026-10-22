package dev.powerfulcalendar

import android.os.Bundle
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.powerfulcalendar.ui.v2.AlternatingTapRecovery
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.powerfulcalendar.reminders.ReminderScheduler
import dev.powerfulcalendar.ui.v2.ActiveVisualSkin
import dev.powerfulcalendar.ui.v2.PowerCalApp
import dev.powerfulcalendar.ui.v2.PowerCalStores
import dev.powerfulcalendar.ui.v2.PowerCalTheme
import dev.powerfulcalendar.ui.v2.SkinPackageManager
import dev.powerfulcalendar.ui.v2.rememberSystemGlassEffects

/**
 * Single activity host. Theme follows the system live (the activity declares `uiMode` as a handled
 * config change, so a light/dark switch recomposes instead of recreating and losing the editor).
 */
class MainActivity : ComponentActivity() {
    private data class Down(val x: Float, val y: Float, val startedMs: Long)
    private val fingers = mutableMapOf<Int, Down>()
    private var alternatingRecovery: AlternatingTapRecovery? = null
    private var nativeRecoverySignal by mutableIntStateOf(0)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Observe at the Activity boundary, before Compose/WebView dispatch.
        // No imported skin can prevent this recognizer from seeing the taps.
        val detector = alternatingRecovery
            ?: AlternatingTapRecovery(resources.displayMetrics.density).also {
                alternatingRecovery = it
            }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount > 2) {
                    fingers.clear()
                    detector.reset()
                } else {
                    val index = event.actionIndex
                    fingers[event.getPointerId(index)] = Down(
                        event.getX(index), event.getY(index), event.eventTime
                    )
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val down = fingers.remove(event.getPointerId(index))
                if (down != null) {
                    val x = event.getX(index)
                    val y = event.getY(index)
                    val movement = kotlin.math.hypot(x - down.x, y - down.y)
                    val duration = event.eventTime - down.startedMs
                    if (movement <= 24f * resources.displayMetrics.density &&
                        detector.completedTap(x, y, event.eventTime, duration) &&
                        SkinPackageManager(applicationContext).currentId() != "powercal-default") {
                        // Immediate persistent native rollback; no database/skin uninstall.
                        SkinPackageManager(applicationContext).select("powercal-default")
                        nativeRecoverySignal++
                        return true
                    }
                    if (movement > 24f * resources.displayMetrics.density) detector.reset()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                fingers.clear()
                detector.reset()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ReminderScheduler.ensureChannels(this)
        val store = PowerCalStores.get(applicationContext)
        // Restore only pre-validated, data-only presentation; corrupt packages fail to default.
        SkinPackageManager(applicationContext).restoreSelection()
        ActiveVisualSkin.install()

        setContent {
            PowerCalTheme(effects = rememberSystemGlassEffects()) {
                PowerCalApp(store, nativeRecoverySignal)
            }
        }
    }
}
