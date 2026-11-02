package dev.powerfulcalendar.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.api.v1.EventFeatureChangeTypeV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
import dev.powerfulcalendar.core.api.v1.StructuredCalendarEventV1
import dev.powerfulcalendar.core.api.v1.StructuredImportStatusV1
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
import java.security.MessageDigest
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
    private lateinit var core: PowerCalCoreFeatureApiV1
    private lateinit var legacyPath: java.io.File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "test-" + UUID.randomUUID() + ".db")
        core = PowerCalCoreFeatureApiV1(store, importCheckpointContext = context)
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

    private fun createLegacyDatabase(
        rows: Int,
        reminderMinutes: Int = 10,
        source: String = "local",
        externalIdPrefix: String? = null,
        priority: String = "HIGH"
    ) {
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
                    priority, reminderMinutes, 0, source,
                    externalIdPrefix?.let { "$it:$index" }
                )
            )
        }
        db.close()
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

    private fun legacySha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(legacyPath.readBytes())
        .joinToString("") { "%02x".format(it) }

    @Test
    fun migrationImportsOnceAndLeavesTheOldFileIntact() {
        createLegacyDatabase(rows = 2)
        val before = legacyPath.length()
        val changes = mutableListOf<EventFeatureChangeTypeV1>()
        val subscription = core.events.subscribe { changes += it.type }
        try {
            val first = LegacyImporter.migrate(context, core.importExport)
            assertTrue(first.available)
            assertEquals(2, first.imported)
            assertEquals(0, first.skipped)
            assertEquals(2, store.all().size)
            assertTrue(store.all().all { it.kind == TemporalKind.ACTIVITY })
            assertTrue(store.all().all { it.source == LegacyImporter.SOURCE })
            assertEquals(2, store.identityCount())
            assertEquals(3L, store.nextSequencePreview())
            assertEquals(
                listOf(
                    EventFeatureChangeTypeV1.FORMAL_CREATED,
                    EventFeatureChangeTypeV1.FORMAL_CREATED
                ),
                changes
            )
            assertEquals(2, legacyRowCount())
            assertEquals(before, legacyPath.length())

            // Re-running is idempotent: no duplicates or duplicate change notifications.
            val second = LegacyImporter.migrate(context, core.importExport)
            assertEquals(0, second.imported)
            assertEquals(2, second.skipped)
            assertEquals(2, store.all().size)
            assertEquals(2, store.identityCount())
            assertEquals(3L, store.nextSequencePreview())
            assertEquals(2, changes.size)
            assertEquals(2, legacyRowCount())
            assertEquals(before, legacyPath.length())
        } finally {
            subscription.close()
        }
    }

    @Test
    fun missingLegacyDatabaseIsReportedWithoutCreatingOne() {
        val result = LegacyImporter.migrate(context, core.importExport)
        assertFalse(result.available)
        assertFalse(legacyPath.exists())
        assertEquals(0, store.all().size)
    }

    @Test
    fun missingEventsTableClosesMigrationBatchAndIsSafeToRetry() {
        createLegacyDatabaseWithoutEvents()
        val originalHash = legacySha256()

        val first = LegacyImporter.migrate(context, core.importExport)
        assertTrue(first.available)
        assertEquals(listOf("旧版数据库没有 events 表"), first.warnings)
        assertEquals(1 to 0, migrationCounts())
        assertEquals(listOf("旧版数据库没有 events 表"), store.migrationSummary(first.batchId)?.third)
        assertTrue(store.all().isEmpty())
        assertEquals(0L, store.identityCount())
        assertEquals(1L, store.nextSequencePreview())

        val second = LegacyImporter.migrate(context, core.importExport)
        assertTrue(second.available)
        assertEquals(listOf("旧版数据库没有 events 表"), second.warnings)
        assertEquals(2 to 0, migrationCounts())
        assertTrue(store.all().isEmpty())
        assertEquals(0L, store.identityCount())
        assertEquals(1L, store.nextSequencePreview())
        assertEquals(originalHash, legacySha256())
    }

    @Test
    fun legacyRowsKeepTheirOwnMetadata() {
        createLegacyDatabase(rows = 1, reminderMinutes = -1)
        val result = LegacyImporter.migrate(context, core.importExport)
        val record = store.all().single()
        assertEquals("旧事件 0", record.title)
        assertEquals("旧备注", record.notes)
        assertEquals("老地点", record.location)
        assertNotNull(record.startMillis)
        assertNotNull(record.endMillis)
        assertTrue(record.endMillis!! > record.startMillis!!)
        assertTrue(record.enabled)
        assertEquals(-1, record.reminderMinutes)
        assertNotNull(store.sourceLink(LegacyImporter.SOURCE, "v01:1"))
        assertTrue(result.warnings.any {
            it.contains("priority 未保留") && it.contains("HIGH×1")
        })
    }

    @Test
    fun legacyAndroidCalendarIdentityIsLinkedToTheMigratedEvent() {
        createLegacyDatabase(
            rows = 1,
            source = "android-calendar",
            externalIdPrefix = "provider-event",
            priority = "NORMAL"
        )

        val migrated = LegacyImporter.migrate(context, core.importExport)
        val record = store.all().single()
        assertEquals(1, migrated.imported)
        assertEquals(record.eventSeq, store.sourceLink(LegacyImporter.SOURCE, "v01:1"))
        assertEquals(record.eventSeq, store.sourceLink("android-calendar", "provider-event:0"))
        assertTrue(migrated.warnings.any {
            it.contains("priority 未保留") && it.contains("NORMAL×1")
        })

        val retryBatch = core.importExport.beginStructuredImport("android-calendar")
        val duplicate = core.importExport.importStructuredEvent(
            batch = retryBatch,
            source = "android-calendar",
            externalId = "provider-event:0",
            event = StructuredCalendarEventV1(
                title = "系统日历重复项",
                startMillis = 1_790_000_000_000L,
                endMillis = 1_790_000_060_000L,
                source = "android-calendar",
                zoneId = "UTC"
            )
        )
        core.importExport.finishStructuredImport(retryBatch)
        assertEquals(StructuredImportStatusV1.ALREADY_IMPORTED, duplicate.status)
        assertEquals(record.eventSeq, duplicate.eventSeq)
        assertEquals(1, store.all().size)
    }

    @Test
    fun legacyProviderIdentityJoinsAnAlreadyImportedCalendarEvent() {
        createLegacyDatabase(
            rows = 1,
            source = "android-calendar",
            externalIdPrefix = "provider-event",
            priority = "NORMAL"
        )
        val calendarBatch = core.importExport.beginStructuredImport("android-calendar")
        val existing = core.importExport.importStructuredEvent(
            batch = calendarBatch,
            source = "android-calendar",
            externalId = "provider-event:0",
            event = StructuredCalendarEventV1(
                title = "已存在的系统日历事件",
                startMillis = 1_790_000_000_000L,
                endMillis = 1_790_003_600_000L,
                source = "android-calendar",
                zoneId = "UTC"
            )
        )
        core.importExport.finishStructuredImport(calendarBatch)
        val existingSeq = requireNotNull(existing.eventSeq)

        val migrated = LegacyImporter.migrate(context, core.importExport)
        assertEquals(0, migrated.imported)
        assertEquals(1, migrated.skipped)
        assertEquals(1, store.all().size)
        assertEquals("已存在的系统日历事件", store.get(existingSeq)?.title)
        assertEquals(existingSeq, store.sourceLink(LegacyImporter.SOURCE, "v01:1"))
        assertEquals(existingSeq, store.sourceLink("android-calendar", "provider-event:0"))
    }
}
