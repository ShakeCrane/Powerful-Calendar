package dev.powerfulcalendar

import android.app.ActivityManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.powerfulcalendar.reminders.ReminderScheduler
import dev.powerfulcalendar.ui.v2.GlassEffects
import dev.powerfulcalendar.ui.v2.PowerCalApp
import dev.powerfulcalendar.ui.v2.PowerCalStores
import dev.powerfulcalendar.ui.v2.PowerCalTheme

/**
 * Single activity host. Theme follows the system live (the activity declares `uiMode` as a handled
 * config change, so a light/dark switch recomposes instead of recreating and losing the editor).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val animationScale = runCatching {
            Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        val lowRam = runCatching {
            getSystemService(ActivityManager::class.java)?.isLowRamDevice ?: false
        }.getOrDefault(false)
        val reduceMotion = animationScale == 0f
        val effects = GlassEffects(
            blur = !lowRam && !reduceMotion,
            animationScale = if (animationScale <= 0f) 1f else animationScale,
            reduceMotion = reduceMotion
        )

        ReminderScheduler.ensureChannels(this)
        val store = PowerCalStores.get(applicationContext)

        setContent {
            PowerCalTheme(effects = effects) {
                PowerCalApp(store)
            }
        }
    }
}
