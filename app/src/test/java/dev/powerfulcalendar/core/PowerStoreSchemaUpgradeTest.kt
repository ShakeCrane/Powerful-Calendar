package dev.powerfulcalendar.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Real on-disk PowerStore schema-v1 fixture based on the DDL at 46bf087. It contains committed and
 * pending data, a tombstone, sequence/order high-water marks, source/migration evidence, schedules
 * and execution history. Opening it with the current store must upgrade it in place and preserve
 * all user data; this is not a create-empty-db test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PowerStoreSchemaUpgradeTest {
    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var store: PowerStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("legacy-v1-" + UUID.randomUUID() + ".db")
        dbFile.parentFile?.mkdirs()
        context.deleteDatabase(dbFile.name)
        createFixture(dbFile, version = 1, withReviewedTable = false)
        store = PowerStore(context, dbFile.name)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(dbFile.name)
    }

    private fun createFixture(file: File, version: Int, withReviewedTable: Boolean, withLineageColumn: Boolean = false) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("""CREATE TABLE meta(id INTEGER PRIMARY KEY CHECK(id=1), next_seq INTEGER NOT NULL, next_order INTEGER NOT NULL, ai_epoch INTEGER NOT NULL DEFAULT 0, ai_locked INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("INSERT INTO meta(id,next_seq,next_order,ai_epoch,ai_locked) VALUES(1,43,3,9,0)")
        db.execSQL("""CREATE TABLE identities(event_seq INTEGER PRIMARY KEY, allocated_ms INTEGER NOT NULL, retired_ms INTEGER, origin TEXT NOT NULL DEFAULT 'local')""")
        db.execSQL("""CREATE TABLE objects(event_seq INTEGER PRIMARY KEY, revision INTEGER NOT NULL, kind TEXT NOT NULL, anchor_ms INTEGER, span_end_ms INTEGER, payload TEXT NOT NULL, retired INTEGER NOT NULL DEFAULT 0, created_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX objects_anchor ON objects(retired,anchor_ms)")
        db.execSQL("""CREATE TABLE drafts(draft_id INTEGER PRIMARY KEY AUTOINCREMENT, event_seq INTEGER NOT NULL, queue_order INTEGER NOT NULL UNIQUE, base_rev INTEGER, op TEXT NOT NULL, payload TEXT NOT NULL, status TEXT NOT NULL, draft_version INTEGER NOT NULL DEFAULT 0, created_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX drafts_queue ON drafts(status,queue_order)")
        db.execSQL("""CREATE TABLE commits(commit_id TEXT PRIMARY KEY, draft_id INTEGER UNIQUE, event_seq INTEGER NOT NULL, previous_revision INTEGER, new_revision INTEGER NOT NULL, actor TEXT NOT NULL, operation TEXT NOT NULL, created_ms INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX commits_event ON commits(event_seq)")
        db.execSQL("""CREATE TABLE ai_sessions(session_id TEXT PRIMARY KEY, lock_epoch INTEGER NOT NULL, scope TEXT NOT NULL, allowed_ops TEXT NOT NULL, state TEXT NOT NULL, owner_token TEXT NOT NULL, started_ms INTEGER NOT NULL, updated_ms INTEGER NOT NULL, report TEXT)""")
        db.execSQL("""CREATE TABLE source_links(source TEXT NOT NULL, external_id TEXT NOT NULL, event_seq INTEGER NOT NULL, PRIMARY KEY(source,external_id))""")
        db.execSQL("""CREATE TABLE migrations(batch_id TEXT PRIMARY KEY, source TEXT NOT NULL, started_ms INTEGER NOT NULL, finished_ms INTEGER, imported INTEGER NOT NULL DEFAULT 0, skipped INTEGER NOT NULL DEFAULT 0, warnings TEXT NOT NULL DEFAULT '[]')""")
        db.execSQL("""CREATE TABLE schedule_intents(event_seq INTEGER NOT NULL, trigger_id TEXT NOT NULL, revision INTEGER NOT NULL, occurrence_key TEXT NOT NULL, instant_ms INTEGER NOT NULL, requirement TEXT NOT NULL, desired_state TEXT NOT NULL, android_hint TEXT, updated_ms INTEGER NOT NULL, PRIMARY KEY(event_seq,trigger_id))""")
        db.execSQL("CREATE INDEX intents_due ON schedule_intents(desired_state,instant_ms)")
        db.execSQL("""CREATE TABLE executions(execution_id TEXT PRIMARY KEY, event_seq INTEGER NOT NULL, revision INTEGER NOT NULL, occurrence_key TEXT NOT NULL, trigger_id TEXT NOT NULL, result TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '', at_ms INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX executions_event ON executions(event_seq,at_ms)")

        val now = System.currentTimeMillis()
        val start = now + 3_600_000L
        val zone = ZoneId.systemDefault().id
        val activePayload = JSONObject().apply {
            put("eventSeq", 41L); put("revision", 7L); put("kind", "ACTIVITY"); put("title", "v1 已提交")
            put("notes", "原库备注"); put("location", "旧地点"); put("startMs", start); put("endMs", start + 3_600_000L)
            put("recurrence", JSONObject().put("freq", "NONE").put("interval", 1))
            put("reminder", -1); put("enabled", true); put("source", "local"); put("zone", zone)
            put("createdMs", now - 10_000); put("updatedMs", now - 5_000)
        }.toString()
        val retiredPayload = JSONObject().apply {
            put("eventSeq", 42L); put("revision", 3L); put("kind", "ALARM"); put("title", "v1 墓碑")
            put("notes", ""); put("location", ""); put("startMs", start + 86_400_000L)
            put("recurrence", JSONObject().put("freq", "NONE").put("interval", 1))
            put("reminder", -1); put("enabled", true); put("source", "local"); put("zone", zone)
            put("createdMs", now - 20_000); put("updatedMs", now - 10_000)
        }.toString()
        db.execSQL("INSERT INTO identities(event_seq,allocated_ms,origin) VALUES(41,?,'local')", arrayOf(now - 10_000))
        db.execSQL("INSERT INTO identities(event_seq,allocated_ms,retired_ms,origin) VALUES(42,?,?, 'local')", arrayOf(now - 20_000, now - 10_000))
        db.execSQL("INSERT INTO objects(event_seq,revision,kind,anchor_ms,span_end_ms,payload,retired,created_ms,updated_ms) VALUES(41,7,'ACTIVITY',?,?,?,?,?,?)",
            arrayOf(start, start + 3_600_000L, activePayload, 0, now - 10_000, now - 5_000))
        db.execSQL("INSERT INTO objects(event_seq,revision,kind,anchor_ms,span_end_ms,payload,retired,created_ms,updated_ms) VALUES(42,3,'ALARM',?,?,?,?,?,?)",
            arrayOf(start + 86_400_000L, start + 86_400_000L, retiredPayload, 1, now - 20_000, now - 10_000))

        val form = JSONObject().apply {
            put("kind", "ACTIVITY"); put("title", "v1 待处理最后一字 好")
            put("start", DraftForm.formatInstant(start, ZoneId.of(zone)))
            put("end", DraftForm.formatInstant(start + 3_600_000L, ZoneId.of(zone)))
            put("notes", "尚未提交"); put("location", ""); put("reminder", -1); put("enabled", true)
            put("source", "local"); put("zone", zone)
            put("recurrence", JSONObject().put("freq", "NONE").put("interval", 1))
        }.toString()
        db.execSQL("INSERT INTO drafts(draft_id,event_seq,queue_order,base_rev,op,payload,status,draft_version,created_ms,updated_ms) VALUES(18,41,1,6,'REPLACE',?,'COMMITTED',2,?,?)",
            arrayOf(JSONObject(form).put("title", "v1 已提交").toString(), now - 6_000, now - 5_000))
        db.execSQL("INSERT INTO drafts(draft_id,event_seq,queue_order,base_rev,op,payload,status,draft_version,created_ms,updated_ms) VALUES(19,41,2,7,'REPLACE',?,'PENDING',4,?,?)",
            arrayOf(form, now - 2_000, now - 1_000))
        db.execSQL("INSERT INTO commits(commit_id,draft_id,event_seq,previous_revision,new_revision,actor,operation,created_ms) VALUES('commit-v1',18,41,6,7,'user','REPLACE',?)", arrayOf(now - 5_000))
        db.execSQL("INSERT INTO source_links(source,external_id,event_seq) VALUES('legacy-v01','old:41',41)")
        db.execSQL("INSERT INTO migrations(batch_id,source,started_ms,finished_ms,imported,skipped,warnings) VALUES('m-v1','legacy-v01',?,?,1,0,'[]')", arrayOf(now - 10_000, now - 9_000))
        db.execSQL("INSERT INTO schedule_intents(event_seq,trigger_id,revision,occurrence_key,instant_ms,requirement,desired_state,updated_ms) VALUES(41,'primary',7,'slot-v1',?,'INEXACT','SCHEDULED',?)", arrayOf(start - 600_000, now - 1_000))
        db.execSQL("INSERT INTO executions(execution_id,event_seq,revision,occurrence_key,trigger_id,result,at_ms) VALUES('exec-v1',41,7,'slot-v1','primary','FIRED',?)", arrayOf(now - 500))
        if (withReviewedTable) {
            db.execSQL(
                """CREATE TABLE ai_session_drafts(
                     session_id TEXT NOT NULL, draft_id INTEGER NOT NULL,
                     draft_version INTEGER NOT NULL, operation TEXT NOT NULL,
                     PRIMARY KEY(session_id, draft_id))"""
            )
        }
        if (withLineageColumn) {
            // Simulates a crash after ALTER TABLE but before SQLiteOpenHelper bumped user_version:
            // the retry must be idempotent rather than fail on a duplicate column.
            db.execSQL("ALTER TABLE meta ADD COLUMN lineage_id TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                "UPDATE meta SET lineage_id=? WHERE id=1",
                arrayOf(UUID.randomUUID().toString())
            )
        }
        db.version = version
        db.close()
    }

    @Test
    fun upgradingRealV1DataInPlacePreservesRowsAndAddsOnlyTheReviewedTable() {
        // First open triggers onUpgrade(1 -> 2). Nothing is copied to an empty replacement DB.
        val records = store.all()
        assertEquals(1, records.size)
        assertEquals("v1 已提交", records.single().title)
        assertEquals(7L, records.single().revision)
        assertEquals("原库备注", records.single().notes)
        assertEquals(1, store.pendingCount())
        val draft = store.pendingDrafts().single()
        assertEquals(19L, draft.draftId)
        assertEquals(2L, draft.queueOrder)
        assertEquals(4L, draft.draftVersion)
        assertEquals("v1 待处理最后一字 好", draft.form.title)
        assertTrue(store.isRetired(42L))
        assertEquals("v1 墓碑", store.getIncludingRetired(42L)!!.title)
        assertEquals(2L, store.identityCount())
        assertEquals(43L, store.nextSequencePreview())
        assertEquals(41L, store.sourceLink("legacy-v01", "old:41"))
        assertEquals(1, store.history(41L).size)
        assertEquals("FIRED", store.lastExecution(41L))
        assertEquals(1, store.migrationSummary("m-v1")!!.first)
        assertNotNull(store.intentFor(41L))

        // A follow-up write proves high-water/order metadata still continues, not resets.
        val next = store.newDraft(DraftForm.fresh(TemporalKind.ALARM, LocalDate.now(), ZoneId.systemDefault()))
        assertEquals(43L, next.eventSeq)
        assertTrue(next.queueOrder > draft.queueOrder)

        // The chain must end at the current schema, with a durable lineage and a working new table.
        assertEquals(PowerStore.VERSION, store.readableDatabase.version)
        assertNotNull(UUID.fromString(store.exportBackup().getString("storeLineageId")))
        val session = store.beginAiBatch(
            mapOf(draft.draftId to draft.draftVersion),
            setOf(DraftOperation.REPLACE)
        )
        assertTrue(store.finishAiBatch(session.sessionId))
    }

    @Test
    fun retryingAHalfAppliedLineageMigrationSucceedsWithoutTouchingRows() {
        val file = context.getDatabasePath("half-v2-" + UUID.randomUUID() + ".db")
        createFixture(file, version = 2, withReviewedTable = true, withLineageColumn = true)
        val half = PowerStore(context, file.name)
        try {
            assertEquals("v1 已提交", half.get(41L)!!.title)
            assertEquals(1, half.pendingCount())
            assertEquals(PowerStore.VERSION, half.readableDatabase.version)
            assertNotNull(UUID.fromString(half.exportBackup().getString("storeLineageId")))
        } finally {
            val name = half.databaseName
            half.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun failedV4MigrationRollsBackAndCanRetryWithoutLosingLegacyRows() {
        val file = context.getDatabasePath("failed-v4-" + UUID.randomUUID() + ".db")
        createFixture(file, version = 3, withReviewedTable = true, withLineageColumn = true)

        val fixture = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            fixture.execSQL(
                """INSERT INTO drafts(draft_id,event_seq,queue_order,base_rev,op,payload,status,
                   draft_version,created_ms,updated_ms)
                   SELECT 20,event_seq,3,base_rev,op,payload,'PENDING',5,created_ms,updated_ms+1
                   FROM drafts WHERE draft_id=19"""
            )
            fixture.execSQL(
                """CREATE TRIGGER fail_v4_draft_update BEFORE UPDATE OF status ON drafts
                   WHEN NEW.status='DISCARDED'
                   BEGIN SELECT RAISE(ABORT,'injected v4 migration failure'); END"""
            )
        } finally {
            fixture.close()
        }

        val failedOpen = PowerStore(context, file.name)
        try {
            val failure = runCatching { failedOpen.readableDatabase }.exceptionOrNull()
            assertNotNull("the injected failure must interrupt the migration", failure)
        } finally {
            failedOpen.close()
        }

        val afterFailure = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            assertEquals("SQLite must retain the pre-migration version", 3, afterFailure.version)
            afterFailure.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='draft_migration_audit'",
                null
            ).use { assertFalse("DDL from the failed migration must roll back", it.moveToFirst()) }
            afterFailure.rawQuery(
                "SELECT COUNT(*) FROM drafts WHERE status='PENDING' AND event_seq=41", null
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("both legacy drafts must survive the failed migration", 2, it.getInt(0))
            }
            afterFailure.execSQL("DROP TRIGGER fail_v4_draft_update")
        } finally {
            afterFailure.close()
        }

        val retry = PowerStore(context, file.name)
        try {
            assertEquals(PowerStore.VERSION, retry.readableDatabase.version)
            assertEquals("v1 已提交", retry.get(41L)!!.title)
            assertEquals("v1 墓碑", retry.getIncludingRetired(42L)!!.title)
            assertEquals(1, retry.pendingCount())
            assertEquals(43L, retry.nextSequencePreview())
            retry.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM draft_migration_audit WHERE reason='v4-duplicate-active-draft'",
                null
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("retry must record exactly the duplicate it retired", 1, it.getInt(0))
            }
        } finally {
            val name = retry.databaseName
            retry.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun upgradingRealV2AddsLineageInPlaceWithoutTouchingBusinessRows() {
        val file = context.getDatabasePath("legacy-v2-" + UUID.randomUUID() + ".db")
        createFixture(file, version = 2, withReviewedTable = true)
        val v2 = PowerStore(context, file.name)
        try {
            assertEquals(1, v2.all().size)
            assertEquals("v1 已提交", v2.get(41L)!!.title)
            assertEquals(1, v2.pendingCount())
            assertEquals(1, v2.history(41L).size)
            assertEquals(PowerStore.VERSION, v2.readableDatabase.version)
            assertNotNull(UUID.fromString(v2.exportBackup().getString("storeLineageId")))
        } finally {
            val name = v2.databaseName
            v2.close()
            context.deleteDatabase(name)
        }
    }
}
