package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** API/device rendering and scroll reachability for the complete stale-replacement review. */
@RunWith(AndroidJUnit4::class)
class ConflictReviewInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fullBeforeAndAfterSnapshotIsScrollableAndRequiresExplicitConfirmation() {
        val context: Context = compose.activity.applicationContext
        val databaseName = "pc-issue-164-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        try {
            val zone = ZoneId.of("Asia/Shanghai")
            val day = LocalDate.of(2026, 10, 25)
            val overrideKey = "2026-11-27T11:20"
            val beforeForm = DraftForm.fresh(TemporalKind.ACTIVITY, day, zone).copy(
                title = "BEFORE_TITLE",
                startText = "2026-10-25T09:15",
                endText = "2026-10-25T10:45",
                location = "BEFORE_LOCATION",
                notes = "BEFORE_NOTE " + "长备注内容 ".repeat(45),
                reminderMinutes = 10,
                reminderMethod = ReminderMethod.MESSAGE,
                reminderTiming = ReminderTiming.OFFSET,
                enabled = true,
                source = "manual",
                trackingEnabled = true
            )
            val originalDraft = store.newDraft(beforeForm)
            store.commit(originalDraft.draftId)
            val original = requireNotNull(store.get(originalDraft.eventSeq))

            val afterForm = DraftForm.from(original).copy(
                title = "AFTER_TITLE",
                startText = "2026-10-25T11:20",
                endText = "2026-10-25T13:50",
                location = "AFTER_LOCATION",
                notes = "AFTER_NOTE " + "替换后的长备注 ".repeat(45),
                reminderMinutes = 5,
                reminderMethod = ReminderMethod.ALARM,
                reminderTiming = ReminderTiming.DAY_START,
                enabled = false,
                source = "ics-import",
                zoneId = "UTC",
                recurrence = Recurrence(
                    freq = RecurrenceFreq.MONTHLY,
                    interval = 3,
                    count = 8,
                    untilMillis = day.plusDays(180).atTime(13, 50).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli(),
                    activeFromDate = day.toString(),
                    activeUntilDate = day.plusMonths(9).toString(),
                    byDay = setOf(DayOfWeek.FRIDAY),
                    exceptions = setOf("2026-12-25T11:20"),
                    overrides = mapOf(
                        overrideKey to OccurrenceOverride(
                            LocalDate.of(2026, 11, 27).atTime(13, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli(),
                            LocalDate.of(2026, 11, 27).atTime(14, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli(),
                            "AFTER_OVERRIDE"
                        )
                    )
                ),
                completedKeys = emptySet()
            )
            val staleDraft = store.newDraft(afterForm, original.eventSeq, DraftOperation.REPLACE)
            assertTrue(
                "creating a later completion must make this replacement stale",
                store.setCompleted(original.eventSeq, "single", true, expectedRevision = original.revision)
            )
            val latestBeforeReview = requireNotNull(store.get(original.eventSeq))
            val controller = DeckController(
                StoreDeckPersistence(store), ioDispatcher = Dispatchers.Unconfined
            )
            runBlocking { controller.open(staleDraft.draftId) }
            assertFalse("stale replacement must pause for explicit review", runBlocking { controller.commit() })
            val prompt = requireNotNull(controller.state.conflict)
            val rows = snapshotRows(prompt.current, prompt.approvedForm)
            assertEquals("all editable snapshot fields must be represented", 25, rows.size)
            assertEquals("通知", rows.single { it.label == "提醒方式" }.before)
            assertEquals("闹钟", rows.single { it.label == "提醒方式" }.after)
            assertEquals("按提前量", rows.single { it.label == "提醒时机" }.before)
            assertEquals("当天开始", rows.single { it.label == "提醒时机" }.after)
            assertEquals("single", rows.single { it.label == "已完成实例槽位" }.before)
            assertEquals("无", rows.single { it.label == "已完成实例槽位" }.after)
            assertEquals(day.toString(), rows.single { it.label == "重复生效开始日期" }.after)
            assertEquals(day.plusMonths(9).toString(), rows.single { it.label == "重复生效结束日期" }.after)
            assertEquals("formal record remains unchanged before approval", latestBeforeReview, store.get(original.eventSeq))

            compose.setContent {
                PowerCalTheme {
                    DraftDeckDialog(controller, null, {}, {}, {}, {}, { emptyList() })
                }
            }
            compose.waitForIdle()
            compose.onNodeWithText("完整差异").assertIsDisplayed()
            compose.onNodeWithText("确认整份覆盖").assertIsDisplayed()

            val screenshots = requireNotNull(compose.activity.externalCacheDir)
            fun saveScreenshot(name: String) {
                val file = File(screenshots, name)
                file.outputStream().use { output ->
                    assertTrue(
                        "conflict review screenshot must encode",
                        requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                            .compress(Bitmap.CompressFormat.PNG, 100, output)
                    )
                }
                assertTrue("screenshot must contain rendered pixels", file.length() > 0L)
                println("CONFLICT_REVIEW_SCREENSHOT=${file.absolutePath} bytes=${file.length()}")
            }

            saveScreenshot("pc-issue-164-conflict-review-top.png")
            fun scrollEveryMatchIntoView(text: String) {
                val count = compose.onAllNodesWithText(text).fetchSemanticsNodes().size
                assertTrue("expected review text in semantics tree: $text", count > 0)
                repeat(count) { index ->
                    compose.onAllNodesWithText(text)[index].performScrollTo().assertIsDisplayed()
                }
            }
            for (row in rows) {
                val state = if (row.changed) "将更改" else "保持"
                compose.onNodeWithText("${row.label} · $state").performScrollTo().assertIsDisplayed()
                scrollEveryMatchIntoView("现在：${row.before}")
                scrollEveryMatchIntoView("本卡：${row.after}")
            }
            saveScreenshot("pc-issue-164-conflict-review-bottom.png")
            println(
                "CONFLICT_REVIEW_API35 rows=${rows.size} changed=${rows.count { it.changed }} " +
                    "labels=${rows.joinToString("|") { it.label }}"
            )

            assertEquals("reviewing the scrollable diff cannot write the formal record", latestBeforeReview, store.get(original.eventSeq))
            compose.onNodeWithText("确认整份覆盖").performClick()
            compose.waitUntil(10_000) { controller.state.completion != null }
            assertEquals("AFTER_TITLE", store.get(original.eventSeq)?.title)
            assertEquals("approval creates exactly one replacement revision", latestBeforeReview.revision + 1, store.get(original.eventSeq)?.revision)
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }
}
