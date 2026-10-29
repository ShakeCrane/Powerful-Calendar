package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.MainActivity
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivityRecoveryTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityDispatchOpensNativeToolsOnEighthAlternatingZoneTapWithoutStoreWrites() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = SkinPackageManager(context)
        val previousSkin = manager.currentId()
        val store = PowerCalStores.get(context)
        val before = snapshot(store)

        try {
            manager.select("powercal-clear-default")
            ui.waitForIdle()
            val firstZone = 70f
            val secondZone = 200f
            val y = 300f
            val beforeFinalTap = mutableListOf<String>()
            var finalTapConsumed = false
            ui.runOnIdle {
                val activity = ui.activity
                // Same pointer ID throughout: proves the current two-zone rule, not two-finger identity.
                var start = SystemClock.uptimeMillis()
                repeat(8) { index ->
                    dispatchTap(activity, firstZone, y, start + index * 170L)
                }
                assertEquals("single-location tapping must not switch skins",
                    "powercal-clear-default", manager.currentId())

                start += 8 * 170L
                repeat(7) { index ->
                    dispatchTap(activity,
                        if (index % 2 == 0) firstZone else secondZone,
                        y,
                        start + index * 170L
                    )
                    beforeFinalTap += manager.currentId()
                }
                finalTapConsumed = dispatchTap(activity, secondZone, y, start + 7 * 170L)
            }
            ui.waitForIdle()

            assertEquals(7, beforeFinalTap.size)
            assertTrue("recovery must not happen before the eighth valid tap",
                beforeFinalTap.all { it == "powercal-clear-default" })
            assertTrue("the eighth valid tap is consumed by the Activity recovery route",
                finalTapConsumed)
            assertEquals("powercal-default", manager.currentId())
            assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
            ui.onNodeWithText("工具与设置").assertIsDisplayed()
            ui.onNodeWithText(
                "已切换至原生设置页，日历数据及已安装皮肤均保留。"
            ).assertIsDisplayed()
            val restoredManager = SkinPackageManager(context)
            restoredManager.restoreSelection()
            assertEquals("powercal-default", restoredManager.currentId())
            assertEquals("recovery must not mutate events, drafts, IDs or commits", before,
                snapshot(store))
        } finally {
            runCatching { manager.select(previousSkin) }
                .onFailure { manager.select("powercal-default") }
            manager.restoreSelection()
        }
    }

    private fun dispatchTap(
        activity: MainActivity,
        x: Float,
        y: Float,
        downAt: Long
    ): Boolean {
        val down = MotionEvent.obtain(
            downAt, downAt, MotionEvent.ACTION_DOWN, x, y, 0
        )
        val up = MotionEvent.obtain(
            downAt, downAt + 70L, MotionEvent.ACTION_UP, x, y, 0
        )
        try {
            activity.dispatchTouchEvent(down)
            return activity.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun snapshot(store: PowerStore): StoreSnapshot = StoreSnapshot(
        records = store.all(),
        identities = store.identityCount(),
        nextSequence = store.nextSequencePreview(),
        pending = store.pendingCount(),
        commits = commitCount(store)
    )

    private fun commitCount(store: PowerStore): Int {
        val db = SQLiteDatabase.openDatabase(
            ApplicationProvider.getApplicationContext<Context>()
                .getDatabasePath(store.databaseName).path,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        return try {
            db.rawQuery("SELECT COUNT(*) FROM commits", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
        } finally {
            db.close()
        }
    }

    private data class StoreSnapshot(
        val records: List<TemporalRecord>,
        val identities: Long,
        val nextSequence: Long,
        val pending: Int,
        val commits: Int
    )
}
