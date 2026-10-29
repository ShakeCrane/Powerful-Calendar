package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.data.LegacyImporter
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyHostMigrationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private lateinit var backing: android.app.Application
    private lateinit var namespace: String
    private lateinit var context: IsolatedContext
    private lateinit var store: PowerStore
    private lateinit var legacyPath: File

    @Before
    fun setUp() {
        backing = ApplicationProvider.getApplicationContext()
        namespace = "legacy-${UUID.randomUUID().toString().take(10)}"
        context = IsolatedContext(ui.activity, backing, namespace)
        store = PowerStore(context, "events.db")
        legacyPath = context.getDatabasePath(LegacyImporter.LEGACY_DATABASE)
    }

    @Test
    fun migrationFlowIsReadOnlyAndIdempotent() {
        store.readableDatabase
        createLegacyDatabase()
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
        PowerCalSkinRegistry.reset()

        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            WebSkinBridgeRuntime.owner != null
        }

        val options = WebSkinBridgeRuntime.state().getJSONArray("optionGroups")
            .getJSONObject(0).getJSONArray("items")
        assertTrue((0 until options.length()).any { index ->
            options.getJSONObject(index).getString("id") == "data.legacy.migrate"
        })
        val originalHash = legacySha256()
        assertEquals(0 to 0, migrationBatchCounts())

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.legacy.migrate"))
        }
        ui.waitForIdle()
        ui.onNodeWithText("迁移旧版事件？").assertExists()
        ui.onNodeWithText("取消").performClick()
        ui.waitForIdle()
        ui.onNodeWithText("迁移旧版事件？").assertDoesNotExist()
        assertEquals("cancel must not create a migration batch", 0 to 0, migrationBatchCounts())
        assertTrue("cancel must not create identities", store.all().isEmpty())
        assertEquals(0L, store.identityCount())
        assertEquals(1L, store.nextSequencePreview())
        assertEquals(0, store.pendingCount())
        assertEquals(0, commitCount())
        assertEquals(originalHash, legacySha256())

        confirmAndStartMigration()
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            store.all().size == 1 && migrationBatchCounts() == (1 to 0)
        }
        val imported = store.all().single()
        assertEquals("旧事件 1", imported.title)
        assertEquals(LegacyImporter.SOURCE, imported.source)
        assertEquals(imported.eventSeq, store.sourceLink(LegacyImporter.SOURCE, "v01:1"))
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(0, store.pendingCount())
        val importedSnapshot = store.all()
        val committedImportCount = commitCount()

        confirmAndStartMigration()
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            migrationBatchCounts() == (2 to 0)
        }
        assertEquals("repeat must preserve the complete formal record snapshot", importedSnapshot, store.all())
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(committedImportCount, commitCount())
        assertEquals(0, store.pendingCount())
        assertEquals(originalHash, legacySha256())
    }

    private fun confirmAndStartMigration() {
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.legacy.migrate"))
        }
        ui.waitForIdle()
        ui.onNodeWithText("迁移旧版事件？").assertExists()
        ui.onNodeWithText("开始迁移").performClick()
    }

    private fun createLegacyDatabase() {
        legacyPath.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(legacyPath, null)
        try {
            db.execSQL(
                """CREATE TABLE events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    start_ms INTEGER NOT NULL,
                    end_ms INTEGER NOT NULL,
                    notes TEXT NOT NULL DEFAULT '',
                    location TEXT NOT NULL DEFAULT '',
                    priority TEXT NOT NULL DEFAULT 'NORMAL',
                    reminder_minutes INTEGER NOT NULL DEFAULT 10,
                    all_day INTEGER NOT NULL DEFAULT 0,
                    source TEXT NOT NULL DEFAULT 'local',
                    external_id TEXT
                )"""
            )
            db.execSQL(
                "INSERT INTO events(id,title,start_ms,end_ms) VALUES(1,?,?,?)",
                arrayOf<Any>("旧事件 1", 1_790_000_000_000L, 1_790_000_600_000L)
            )
        } finally {
            db.close()
        }
    }

    private fun migrationBatchCounts(): Pair<Int, Int> {
        val path = context.getDatabasePath(store.databaseName)
        val db = SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(CASE WHEN finished_ms IS NULL THEN 1 ELSE 0 END), 0) " +
                    "FROM migrations WHERE source=?",
                arrayOf(LegacyImporter.SOURCE)
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0) to cursor.getInt(1)
            }
        } finally {
            db.close()
        }
    }

    private fun commitCount(): Int {
        val path = context.getDatabasePath(store.databaseName)
        val db = SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery("SELECT COUNT(*) FROM commits", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
        } finally {
            db.close()
        }
    }

    private fun legacySha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(legacyPath.readBytes())
        .joinToString("") { "%02x".format(it) }

    @After
    fun tearDown() {
        ui.activity.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
        val appJob = PowerCalStores.appScope.coroutineContext[kotlinx.coroutines.Job]
        ui.waitUntil(15_000) {
            shadowOf(Looper.getMainLooper()).idle()
            appJob?.children?.none() ?: true
        }
        store.close()
        context.deleteDatabase(store.databaseName)
        context.deleteDatabase(LegacyImporter.LEGACY_DATABASE)
        context.deleteSharedPreferences("powercal-onboarding")
        context.cleanupRoot()
        PowerCalSkinRegistry.reset()
    }

    private class IsolatedContext(
        target: Context,
        private val backing: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(backing.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }

        private fun databaseAlias(name: String) = "$namespace-$name"
        private fun databasePath(name: String): File = backing.getDatabasePath(databaseAlias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            backing.getSharedPreferences("$namespace-$name", mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = databasePath(name)
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(databaseAlias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(databaseAlias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean =
            backing.deleteDatabase(databaseAlias(name))
        override fun deleteSharedPreferences(name: String): Boolean =
            backing.deleteSharedPreferences("$namespace-$name")
        fun cleanupRoot() { root.deleteRecursively() }
    }
}
