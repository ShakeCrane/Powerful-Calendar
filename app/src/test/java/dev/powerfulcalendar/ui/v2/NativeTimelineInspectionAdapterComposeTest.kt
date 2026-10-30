package dev.powerfulcalendar.ui.v2

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NativeTimelineInspectionAdapterComposeTest {
    @get:Rule val ui = createComposeRule()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val noon = LocalDate.of(2026, 10, 30).atTime(12, 0).atZone(zone)
        .toInstant().toEpochMilli()

    @Test
    fun lockedTimelineEventUsesNativeInspectionAndSkinAwareDialogShell() {
        val event = TemporalRecord(
            eventSeq = 42,
            revision = 3,
            kind = TemporalKind.ACTIVITY,
            title = "复诊预约",
            notes = "带检查报告",
            startMillis = noon + 3_600_000L,
            endMillis = noon + 5_400_000L,
            zoneId = zone.id
        )

        ui.setContent {
            PowerCalTheme {
                TimelineScreen(
                    records = listOf(event),
                    camera = TimelineCoordinate.Camera(noon, 24.0),
                    nowMillis = noon,
                    zone = zone,
                    pendingCount = 1,
                    aiLocked = true,
                    onCameraChange = {},
                    onOpenEvent = {},
                    onMoveEvent = { _, _ -> },
                    onCreate = { _, _ -> },
                    onToday = {},
                    onOpenDeck = {},
                    onOpenTools = {}
                )
            }
        }

        ui.onNodeWithTag(eventTag(event)).performClick()
        ui.onNodeWithText("复诊预约").assertIsDisplayed()
        ui.onNodeWithText("13:00 – 13:30").assertIsDisplayed()
        ui.onNodeWithText("带检查报告").assertIsDisplayed()
        ui.onNodeWithText("AI 正在处理卡组，暂时只可查看。").assertIsDisplayed()
        ui.onNodeWithText("完成").performClick()
        ui.onNodeWithText("AI 正在处理卡组，暂时只可查看。").assertDoesNotExist()
    }

    private fun eventTag(record: TemporalRecord): String {
        val localStart = java.time.Instant.ofEpochMilli(record.startMillis!!)
            .atZone(record.zone).toLocalDateTime()
        val key = "${record.eventSeq}:${RecurrenceEngine.keyFor(localStart)}"
        return "timeline-event-${record.eventSeq}-${key.hashCode().toUInt().toString(16)}"
    }
}
