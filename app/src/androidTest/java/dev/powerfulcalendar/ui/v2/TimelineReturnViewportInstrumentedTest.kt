package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.PowerStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TimelineReturnViewportInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private data class CameraSnapshot(
        val page: String,
        val anchorMillis: Long,
        val hours: Double
    )

    /** App resources/system services come from the target Activity; all app-owned files are isolated. */
    private class IsolatedAppContext(
        target: Context,
        private val storage: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(storage.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory) { "unable to create isolated instrumentation storage" }
        }

        private fun databaseAlias(name: String) = "$namespace-$name"
        private fun isolatedDatabasePath(name: String): File =
            storage.getDatabasePath(databaseAlias(name)).also {
                check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true) {
                    "unable to create isolated instrumentation database directory"
                }
            }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences("$namespace-$name", mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File =
            File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = isolatedDatabasePath(name)
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            isolatedDatabasePath(name)
            return storage.openOrCreateDatabase(databaseAlias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            isolatedDatabasePath(name)
            return storage.openOrCreateDatabase(databaseAlias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = storage.deleteDatabase(databaseAlias(name))
    }

    private fun cameraSnapshot(): CameraSnapshot {
        var snapshot: CameraSnapshot? = null
        compose.runOnIdle {
            val appState = WebSkinBridgeRuntime.state()
            val timeline = appState.getJSONObject("timeline")
            snapshot = CameraSnapshot(
                page = appState.getString("page"),
                anchorMillis = timeline.getLong("anchorMillis"),
                hours = timeline.getDouble("hours")
            )
        }
        return requireNotNull(snapshot)
    }

    @Test
    fun returningFromMemoPreservesTimelineAnchorAndZoom() {
        val testStorage = compose.activity.applicationContext
        val namespace = "pc-issue-153-" + UUID.randomUUID().toString()
        val databaseName = "$namespace.db"
        val isolatedContext = IsolatedAppContext(compose.activity, testStorage, namespace)
        val store = PowerStore(isolatedContext, databaseName)
        isolatedContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()

        try {
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides isolatedContext) {
                        PowerCalTheme { PowerCalApp(store) }
                    }
                }
            }
            compose.waitForIdle()

            val initial = cameraSnapshot()
            assertEquals("TIMELINE", initial.page)
            assertEquals(24.0, initial.hours, 0.0)

            compose.onNodeWithContentDescription("放大，显示更短时间")
                .assertExists().performClick()
            compose.waitForIdle()

            compose.onNodeWithTag("timeline-viewport").assertExists()
                .performTouchInput { swipeUp() }
            compose.waitForIdle()

            val beforeNavigation = cameraSnapshot()
            assertEquals("TIMELINE", beforeNavigation.page)
            assertEquals(16.0, beforeNavigation.hours, 0.0)
            assertNotEquals("pan action should move the camera anchor", initial.anchorMillis,
                beforeNavigation.anchorMillis)

            compose.onNodeWithContentDescription("Memo 未完成 0")
                .assertExists().performClick()
            compose.onNodeWithText("Memo · 未完成").assertExists()
            compose.waitForIdle()
            assertEquals("MEMO", cameraSnapshot().page)

            compose.onNodeWithContentDescription("返回").assertExists().performClick()
            compose.onNodeWithContentDescription(
                "中央时间轴，左侧闹钟，右侧活动和截止"
            ).assertExists()
            compose.waitForIdle()

            val afterReturn = cameraSnapshot()
            println(
                "TIMELINE_RETURN_CAMERA initial=$initial before=$beforeNavigation after=$afterReturn"
            )
            assertEquals("TIMELINE", afterReturn.page)
            assertEquals("returning from Memo must preserve the panned anchor",
                beforeNavigation.anchorMillis, afterReturn.anchorMillis)
            assertEquals("returning from Memo must preserve zoom",
                beforeNavigation.hours, afterReturn.hours, 0.0)
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            store.close()
            testStorage.deleteDatabase("$namespace-$databaseName")
            listOf("powercal-onboarding", "powercal-ui", "powercal-skin-choice", "powercal-profile")
                .forEach { testStorage.deleteSharedPreferences("$namespace-$it") }
            File(testStorage.cacheDir, namespace).deleteRecursively()
        }
    }
}
