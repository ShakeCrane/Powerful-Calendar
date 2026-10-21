package dev.powerfulcalendar

import android.os.Bundle
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
                PowerCalApp(store)
            }
        }
    }
}
