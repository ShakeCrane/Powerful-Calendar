package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.core.api.v1.EventRecordV1
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h760dp-xhdpi")
class TimelineSkinContextContractTest {
    @get:Rule val ui = createComposeRule()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val noon = LocalDate.of(2026, 10, 21)
        .atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Before fun resetSkinState() {
        CompiledSkinRuntime.restoreNativeTimeline()
        PowerCalSkinRegistry.reset()
    }

    @After fun restoreSkinState() {
        CompiledSkinRuntime.restoreNativeTimeline()
        PowerCalSkinRegistry.reset()
    }

    @Test
    fun compiledTimelineReceivesDtosAndRoutesOnlyCurrentEventIdentity() {
        val owner = TemporalRecord(
            eventSeq = 73,
            revision = 5,
            kind = TemporalKind.ACTIVITY,
            title = "设计评审",
            startMillis = noon,
            endMillis = noon + 3_600_000,
            zoneId = zone.id
        )
        val opened = mutableListOf<Pair<TemporalRecord, String?>>()
        var moved: Pair<TemporalRecord, Long>? = null
        var created: Pair<TemporalKind, Long>? = null
        var changedCamera: TimelineCoordinate.Camera? = null
        var projectedY: Float? = null

        CompiledSkinRuntime.registerTimeline(object : CompiledTimelineSkin {
            override val rendererId = "test/timeline-dto"

            @Composable override fun Render(context: TimelineSkinContext) {
                val skinRecords: List<EventRecordV1> = context.records
                val skinCamera: NativeTimelineCamera = context.camera
                val open: (Long, Long, String?) -> Unit = context.onOpenEvent
                val move: (Long, Long, Long) -> Unit = context.onMoveEvent
                val create: (EventKindV1, Long) -> Unit = context.onCreate
                projectedY = context.yOf(noon, 600f)
                Column(Modifier.fillMaxSize()) {
                    Text("event:${skinRecords.single().title}")
                    Text("camera:${skinCamera.anchorMillis}:${skinCamera.hours}")
                    Button(onClick = {
                        open(skinRecords.single().eventSeq, skinRecords.single().revision, "2026-10-21T12:00")
                    }) { Text("Open current") }
                    Button(onClick = {
                        open(skinRecords.single().eventSeq, skinRecords.single().revision + 1, null)
                    }) { Text("Open stale") }
                    Button(onClick = {
                        move(skinRecords.single().eventSeq, skinRecords.single().revision, noon + 600_000)
                    }) { Text("Move current") }
                    Button(onClick = { create(EventKindV1.DEADLINE, noon + 1_800_000) }) {
                        Text("Create deadline")
                    }
                    Button(onClick = { context.onCameraChange(NativeTimelineCamera(noon - 60_000, 12.0)) }) {
                        Text("Change camera")
                    }
                }
            }
        })

        ui.setContent {
            PowerCalTheme {
                SkinnableTimelineScreen(
                    records = listOf(owner),
                    camera = TimelineCoordinate.Camera(noon, 24.0),
                    nowMillis = noon,
                    zone = zone,
                    pendingCount = 0,
                    aiLocked = false,
                    onCameraChange = { changedCamera = it },
                    onOpenEvent = {},
                    onOpenOccurrence = { record, key -> opened += record to key },
                    onMoveEvent = { record, target -> moved = record to target },
                    onCreate = { kind, atMillis -> created = kind to atMillis },
                    onToday = {},
                    onOpenDeck = {},
                    onOpenTools = {}
                )
            }
        }

        ui.onNodeWithText("event:设计评审").assertIsDisplayed()
        ui.onNodeWithText("camera:$noon:24.0").assertIsDisplayed()
        ui.onNodeWithText("Open current").performClick()
        ui.onNodeWithText("Open stale").performClick()
        ui.onNodeWithText("Move current").performClick()
        ui.onNodeWithText("Create deadline").performClick()
        ui.onNodeWithText("Change camera").performClick()

        ui.runOnIdle {
            assertEquals(listOf(owner to "2026-10-21T12:00"), opened)
            assertEquals(owner to (noon + 600_000), moved)
            assertEquals(TemporalKind.DEADLINE to (noon + 1_800_000), created)
            assertEquals(TimelineCoordinate.Camera(noon - 60_000, 12.0), changedCamera)
            assertNotNull(projectedY)
            assertEquals(TimelineCoordinate.yOf(noon, noon, 600f, 24.0), projectedY!!, 0.001f)
        }
    }
}
