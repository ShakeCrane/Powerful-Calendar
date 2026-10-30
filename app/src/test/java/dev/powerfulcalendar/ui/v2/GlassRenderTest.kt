package dev.powerfulcalendar.ui.v2


import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.powerfulcalendar.core.*
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.*

/** Software-rendered review artifacts. These images do not establish device/GPU fidelity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassRenderTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private var store: PowerStore? = null
    @After fun closeStore() { store?.close() }

    private fun capture(name: String, dialog: Boolean = false) {
        val folder = File("build/reports/glass-ui").also { it.mkdirs() }
        ui.waitForIdle()
        ui.runOnUiThread {
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            if (dialog) org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView.draw(canvas)
            File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun renderBothThemesForVisualReview() {
        val zone = ZoneId.of("Asia/Shanghai")
        val noon = LocalDate.of(2026, 10, 20).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val events = listOf(
            TemporalRecord(42, 1, TemporalKind.ALARM, "慢慢醒来", startMillis = noon - 4 * 3_600_000, zoneId = zone.id),
            TemporalRecord(43, 1, TemporalKind.ACTIVITY, "留给设计的时间", startMillis = noon - 3_600_000, endMillis = noon + 2 * 3_600_000, zoneId = zone.id),
            TemporalRecord(44, 1, TemporalKind.DEADLINE, "交付设计方案", deadlineDate = "2026-10-20", zoneId = zone.id)
        )
        val dark = mutableStateOf(false)
        ui.setContent { PowerCalTheme(darkTheme = dark.value, effects = GlassEffects(reduceMotion = true)) {
            Surface(Modifier.fillMaxSize()) {
                TimelineScreen(events, TimelineCoordinate.Camera(noon, 12.0), noon, zone, 7, false,
                    {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
            }
        } }
        capture("timeline-light.png")
        ui.runOnIdle { dark.value = true }
        capture("timeline-dark.png")
    }

    @Test fun renderDeckCompactExpandedAndDark() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(PowerStore.DATABASE_NAME)
        val database = PowerStore(context).also { store = it }
        val zone = ZoneId.of("Asia/Shanghai")
        val base = DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.of(2026, 10, 20), zone).copy(
            title = "与团队对齐设计方向", location = "工作室 · 讨论区", notes = "带上新的时间轴方案，留十分钟讨论交互细节。"
        )
        val first = database.newDraft(base)
        repeat(3) { database.newDraft(base.copy(title = "待确认计划 ${it + 2}")) }
        val controller = DeckController(
            StoreDeckPersistence(database),
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        ).also { kotlinx.coroutines.runBlocking { it.open(first.draftId) } }
        val dark = mutableStateOf(false)
        val noon = LocalDate.of(2026, 10, 20).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        ui.setContent { PowerCalTheme(darkTheme = dark.value, effects = GlassEffects(reduceMotion = true)) {
            Surface(Modifier.fillMaxSize()) {
                TimelineScreen(emptyList(), TimelineCoordinate.Camera(noon, 24.0), noon, zone, 4, false,
                    {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
            }
            DraftDeckDialog(controller, null, {}, {}, {}, {}, { emptyList() })
        } }
        capture("deck-compact-light.png", true)
        ui.onNodeWithText("轻点卡片，展开完整编辑").performClick()
        capture("deck-expanded-light.png", true)
        ui.runOnIdle { dark.value = true }
        capture("deck-expanded-dark.png", true)
    }

    @Test fun renderMonth() {
        val zone = ZoneId.of("Asia/Shanghai")
        val record = TemporalRecord(44, 1, TemporalKind.DEADLINE, "交付设计方案", deadlineDate = "2026-10-20", zoneId = zone.id)
        ui.setContent { PowerCalTheme(effects = GlassEffects(reduceMotion = true)) {
            Surface(Modifier.fillMaxSize()) { MonthScreen(listOf(record), LocalDate.of(2026, 10, 20), zone, false, {}, {}, { _, _ -> }, {}) }
        } }
        capture("month-light.png")
    }
}
