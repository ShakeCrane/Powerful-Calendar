package dev.powerfulcalendar.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * The v0.1 database must survive migration untouched. This is the concrete "原有数据库不会被破坏"
 * check: the old file's rows are byte-for-byte still there and the migration is re-runnable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyMigrationTest {

    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var legacyPath: java.io.File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "test-" + UUID.randomUUID() + ".db")
        legacyPath = context.getDatabasePath(LegacyImporter.LEGACY_DATABASE)
        legacyPath.parentFile?.mkdirs()
        context.deleteDatabase(LegacyImporter.LEGACY_DATABASE)
    }

    @After
    fun tearDown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
        context.deleteDatabase(LegacyImporter.LEGACY_DATABASE)
    }

    private fun createLegacyDatabase(rows: Int) {
        val db = SQLiteDatabase.openOrCreateDatabase(legacyPath, null)
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
        repeat(rows) { index ->
            val start = 1_790_000_000_000L + index * 86_400_000L
            db.execSQL(
                """INSERT INTO events(title,start_ms,end_ms,notes,location,priority,
                   reminder_minutes,all_day,source,external_id)
                   VALUES(?,?,?,?,?,?,?,?,?,?)""",
                arrayOf<Any?>(
                    "旧事件 $index", start, start + 3_600_000L, "旧备注", "老地点",
                    "HIGH", 10, 0, "local", null
                )
            )
        }
        db.close()
    }

    private fun createLegacyDatabaseWithoutEvents() {
        val db = SQLiteDatabase.openOrCreateDatabase(legacyPath, null)
        db.execSQL("CREATE TABLE unrelated_metadata (value TEXT NOT NULL)")
        db.close()
    }

    private fun migrationCounts(): Pair<Int, Int> {
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

    private fun legacyRowCount(): Int {
        val db = SQLiteDatabase.openDatabase(legacyPath.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery("SELECT COUNT(*) FROM events", null).use {
                it.moveToFirst()
                it.getInt(0)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationImportsOnceAndLeavesTheOldFileIntact() {
        createLegacyDatabase(rows = 2)
        val before = legacyPath.length()

        val first = LegacyImporter.migrate(context, store)
        assertTrue(first.available)
        assertEquals(2, first.imported)
        assertEquals(0, first.skipped)
        assertEquals(2, store.all().size)
        assertTrue(store.all().all { it.kind == TemporalKind.ACTIVITY })
        assertTrue(store.all().all { it.source == LegacyImporter.SOURCE })
        assertEquals(2, legacyRowCount())
        assertEquals(before, legacyPath.length())

        // Re-running is idempotent: no duplicates, everything counted as skipped.
        val second = LegacyImporter.migrate(context, store)
        assertEquals(0, second.imported)
        assertEquals(2, second.skipped)
        assertEquals(2, store.all().size)
        assertEquals(2, legacyRowCount())
    }

    @Test
    fun missingLegacyDatabaseIsReportedWithoutCreatingOne() {
        val result = LegacyImporter.migrate(context, store)
        assertFalse(result.available)
        assertFalse(legacyPath.exists())
        assertEquals(0, store.all().size)
    }

    @Test
    fun missingEventsTableClosesMigrationBatchAndIsSafeToRetry() {
        createLegacyDatabaseWithoutEvents()
        val originalSize = legacyPath.length()

        val first = LegacyImporter.migrate(context, store)
        assertTrue(first.available)
        assertEquals(listOf("旧版数据库没有 events 表"), first.warnings)
        assertEquals(1 to 0, migrationCounts())
        assertEquals(listOf("旧版数据库没有 events 表"), store.migrationSummary(first.batchId)?.third)
        assertEquals(0, store.all().size)

        val second = LegacyImporter.migrate(context, store)
        assertTrue(second.available)
        assertEquals(listOf("旧版数据库没有 events 表"), second.warnings)
        assertEquals(2 to 0, migrationCounts())
        assertEquals(0, store.all().size)
        assertEquals(originalSize, legacyPath.length())
    }

    @Test
    fun legacyRowsKeepTheirOwnMetadata() {
        createLegacyDatabase(rows = 1)
        LegacyImporter.migrate(context, store)
        val record = store.all().single()
        assertEquals("旧事件 0", record.title)
        assertEquals("旧备注", record.notes)
        assertEquals("老地点", record.location)
        assertNotNull(record.startMillis)
        assertNotNull(record.endMillis)
        assertTrue(record.endMillis!! > record.startMillis!!)
        assertNotNull(store.sourceLink(LegacyImporter.SOURCE, "v01:1"))
    }
}
