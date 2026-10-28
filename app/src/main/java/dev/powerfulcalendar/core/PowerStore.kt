package dev.powerfulcalendar.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * PowerCal authoritative store (protocol v0.3).
 *
 * Deliberately a **separate database** from the v0.1 prototype (`powerful-calendar.db`), so a
 * botched upgrade can never destroy historical data. The old file is only ever read, by
 * [LegacyImporter], after an explicit user action.
 *
 * Invariants enforced here:
 *  - eventSeq is allocated once per new event, retired forever on delete, never reused (I-01).
 *  - draftId/queueOrder are independent of eventSeq; one current actionable card per event (schema v4).
 *  - exactly one current snapshot per eventSeq, whole-object replacement only (I-03/I-04).
 *  - a stale card cannot commit without explicit human re-approval; it is never merged (I-18).
 *  - no unauthorized write may touch a formal object while the single AI batch holds the deck
 *    lock; every AI write is fenced by session + lock epoch + scope (I-06/I-07).
 *  - commit records are idempotent; re-clicking checkmark cannot double-publish (I-15).
 */
class PowerStore(
    context: Context,
    databaseName: String = DATABASE_NAME,
    private val processIdentity: String = PROCESS_TOKEN
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, VERSION) {
    private val appContext = context.applicationContext

    /** Stable typed preflight/transaction failure. Callers must not infer this from locale text. */
    class NonEmptyRestoreTargetException : IllegalArgumentException(
        "目标数据库不是空库；为避免覆盖已有数据，恢复已被拒绝"
    )

    /**
     * Distinguishes this process from an earlier one, so a restart can prove a batch is orphaned.
     * It must be process-wide: a receiver or boot handler opening its own [PowerStore] must not
     * mistake the running UI's AI batch for an orphan and unlock the deck behind its back.
     */
    private val processToken: String get() = processIdentity

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE meta(
                 id INTEGER PRIMARY KEY CHECK(id=1),
                 next_seq INTEGER NOT NULL,
                 next_order INTEGER NOT NULL,
                 ai_epoch INTEGER NOT NULL DEFAULT 0,
                 ai_locked INTEGER NOT NULL DEFAULT 0,
                 lineage_id TEXT NOT NULL
               )"""
        )
        db.execSQL(
            "INSERT INTO meta(id,next_seq,next_order,lineage_id) VALUES(1,1,1,?)",
            arrayOf(UUID.randomUUID().toString())
        )
        db.execSQL(
            """CREATE TABLE identities(
                 event_seq INTEGER PRIMARY KEY,
                 allocated_ms INTEGER NOT NULL,
                 retired_ms INTEGER,
                 origin TEXT NOT NULL DEFAULT 'local'
               )"""
        )
        db.execSQL(
            """CREATE TABLE objects(
                 event_seq INTEGER PRIMARY KEY,
                 revision INTEGER NOT NULL,
                 kind TEXT NOT NULL,
                 anchor_ms INTEGER,
                 span_end_ms INTEGER,
                 payload TEXT NOT NULL,
                 retired INTEGER NOT NULL DEFAULT 0,
                 created_ms INTEGER NOT NULL,
                 updated_ms INTEGER NOT NULL
               )"""
        )
        db.execSQL("CREATE INDEX objects_anchor ON objects(retired, anchor_ms)")
        db.execSQL(
            """CREATE TABLE drafts(
                 draft_id INTEGER PRIMARY KEY AUTOINCREMENT,
                 event_seq INTEGER NOT NULL,
                 queue_order INTEGER NOT NULL UNIQUE,
                 base_rev INTEGER,
                 op TEXT NOT NULL,
                 payload TEXT NOT NULL,
                 status TEXT NOT NULL,
                 draft_version INTEGER NOT NULL DEFAULT 0,
                 created_ms INTEGER NOT NULL,
                 updated_ms INTEGER NOT NULL
               )"""
        )
        db.execSQL("CREATE INDEX drafts_queue ON drafts(status, queue_order)")
        createDraftMigrationAuditTable(db)
        createUniqueActiveDraftIndex(db)
        createHumanEditorTable(db)
        db.execSQL(
            """CREATE TABLE commits(
                 commit_id TEXT PRIMARY KEY,
                 draft_id INTEGER UNIQUE,
                 event_seq INTEGER NOT NULL,
                 previous_revision INTEGER,
                 new_revision INTEGER NOT NULL,
                 actor TEXT NOT NULL,
                 operation TEXT NOT NULL,
                 created_ms INTEGER NOT NULL
               )"""
        )
        db.execSQL("CREATE INDEX commits_event ON commits(event_seq)")
        db.execSQL(
            """CREATE TABLE ai_sessions(
                 session_id TEXT PRIMARY KEY,
                 lock_epoch INTEGER NOT NULL,
                 scope TEXT NOT NULL,
                 allowed_ops TEXT NOT NULL,
                 state TEXT NOT NULL,
                 owner_token TEXT NOT NULL,
                 started_ms INTEGER NOT NULL,
                 updated_ms INTEGER NOT NULL,
                 report TEXT
               )"""
        )
        db.execSQL(
            """CREATE TABLE source_links(
                 source TEXT NOT NULL,
                 external_id TEXT NOT NULL,
                 event_seq INTEGER NOT NULL,
                 PRIMARY KEY(source, external_id)
               )"""
        )
        db.execSQL(
            """CREATE TABLE migrations(
                 batch_id TEXT PRIMARY KEY,
                 source TEXT NOT NULL,
                 started_ms INTEGER NOT NULL,
                 finished_ms INTEGER,
                 imported INTEGER NOT NULL DEFAULT 0,
                 skipped INTEGER NOT NULL DEFAULT 0,
                 warnings TEXT NOT NULL DEFAULT '[]'
               )"""
        )
        db.execSQL(
            """CREATE TABLE schedule_intents(
                 event_seq INTEGER NOT NULL,
                 trigger_id TEXT NOT NULL,
                 revision INTEGER NOT NULL,
                 occurrence_key TEXT NOT NULL,
                 instant_ms INTEGER NOT NULL,
                 requirement TEXT NOT NULL,
                 desired_state TEXT NOT NULL,
                 android_hint TEXT,
                 updated_ms INTEGER NOT NULL,
                 PRIMARY KEY(event_seq, trigger_id)
               )"""
        )
        db.execSQL("CREATE INDEX intents_due ON schedule_intents(desired_state, instant_ms)")
        db.execSQL(
            """CREATE TABLE executions(
                 execution_id TEXT PRIMARY KEY,
                 event_seq INTEGER NOT NULL,
                 revision INTEGER NOT NULL,
                 occurrence_key TEXT NOT NULL,
                 trigger_id TEXT NOT NULL,
                 result TEXT NOT NULL,
                 detail TEXT NOT NULL DEFAULT '',
                 at_ms INTEGER NOT NULL
               )"""
        )
        db.execSQL("CREATE INDEX executions_event ON executions(event_seq, at_ms)")
        createReviewedAuthorizationTable(db)
        createAiTaskReceiptTable(db)
    }

    /**
     * DSH-08: authorizations are about a reviewed snapshot, so the exact `draftVersion` the user
     * confirmed must be durable for the lifetime of the batch. Kept as its own table so the
     * session row stays compatible with earlier builds that only stored id/operation sets.
     */
    private fun createReviewedAuthorizationTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS ai_session_drafts(
                 session_id TEXT NOT NULL,
                 draft_id INTEGER NOT NULL,
                 draft_version INTEGER NOT NULL,
                 operation TEXT NOT NULL,
                 PRIMARY KEY(session_id, draft_id)
               )"""
        )
    }

    /** Durable idempotency for one reviewed AI task revision and candidate index. */
    private fun createAiTaskReceiptTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS ai_task_receipts(
                 task_id TEXT NOT NULL,
                 task_revision INTEGER NOT NULL,
                 item_index INTEGER NOT NULL,
                 fingerprint TEXT NOT NULL,
                 event_seq INTEGER NOT NULL,
                 draft_id INTEGER NOT NULL UNIQUE,
                 state TEXT NOT NULL,
                 idempotency_key TEXT NOT NULL UNIQUE,
                 commit_id TEXT,
                 created_ms INTEGER NOT NULL,
                 updated_ms INTEGER NOT NULL,
                 PRIMARY KEY(task_id, task_revision, item_index)
               )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS ai_task_receipts_event ON ai_task_receipts(event_seq)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Every step is additive and ordered. A real v1/v2 user database upgrades in place; nothing
        // is recreated, and a gap in the known chain fails loudly instead of producing an empty DB.
        require(oldVersion in 1 until newVersion) { "不支持的数据库升级 v$oldVersion → v$newVersion" }
        var version = oldVersion
        if (version < 2 && newVersion >= 2) {
            createReviewedAuthorizationTable(db)
            version = 2
        }
        if (version < 3 && newVersion >= 3) {
            addStoreLineageColumn(db)
            version = 3
        }
        if (version < 4 && newVersion >= 4) {
            createDraftMigrationAuditTable(db)
            consolidateLegacyActiveDrafts(db)
            createUniqueActiveDraftIndex(db)
            createHumanEditorTable(db)
            version = 4
        }
        if (version < 5 && newVersion >= 5) {
            createAiTaskReceiptTable(db)
            version = 5
        }
        require(version == newVersion) {
            "PowerCal 数据库升级路径不完整 (v$oldVersion → v$newVersion)，已拒绝打开"
        }
    }

    /**
     * v2→v3: additive lineage identity carried into backups for provenance; no business row is
     * rewritten. Nothing consumes it for merge decisions yet.
     * Idempotent on purpose: a process killed between ALTER TABLE and the version bump must be able
     * to retry the same upgrade instead of being locked out of its own database.
     */
    private fun addStoreLineageColumn(db: SQLiteDatabase) {
        if (!hasColumn(db, "meta", "lineage_id")) {
            db.execSQL("ALTER TABLE meta ADD COLUMN lineage_id TEXT NOT NULL DEFAULT ''")
        }
        db.execSQL(
            "UPDATE meta SET lineage_id=? WHERE lineage_id IS NULL OR lineage_id=''",
            arrayOf(UUID.randomUUID().toString())
        )
    }

    /** v4 migration retains every old draft, while removing duplicate actionable status. */
    private fun consolidateLegacyActiveDrafts(db: SQLiteDatabase) {
        val losers = mutableListOf<Long>()
        val seen = mutableSetOf<Long>()
        db.rawQuery(
            """SELECT draft_id,event_seq FROM drafts
               WHERE status IN ('PENDING','CONFLICT','INVALID')
               ORDER BY event_seq ASC, updated_ms DESC, draft_version DESC,
                        queue_order DESC, draft_id DESC""", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                if (!seen.add(cursor.getLong(1))) losers.add(id)
            }
        }
        losers.forEach { id ->
            val oldStatus = db.rawQuery("SELECT status FROM drafts WHERE draft_id=?", arrayOf(id.toString()))
                .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "UNKNOWN" }
            db.insertWithOnConflict("draft_migration_audit", null, ContentValues().apply {
                put("draft_id", id); put("old_status", oldStatus)
                put("reason", "v4-duplicate-active-draft"); put("migrated_ms", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_IGNORE)
            // Keep payload, IDs, queue order, versions and original timestamps intact.
            db.execSQL("UPDATE drafts SET status='DISCARDED' WHERE draft_id=?", arrayOf(id))
        }
    }

    private fun createDraftMigrationAuditTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS draft_migration_audit(
                 draft_id INTEGER PRIMARY KEY,
                 old_status TEXT NOT NULL,
                 reason TEXT NOT NULL,
                 migrated_ms INTEGER NOT NULL
               )"""
        )
    }

    private fun createUniqueActiveDraftIndex(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE UNIQUE INDEX IF NOT EXISTS drafts_one_actionable_per_event
               ON drafts(event_seq)
               WHERE status IN ('PENDING','CONFLICT','INVALID')"""
        )
    }

    private fun createHumanEditorTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS human_editor(
                 id INTEGER PRIMARY KEY CHECK(id=1),
                 token TEXT NOT NULL,
                 owner_token TEXT NOT NULL,
                 event_seq INTEGER,
                 opened_ms INTEGER NOT NULL,
                 touched_ms INTEGER NOT NULL
               )"""
        )
    }

    /** A live human editor is the reciprocal half of the global calendar-writer lock. */
    data class HumanEditLease(val token: String, val eventSeq: Long?, val openedAtMillis: Long)

    @Synchronized
    fun beginHumanEdit(eventSeq: Long? = null, authenticatedToken: String? = null): HumanEditLease = tx { db ->
        check(!isLocked(db)) { "AI mutation is active; editing is temporarily read-only" }
        if (eventSeq != null) requireNotNull(readObject(db, eventSeq)) { "Event no longer exists" }
        val existing = db.rawQuery(
            "SELECT token,event_seq,opened_ms,owner_token FROM human_editor WHERE id=1", null
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else Pair(HumanEditLease(
                cursor.getString(0), if (cursor.isNull(1)) null else cursor.getLong(1), cursor.getLong(2)
            ), cursor.getString(3))
        }
        if (existing != null) {
            val (lease, owner) = existing
            check(authenticatedToken != null && authenticatedToken == lease.token &&
                owner == processToken && lease.eventSeq == eventSeq) {
                "A human editor already owns the calendar writer lease"
            }
            return@tx lease
        }
        val now = System.currentTimeMillis()
        val lease = HumanEditLease(UUID.randomUUID().toString(), eventSeq, now)
        db.insertOrThrow("human_editor", null, ContentValues().apply {
            put("id", 1); put("token", lease.token); put("owner_token", processToken)
            if (eventSeq == null) putNull("event_seq") else put("event_seq", eventSeq)
            put("opened_ms", now); put("touched_ms", now)
        })
        lease
    }

    @Synchronized
    fun touchHumanEdit(token: String): Boolean = tx { db ->
        db.compileStatement("UPDATE human_editor SET touched_ms=? WHERE id=1 AND token=? AND owner_token=?").apply {
            bindLong(1, System.currentTimeMillis()); bindString(2, token); bindString(3, processToken)
        }.executeUpdateDelete() == 1
    }

    @Synchronized
    fun endHumanEdit(token: String): Boolean = tx { db ->
        db.delete("human_editor", "id=1 AND token=? AND owner_token=?", arrayOf(token, processToken)) == 1
    }

    @Synchronized
    fun hasHumanEditor(): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM human_editor WHERE id=1", null
    ).use { it.moveToFirst() }

    /** Timeout recovery only; a fresh lease is never displaced by another process. */
    @Synchronized
    fun recoverAbandonedHumanEdit(staleAfterMillis: Long = 5 * 60_000L): Boolean = tx { db ->
        require(staleAfterMillis >= 60_000L)
        db.compileStatement(
            "DELETE FROM human_editor WHERE id=1 AND owner_token<>? AND touched_ms<?"
        ).apply {
            bindString(1, processToken); bindLong(2, System.currentTimeMillis() - staleAfterMillis)
        }.executeUpdateDelete() == 1
    }

    private fun hasHumanEditor(db: SQLiteDatabase): Boolean = db.rawQuery(
        "SELECT 1 FROM human_editor WHERE id=1", null
    ).use { it.moveToFirst() }

    private fun ownsHumanEditor(db: SQLiteDatabase, token: String?): Boolean {
        if (token == null) return false
        return db.rawQuery(
            "SELECT 1 FROM human_editor WHERE id=1 AND token=? AND owner_token=?",
            arrayOf(token, processToken)
        )
            .use { it.moveToFirst() }
    }

    /** Must occur in the same transaction as the formal snapshot publication. */
    private fun finishEventDrafts(db: SQLiteDatabase, seq: Long, committedId: Long, now: Long) {
        db.execSQL(
            """UPDATE drafts SET status='DISCARDED', updated_ms=?
               WHERE event_seq=? AND draft_id<>?
                 AND status IN ('PENDING','CONFLICT','INVALID')""",
            arrayOf(now, seq, committedId)
        )
        db.execSQL("UPDATE drafts SET status='COMMITTED', updated_ms=? WHERE draft_id=?",
            arrayOf(now, committedId))
        db.execSQL(
            """UPDATE ai_task_receipts SET
                 state=CASE (SELECT status FROM drafts WHERE draft_id=ai_task_receipts.draft_id)
                   WHEN 'COMMITTED' THEN 'COMMITTED' WHEN 'DISCARDED' THEN 'DISCARDED' ELSE state END,
                 commit_id=CASE WHEN (SELECT status FROM drafts WHERE draft_id=ai_task_receipts.draft_id)='COMMITTED'
                   THEN (SELECT commit_id FROM commits WHERE draft_id=ai_task_receipts.draft_id)
                   ELSE commit_id END,
                 updated_ms=? WHERE event_seq=? AND state='PENDING'""",
            arrayOf(now, seq)
        )
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) return@use true
            }
            false
        }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
        error("拒绝降级 PowerCal 数据库")

    // ---------------------------------------------------------------- JSON payloads

    private fun encodeForm(form: DraftForm): String = JSONObject().apply {
        put("kind", form.kind.name)
        put("title", form.title)
        put("start", form.startText)
        put("end", form.endText)
        put("notes", form.notes)
        put("location", form.location)
        put("reminder", form.reminderMinutes)
        put("reminderMethod", form.reminderMethod.name)
        put("reminderTiming", form.reminderTiming.name)
        put("enabled", form.enabled)
        put("source", form.source)
        put("zone", form.zoneId)
        put("recurrence", encodeRecurrence(form.recurrence))
        put("tracking", form.trackingEnabled)
        put("completedKeys", JSONArray(form.completedKeys.sorted()))
        form.recurrenceEditScope?.let { put("recurrenceEditScope", it.name) }
        form.editOccurrenceKey?.let { put("editOccurrenceKey", it) }
    }.toString()

    private fun decodeForm(raw: String): DraftForm {
        val json = JSONObject(raw)
        return DraftForm(
            kind = TemporalKind.valueOf(json.getString("kind")),
            title = json.optString("title"),
            startText = json.optString("start"),
            endText = json.optString("end"),
            notes = json.optString("notes"),
            location = json.optString("location"),
            reminderMinutes = if (!json.has("reminderMethod") &&
                json.optString("kind") == "ALARM" && json.optInt("reminder", -1) < 0) 0
                else json.optInt("reminder", -1),
            reminderMethod = runCatching { ReminderMethod.valueOf(json.optString("reminderMethod",
                if (json.optString("kind") == "ALARM" && !json.has("reminderMethod")) "ALARM" else "MESSAGE")) }.getOrDefault(ReminderMethod.MESSAGE),
            reminderTiming = runCatching { ReminderTiming.valueOf(json.optString("reminderTiming", "OFFSET")) }.getOrDefault(ReminderTiming.OFFSET),
            enabled = json.optBoolean("enabled", true),
            source = json.optString("source", "local"),
            zoneId = json.optString("zone", ZoneId.systemDefault().id),
            recurrence = decodeRecurrence(json.optJSONObject("recurrence")),
            trackingEnabled = json.optBoolean("tracking", json.optString("kind") == "MEMO"),
            completedKeys = json.optJSONArray("completedKeys")?.let { a ->
                (0 until a.length()).map { a.getString(it) }.toSet()
            } ?: emptySet(),
            recurrenceEditScope = runCatching {
                RecurrenceEditScope.valueOf(json.optString("recurrenceEditScope"))
            }.getOrNull(),
            editOccurrenceKey = json.optString("editOccurrenceKey").takeIf(String::isNotBlank)
        )
    }

    private fun encodeRecurrence(rule: Recurrence): JSONObject = JSONObject().apply {
        put("freq", rule.freq.name)
        put("interval", rule.interval)
        rule.count?.let { put("count", it) }
        rule.untilMillis?.let { put("until", it) }
        rule.activeFromDate?.let { put("activeFromDate", it) }
        rule.activeUntilDate?.let { put("activeUntilDate", it) }
        put("byDay", JSONArray(rule.byDay.sortedBy { it.value }.map { it.value }))
        put("exceptions", JSONArray(rule.exceptions.sorted()))
        put("overrides", JSONObject().apply {
            rule.overrides.forEach { (key, value) ->
                put(key, JSONObject().apply {
                    put("start", value.startMillis)
                    value.endMillis?.let { put("end", it) }
                    value.title?.let { put("title", it) }
                })
            }
        })
    }

    private fun decodeRecurrence(json: JSONObject?): Recurrence {
        if (json == null) return Recurrence.NONE
        val dayArray = json.optJSONArray("byDay") ?: JSONArray()
        val days = (0 until dayArray.length()).mapNotNull {
            runCatching { java.time.DayOfWeek.of(dayArray.getInt(it)) }.getOrNull()
        }.toSet()
        val exceptionArray = json.optJSONArray("exceptions") ?: JSONArray()
        val exceptions = (0 until exceptionArray.length()).map { exceptionArray.getString(it) }.toSet()
        val overridesJson = json.optJSONObject("overrides")
        val overrides = buildMap {
            overridesJson?.keys()?.forEach { key ->
                val value = overridesJson.getJSONObject(key)
                put(
                    key,
                    OccurrenceOverride(
                        startMillis = value.getLong("start"),
                        endMillis = if (value.has("end")) value.getLong("end") else null,
                        title = if (value.has("title")) value.getString("title") else null
                    )
                )
            }
        }
        return Recurrence(
            freq = runCatching { RecurrenceFreq.valueOf(json.optString("freq", "NONE")) }
                .getOrDefault(RecurrenceFreq.NONE),
            interval = json.optInt("interval", 1).coerceAtLeast(1),
            count = if (json.has("count")) json.getInt("count") else null,
            untilMillis = if (json.has("until")) json.getLong("until") else null,
            activeFromDate = json.optString("activeFromDate").ifEmpty { null },
            activeUntilDate = json.optString("activeUntilDate").ifEmpty { null },
            byDay = days,
            exceptions = exceptions,
            overrides = overrides
        )
    }

    private fun encodeRecord(record: TemporalRecord): String = JSONObject().apply {
        put("eventSeq", record.eventSeq)
        put("revision", record.revision)
        put("kind", record.kind.name)
        put("title", record.title)
        put("notes", record.notes)
        put("location", record.location)
        record.startMillis?.let { put("startMs", it) }
        record.endMillis?.let { put("endMs", it) }
        record.deadlineMillis?.let { put("deadlineMs", it) }
        record.deadlineDate?.let { put("deadlineDate", it) }
        put("recurrence", encodeRecurrence(record.recurrence))
        record.memoStartDate?.let { put("memoStartDate", it) }
        record.memoEndDate?.let { put("memoEndDate", it) }
        put("tracking", record.trackingEnabled)
        put("completedKeys", JSONArray(record.completedKeys.sorted()))
        put("reminder", record.reminderMinutes)
        put("reminderMethod", record.reminderMethod.name)
        put("reminderTiming", record.reminderTiming.name)
        put("enabled", record.enabled)
        put("source", record.source)
        put("zone", record.zoneId)
        put("createdMs", record.createdAtMillis)
        put("updatedMs", record.updatedAtMillis)
    }.toString()

    private fun decodeRecord(raw: String): TemporalRecord {
        val json = JSONObject(raw)
        return TemporalRecord(
            eventSeq = json.getLong("eventSeq"),
            revision = json.getLong("revision"),
            kind = TemporalKind.valueOf(json.getString("kind")),
            title = json.getString("title"),
            notes = json.optString("notes"),
            location = json.optString("location"),
            startMillis = if (json.has("startMs")) json.getLong("startMs") else null,
            endMillis = if (json.has("endMs")) json.getLong("endMs") else null,
            deadlineMillis = if (json.has("deadlineMs")) json.getLong("deadlineMs") else null,
            deadlineDate = if (json.has("deadlineDate")) json.getString("deadlineDate") else null,
            recurrence = decodeRecurrence(json.optJSONObject("recurrence")),
            memoStartDate = json.optString("memoStartDate").ifEmpty { null },
            memoEndDate = json.optString("memoEndDate").ifEmpty { null },
            trackingEnabled = json.optBoolean("tracking", json.optString("kind") == "MEMO"),
            completedKeys = json.optJSONArray("completedKeys")?.let { a ->
                (0 until a.length()).map { a.getString(it) }.toSet()
            } ?: emptySet(),
            reminderMinutes = if (!json.has("reminderMethod") &&
                json.optString("kind") == "ALARM" && json.optInt("reminder", -1) < 0) 0
                else json.optInt("reminder", -1),
            reminderMethod = runCatching { ReminderMethod.valueOf(json.optString("reminderMethod",
                if (json.optString("kind") == "ALARM" && !json.has("reminderMethod")) "ALARM" else "MESSAGE")) }.getOrDefault(ReminderMethod.MESSAGE),
            reminderTiming = runCatching { ReminderTiming.valueOf(json.optString("reminderTiming", "OFFSET")) }.getOrDefault(ReminderTiming.OFFSET),
            enabled = json.optBoolean("enabled", true),
            source = json.optString("source", "local"),
            zoneId = json.optString("zone", ZoneId.systemDefault().id),
            createdAtMillis = json.optLong("createdMs"),
            updatedAtMillis = json.updatedMillisSafe()
        )
    }

    private fun JSONObject.updatedMillisSafe(): Long =
        if (has("updatedMs")) getLong("updatedMs") else 0L

    // ---------------------------------------------------------------- transactions

    private inline fun <T> tx(block: (SQLiteDatabase) -> T): T =
        inTransaction(writableDatabase, exclusive = true, block = block)

    /** One SQLite read snapshot, so a multi-table export cannot mix revisions across connections. */
    private inline fun <T> readSnapshot(block: (SQLiteDatabase) -> T): T =
        inTransaction(readableDatabase, exclusive = false, block = block)

    private inline fun <T> inTransaction(
        db: SQLiteDatabase,
        exclusive: Boolean,
        block: (SQLiteDatabase) -> T
    ): T {
        if (exclusive) db.beginTransaction() else db.beginTransactionNonExclusive()
        return try {
            val value = block(db)
            db.setTransactionSuccessful()
            value
        } finally {
            db.endTransaction()
        }
    }

    private fun metaLong(db: SQLiteDatabase, column: String): Long =
        db.rawQuery("SELECT $column FROM meta WHERE id=1", null).use {
            check(it.moveToFirst()) { "PowerCal meta 表损坏" }
            it.getLong(0)
        }

    private fun metaString(db: SQLiteDatabase, column: String): String =
        db.rawQuery("SELECT $column FROM meta WHERE id=1", null).use {
            check(it.moveToFirst()) { "PowerCal meta 表损坏" }
            it.getString(0)
        }

    /** Version code recorded in a backup; 0 when running under an environment without package info. */
    private fun installedVersionCode(): Long = runCatching {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }.getOrDefault(0L)

    private fun bump(db: SQLiteDatabase, column: String): Long {
        val current = metaLong(db, column)
        db.execSQL("UPDATE meta SET $column=? WHERE id=1", arrayOf(current + 1))
        return current
    }

    private fun allocateIdentity(db: SQLiteDatabase, origin: String): Long {
        val seq = bump(db, "next_seq")
        db.insertOrThrow("identities", null, ContentValues().apply {
            put("event_seq", seq)
            put("allocated_ms", System.currentTimeMillis())
            put("origin", origin)
        })
        return seq
    }

    private fun readObject(db: SQLiteDatabase, seq: Long, includeRetired: Boolean = false): TemporalRecord? {
        val sql = if (includeRetired) {
            "SELECT payload FROM objects WHERE event_seq=?"
        } else {
            "SELECT payload FROM objects WHERE event_seq=? AND retired=0"
        }
        return db.rawQuery(sql, arrayOf(seq.toString())).use {
            if (it.moveToFirst()) decodeRecord(it.getString(0)) else null
        }
    }

    private fun isRetired(db: SQLiteDatabase, seq: Long): Boolean =
        db.rawQuery("SELECT retired FROM objects WHERE event_seq=?", arrayOf(seq.toString())).use {
            it.moveToFirst() && it.getInt(0) == 1
        }

    private fun readDraft(db: SQLiteDatabase, id: Long): DraftCard? =
        db.rawQuery(
            """SELECT draft_id,event_seq,queue_order,base_rev,op,payload,status,draft_version,
                      created_ms,updated_ms
               FROM drafts WHERE draft_id=?""",
            arrayOf(id.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            DraftCard(
                draftId = cursor.getLong(0),
                eventSeq = cursor.getLong(1),
                queueOrder = cursor.getLong(2),
                baseRevision = if (cursor.isNull(3)) null else cursor.getLong(3),
                operation = DraftOperation.valueOf(cursor.getString(4)),
                form = decodeForm(cursor.getString(5)),
                status = DraftStatus.valueOf(cursor.getString(6)),
                draftVersion = cursor.getLong(7),
                createdAtMillis = cursor.getLong(8),
                updatedAtMillis = cursor.getLong(9)
            )
        }

    // ---------------------------------------------------------------- results

    sealed interface StageResult {
        data class Saved(val draftVersion: Long) : StageResult
        data class VersionMismatch(val current: DraftCard) : StageResult
        data object Locked : StageResult
        data object Missing : StageResult
        data object AlreadyFinal : StageResult
        data object NotAuthorized : StageResult
    }

    sealed interface CommitOutcome {
        data class Committed(
            val eventSeq: Long,
            val revision: Long,
            val commitId: String,
            val duplicate: Boolean = false
        ) : CommitOutcome

        data class Conflict(val current: TemporalRecord?) : CommitOutcome
        data class NeedsRestore(val retiredSeq: Long) : CommitOutcome
        data class Invalid(val reason: String) : CommitOutcome
        data object Locked : CommitOutcome
        data object NotAuthorized : CommitOutcome
    }

    sealed interface AiTaskProposalStage {
        data class Pending(
            val draft: DraftCard,
            val idempotencyKey: String,
            val reused: Boolean
        ) : AiTaskProposalStage
        data class Committed(
            val eventSeq: Long,
            val revision: Long,
            val commitId: String
        ) : AiTaskProposalStage
        data object Discarded : AiTaskProposalStage
    }

    sealed interface DirectEditOutcome {
        data class Saved(val eventSeq: Long, val revision: Long, val changed: Boolean) : DirectEditOutcome
        data class Conflict(val current: TemporalRecord?) : DirectEditOutcome
        data class Invalid(val reason: String) : DirectEditOutcome
        data object Locked : DirectEditOutcome
    }

    sealed interface RecurrenceSplitOutcome {
        data class Applied(
            val oldEventSeq: Long,
            val oldRevision: Long,
            val newEventSeq: Long,
            val newRevision: Long
        ) : RecurrenceSplitOutcome
        data class Conflict(val current: TemporalRecord?) : RecurrenceSplitOutcome
        data class Invalid(val reason: String) : RecurrenceSplitOutcome
        data object Locked : RecurrenceSplitOutcome
    }

    enum class AiState { REQUESTED, RUNNING, CANCEL_REQUESTED, COMPLETED, CANCELLED, FAILED, INTERRUPTED }

    data class AiSession(
        val sessionId: String,
        val lockEpoch: Long,
        val scope: Set<Long>,
        val allowedOperations: Set<DraftOperation>,
        val state: AiState,
        val startedAtMillis: Long,
        val updatedAtMillis: Long,
        val report: String? = null,
        /**
         * The exact `draftVersion` the user reviewed for each authorized card. A batch may only
         * commit content whose version still matches; anything newer needs a fresh review.
         */
        val reviewedVersions: Map<Long, Long> = emptyMap()
    ) {
        val isActive: Boolean
            get() = state == AiState.RUNNING || state == AiState.CANCEL_REQUESTED
    }

    data class DeckStats(val published: Int, val discarded: Int, val conflicts: Int)

    enum class TriggerRequirement { EXACT, INEXACT }
    enum class DesiredSchedule { PENDING, SCHEDULED, BLOCKED, CANCELLED, FIRED }

    data class ScheduleIntent(
        val eventSeq: Long,
        val triggerId: String,
        val revision: Long,
        val occurrenceKey: String,
        val instantMillis: Long,
        val requirement: TriggerRequirement,
        val desired: DesiredSchedule,
        val androidHint: String? = null
    )

    // ---------------------------------------------------------------- drafts

    @Synchronized
    fun newDraft(
        form: DraftForm,
        eventSeq: Long? = null,
        operation: DraftOperation = DraftOperation.CREATE,
        actor: String = "user"
    ): DraftCard {
        require(form.recurrenceEditScope == null && form.editOccurrenceKey == null) {
            "重复范围编辑只能通过正式事件检查点保存，不能分配新草稿身份"
        }
        if (eventSeq == null) {
            val seq = tx { db ->
                checkDraftWriteAllowed(db, actor)
                require(operation == DraftOperation.CREATE) { "只有新建操作可以分配新序号" }
                val allocated = allocateIdentity(db, form.source)
                // Commit the identity separately so a later draft-write failure cannot reuse it.
                // Keep the reservation retired until the corresponding draft commits.
                val retired = db.update(
                    "identities",
                    ContentValues().apply { put("retired_ms", System.currentTimeMillis()) },
                    "event_seq=?",
                    arrayOf(allocated.toString())
                )
                check(retired == 1) { "新分配的事件序号无法暂存" }
                allocated
            }
            return tx { db ->
                checkDraftWriteAllowed(db, actor)
                val draft = insertDraft(
                    db, form, seq, baseRevision = null, operation = operation
                )
                val activated = db.update(
                    "identities",
                    ContentValues().apply { putNull("retired_ms") },
                    "event_seq=? AND retired_ms IS NOT NULL",
                    arrayOf(seq.toString())
                )
                check(activated == 1) { "新建草稿的事件序号无法激活" }
                draft
            }
        }

        val targetSeq = requireNotNull(eventSeq)
        return tx { db ->
            checkDraftWriteAllowed(db, actor)
            require(operation != DraftOperation.CREATE) { "已有事件不能用新建操作创建第二张卡" }
            val existing = readObject(db, targetSeq)
            requireNotNull(existing) { "事件不存在或已删除，需先显式恢复" }
            val base = existing.revision

            val existingId = db.rawQuery(
                """SELECT draft_id FROM drafts
                   WHERE event_seq=? AND status IN ('PENDING','CONFLICT','INVALID') LIMIT 1""",
                arrayOf(targetSeq.toString())
            ).use { if (it.moveToFirst()) it.getLong(0) else null }
            if (existingId != null) {
                val active = requireNotNull(readDraft(db, existingId))
                if (active.form != form || active.operation != operation) {
                    // Preserve base_rev for stale protection, preserve queue slot and ID.
                    db.execSQL("""UPDATE drafts
                        SET payload=?, op=?, draft_version=draft_version+1,
                            status='PENDING', updated_ms=? WHERE draft_id=?""",
                        arrayOf(encodeForm(form), operation.name,
                            System.currentTimeMillis(), existingId))
                }
                return@tx requireNotNull(readDraft(db, existingId))
            }
            insertDraft(
                db, form, targetSeq, baseRevision = base, operation = operation
            )
        }
    }

    private fun checkDraftWriteAllowed(db: SQLiteDatabase, actor: String) {
        // Cards are produced *before* an authorized batch, never during it. A batch scope is
        // frozen at authorization time and must not silently grow with newly arriving cards.
        check(!isLocked(db)) { "待处理卡组正由 AI 独占，批量结束后再添加草稿" }
        check(actor != "ai" || !hasHumanEditor(db)) {
            "A human editor is active; AI calendar mutation was rejected"
        }
    }

    /**
     * Stage one candidate only after explicit review. A stable task/revision/index receipt makes
     * retries return the same draft, and rejects a different model result under the same version.
     * Identity allocation, draft insertion and receipt insertion are one SQLite transaction.
     */
    @Synchronized
    fun stageAiTaskProposal(
        taskId: String,
        taskRevision: Int,
        itemIndex: Int,
        form: DraftForm,
        operation: DraftOperation,
        eventSeq: Long?
    ): AiTaskProposalStage {
        require(taskId.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "AI task ID 无效" }
        require(taskRevision > 0) { "AI task revision 无效" }
        require(itemIndex in 0..99) { "AI 提案索引超出范围" }
        require(form.recurrenceEditScope == null && form.editOccurrenceKey == null) {
            "重复范围提案必须经正式事件范围编辑流程保存"
        }
        require((operation == DraftOperation.CREATE) == (eventSeq == null)) {
            "AI 提案操作与目标序号不一致"
        }
        val idempotencyKey = "ai-task-v1/$taskId/$taskRevision/$itemIndex"
        val fingerprintInput = JSONObject()
            .put("operation", operation.name)
            .put("eventSeq", eventSeq ?: JSONObject.NULL)
            .put("form", JSONObject(encodeForm(form)))
            .toString()
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(fingerprintInput.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        return tx { db ->
            val previous = db.rawQuery(
                """SELECT fingerprint,event_seq,draft_id,state,commit_id
                   FROM ai_task_receipts WHERE task_id=? AND task_revision=? AND item_index=?""",
                arrayOf(taskId, taskRevision.toString(), itemIndex.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) null else AiTaskReceiptRow(
                    fingerprint = cursor.getString(0),
                    eventSeq = cursor.getLong(1),
                    draftId = cursor.getLong(2),
                    state = cursor.getString(3),
                    commitId = if (cursor.isNull(4)) null else cursor.getString(4)
                )
            }
            if (previous != null) {
                check(previous.fingerprint == fingerprint) {
                    "同一 AI 任务版本的候选内容已变化，原审核结果未覆盖"
                }
                when (previous.state) {
                    "COMMITTED" -> {
                        val commit = requireNotNull(previous.commitId)
                        val revision = db.rawQuery(
                            "SELECT new_revision FROM commits WHERE commit_id=?",
                            arrayOf(commit)
                        ).use { cursor ->
                            check(cursor.moveToFirst()) { "AI 提案提交收据缺失" }
                            cursor.getLong(0)
                        }
                        return@tx AiTaskProposalStage.Committed(
                            previous.eventSeq, revision, commit)
                    }
                    "DISCARDED" -> return@tx AiTaskProposalStage.Discarded
                    "PENDING" -> {
                        val existing = requireNotNull(readDraft(db, previous.draftId)) {
                            "AI 提案收据引用的草稿不存在"
                        }
                        when (existing.status) {
                            DraftStatus.COMMITTED -> {
                                val commit = db.rawQuery(
                                    "SELECT commit_id,new_revision FROM commits WHERE draft_id=?",
                                    arrayOf(existing.draftId.toString())
                                ).use { cursor ->
                                    check(cursor.moveToFirst()) { "AI 提案提交收据缺失" }
                                    cursor.getString(0) to cursor.getLong(1)
                                }
                                db.execSQL(
                                    "UPDATE ai_task_receipts SET state='COMMITTED',commit_id=?,updated_ms=? WHERE draft_id=?",
                                    arrayOf(commit.first, System.currentTimeMillis(), existing.draftId)
                                )
                                return@tx AiTaskProposalStage.Committed(
                                    existing.eventSeq, commit.second, commit.first)
                            }
                            DraftStatus.DISCARDED -> {
                                db.execSQL(
                                    "UPDATE ai_task_receipts SET state='DISCARDED',updated_ms=? WHERE draft_id=?",
                                    arrayOf(System.currentTimeMillis(), existing.draftId)
                                )
                                return@tx AiTaskProposalStage.Discarded
                            }
                            else -> if (existing.isActionable) {
                                return@tx AiTaskProposalStage.Pending(
                                    existing, idempotencyKey, reused = true)
                            } else return@tx AiTaskProposalStage.Discarded
                        }
                    }
                    else -> error("AI 提案收据状态无效")
                }
            }

            checkDraftWriteAllowed(db, actor = "ai")
            val targetSeq: Long
            val baseRevision: Long?
            if (operation == DraftOperation.CREATE) {
                targetSeq = allocateIdentity(db, form.source)
                baseRevision = null
            } else {
                targetSeq = requireNotNull(eventSeq)
                val current = requireNotNull(readObject(db, targetSeq)) {
                    "AI 提案目标事件不存在或已删除"
                }
                val occupied = db.rawQuery(
                    "SELECT 1 FROM drafts WHERE event_seq=? AND status IN ('PENDING','CONFLICT','INVALID') LIMIT 1",
                    arrayOf(targetSeq.toString())
                ).use { it.moveToFirst() }
                check(!occupied) { "目标事件已有待处理草稿；请先处理后再审核此 AI 提案" }
                baseRevision = current.revision
            }
            val draft = insertDraft(db, form, targetSeq, baseRevision, operation)
            val now = System.currentTimeMillis()
            db.insertOrThrow("ai_task_receipts", null, ContentValues().apply {
                put("task_id", taskId)
                put("task_revision", taskRevision)
                put("item_index", itemIndex)
                put("fingerprint", fingerprint)
                put("event_seq", targetSeq)
                put("draft_id", draft.draftId)
                put("state", "PENDING")
                put("idempotency_key", idempotencyKey)
                putNull("commit_id")
                put("created_ms", now)
                put("updated_ms", now)
            })
            AiTaskProposalStage.Pending(draft, idempotencyKey, reused = false)
        }
    }

    private data class AiTaskReceiptRow(
        val fingerprint: String,
        val eventSeq: Long,
        val draftId: Long,
        val state: String,
        val commitId: String?
    )

    /**
     * Explicit creation of a complete event without an intermediate pending-draft row. Identity,
     * formal snapshot, audit commit and reminder intent share one transaction. The session key
     * makes repeated delivery of the same Save action idempotent.
     */
    @Synchronized
    fun createFormalEvent(
        form: DraftForm,
        idempotencyKey: String,
        actor: String = "user"
    ): CommitOutcome = tx { db ->
        if (actor != "user") return@tx CommitOutcome.NotAuthorized
        db.rawQuery(
            "SELECT event_seq,new_revision,commit_id FROM commits WHERE commit_id=?",
            arrayOf(idempotencyKey)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return@tx CommitOutcome.Committed(
                    cursor.getLong(0), cursor.getLong(1), cursor.getString(2), duplicate = true
                )
            }
        }
        if (form.recurrenceEditScope != null || form.editOccurrenceKey != null) {
            return@tx CommitOutcome.Invalid("重复范围修改不能作为新建事件直接发布")
        }
        if (isLocked(db)) return@tx CommitOutcome.Locked

        val now = System.currentTimeMillis()
        val candidate = when (val built = form.build(1L, 1L, now)) {
            is FormBuild.Incomplete -> return@tx CommitOutcome.Invalid(built.message)
            is FormBuild.Invalid -> return@tx CommitOutcome.Invalid(built.reason)
            is FormBuild.Ready -> built.record
        }
        val eventSeq = allocateIdentity(db, candidate.source)
        val record = candidate.copy(
            eventSeq = eventSeq,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        upsertObject(db, record)
        db.insertOrThrow("commits", null, ContentValues().apply {
            put("commit_id", idempotencyKey)
            putNull("draft_id")
            put("event_seq", eventSeq)
            putNull("previous_revision")
            put("new_revision", record.revision)
            put("actor", actor)
            put("operation", DraftOperation.CREATE.name)
            put("created_ms", now)
        })
        writeDesiredIntent(db, record, now)
        CommitOutcome.Committed(eventSeq, record.revision, idempotencyKey)
    }

    private fun insertDraft(
        db: SQLiteDatabase,
        form: DraftForm,
        eventSeq: Long,
        baseRevision: Long?,
        operation: DraftOperation
    ): DraftCard {
        val order = bump(db, "next_order")
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("event_seq", eventSeq)
            put("queue_order", order)
            if (baseRevision == null) putNull("base_rev") else put("base_rev", baseRevision)
            put("op", operation.name)
            put("payload", encodeForm(form))
            put("status", DraftStatus.PENDING.name)
            put("draft_version", 0L)
            put("created_ms", now)
            put("updated_ms", now)
        }
        val id = db.insertOrThrow("drafts", null, values)
        return requireNotNull(readDraft(db, id)) { "草稿写入失败" }
    }

    /** Creates a fresh draft for an existing event, inheriting its current snapshot. */
    @Synchronized
    fun proposeReplace(
        eventSeq: Long,
        operation: DraftOperation = DraftOperation.REPLACE,
        actor: String = "user"
    ): DraftCard {
        val record = get(eventSeq) ?: error("事件不存在或已删除")
        pendingDraftsFor(eventSeq).firstOrNull()?.let { return it }
        return newDraft(DraftForm.from(record), eventSeq, operation, actor)
    }

    /** Starts an explicit, reviewable proposal to restore a retired event under the same identity. */
    @Synchronized
    fun proposeRestoreDraft(eventSeq: Long, actor: String = "user"): DraftCard = tx { db ->
        check(!isLocked(db)) { "待处理卡组正由 AI 独占，批量结束后再添加草稿" }
        check(actor != "ai" || !hasHumanEditor(db)) {
            "A human editor is active; AI calendar mutation was rejected"
        }
        val retired = readObject(db, eventSeq, includeRetired = true)
            ?: error("事件序号 #$eventSeq 从未存在")
        check(isRetired(db, eventSeq)) { "事件 #$eventSeq 尚未删除，无需恢复" }

        val activeId = db.rawQuery(
            """SELECT draft_id FROM drafts
               WHERE event_seq=? AND status IN ('PENDING','CONFLICT','INVALID') LIMIT 1""",
            arrayOf(eventSeq.toString())
        ).use { if (it.moveToFirst()) it.getLong(0) else null }
        if (activeId != null) {
            val active = requireNotNull(readDraft(db, activeId))
            check(active.operation == DraftOperation.REPLACE || active.operation == DraftOperation.CONVERT) {
                "该事件已有不兼容的待处理草稿"
            }
            return@tx active
        }

        val now = System.currentTimeMillis()
        val order = bump(db, "next_order")
        val draftId = db.insertOrThrow("drafts", null, ContentValues().apply {
            put("event_seq", eventSeq)
            put("queue_order", order)
            put("base_rev", retired.revision)
            put("op", DraftOperation.REPLACE.name)
            put("payload", encodeForm(DraftForm.from(retired)))
            put("status", DraftStatus.PENDING.name)
            put("draft_version", 0L)
            put("created_ms", now)
            put("updated_ms", now)
        })
        requireNotNull(readDraft(db, draftId)) { "恢复草稿写入失败" }
    }

    /**
     * Optimistic concurrency: a save only lands if the caller edited the version it read.
     * A mismatch returns the newer card instead of overwriting someone else's text.
     *
     * A batch session may not rewrite cards at all: the authorization covers the reviewed
     * snapshots, and the AI's own output is always a *new* draft card created before the batch.
     */
    @Synchronized
    fun stageDraft(
        draftId: Long,
        form: DraftForm,
        expectedVersion: Long,
        aiSession: AiSession? = null
    ): StageResult = tx { db ->
        if (aiSession != null) {
            return@tx StageResult.NotAuthorized
        }
        if (isLocked(db)) {
            return@tx StageResult.Locked
        }
        val now = System.currentTimeMillis()
        val updated = db.compileStatement(
            """UPDATE drafts SET payload=?, draft_version=draft_version+1, updated_ms=?,
                      status=CASE WHEN status='INVALID' THEN 'PENDING' ELSE status END
               WHERE draft_id=? AND draft_version=? AND status IN ('PENDING','CONFLICT','INVALID')"""
        ).apply {
            bindString(1, encodeForm(form))
            bindLong(2, System.currentTimeMillis())
            bindLong(3, draftId)
            bindLong(4, expectedVersion)
        }.executeUpdateDelete()
        if (updated == 1) {
            val card = readDraft(db, draftId)
                ?: return@tx StageResult.Missing
            return@tx StageResult.Saved(card.draftVersion)
        }
        val card = readDraft(db, draftId) ?: return@tx StageResult.Missing
        if (!card.isActionable) return@tx StageResult.AlreadyFinal
        StageResult.VersionMismatch(card)
    }

    @Synchronized
    fun draft(draftId: Long): DraftCard? = readDraft(readableDatabase, draftId)

    /** Pending queue, oldest first. Filters never mutate this order. */
    @Synchronized
    fun pendingDrafts(): List<DraftCard> {
        val db = readableDatabase
        val ids = db.rawQuery(
            "SELECT draft_id FROM drafts WHERE status IN ('PENDING','CONFLICT','INVALID') ORDER BY queue_order ASC",
            null
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        return ids.mapNotNull { readDraft(db, it) }
    }

    @Synchronized
    fun draftsByStatus(status: DraftStatus): List<DraftCard> {
        val db = readableDatabase
        val ids = db.rawQuery(
            "SELECT draft_id FROM drafts WHERE status=? ORDER BY queue_order ASC",
            arrayOf(status.name)
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
        return ids.mapNotNull { readDraft(db, it) }
    }

    /** Open cards for one event; the timeline reuses one instead of stacking duplicates. */
    @Synchronized
    fun pendingDraftsFor(eventSeq: Long): List<DraftCard> {
        val db = readableDatabase
        val ids = db.rawQuery(
            """SELECT draft_id FROM drafts
               WHERE event_seq=? AND status IN ('PENDING','CONFLICT','INVALID') ORDER BY queue_order ASC""",
            arrayOf(eventSeq.toString())
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
        return ids.mapNotNull { readDraft(db, it) }
    }

    @Synchronized
    fun discard(draftId: Long, aiSession: AiSession? = null): Boolean = tx { db ->
        if (aiSession != null) {
            if (authorize(db, aiSession, draftId) != null) return@tx false
        } else if (isLocked(db)) {
            return@tx false
        }
        val card = readDraft(db, draftId) ?: return@tx false
        if (card.status == DraftStatus.COMMITTED) return@tx false
        val now = System.currentTimeMillis()
        val updated = db.compileStatement(
            """UPDATE drafts SET status='DISCARDED', updated_ms=?
               WHERE draft_id=? AND status IN ('PENDING','CONFLICT','INVALID')"""
        ).apply {
            bindLong(1, now)
            bindLong(2, draftId)
        }.executeUpdateDelete()
        // Report the row count, not the earlier read: a concurrent discard must not look successful.
        if (updated == 1) db.execSQL(
            "UPDATE ai_task_receipts SET state='DISCARDED',updated_ms=? WHERE draft_id=? AND state='PENDING'",
            arrayOf(now, draftId)
        )
        updated == 1
    }

    /** Undo keeps the same draftId and queueOrder, so the card returns to its old place. */
    @Synchronized
    fun undoDiscard(draftId: Long): Boolean = tx { db ->
        if (isLocked(db)) return@tx false
        val existing = readDraft(db, draftId) ?: return@tx false
        if (existing.status != DraftStatus.DISCARDED) return@tx false
        val occupied = db.rawQuery(
            """SELECT 1 FROM drafts WHERE event_seq=? AND draft_id<>?
               AND status IN ('PENDING','CONFLICT','INVALID') LIMIT 1""",
            arrayOf(existing.eventSeq.toString(), draftId.toString())
        ).use { it.moveToFirst() }
        if (occupied) return@tx false
        val now = System.currentTimeMillis()
        val updated = db.compileStatement(
            "UPDATE drafts SET status='PENDING', updated_ms=? WHERE draft_id=? AND status='DISCARDED'"
        ).apply {
            bindLong(1, now)
            bindLong(2, draftId)
        }.executeUpdateDelete()
        if (updated == 1) db.execSQL(
            "UPDATE ai_task_receipts SET state='PENDING',commit_id=NULL,updated_ms=? WHERE draft_id=?",
            arrayOf(now, draftId)
        )
        updated == 1
    }

    // ---------------------------------------------------------------- commits

    @Synchronized
    fun commit(
        draftId: Long,
        approveStale: Boolean = false,
        actor: String = "user",
        aiSession: AiSession? = null,
        idempotencyKey: String? = null
    ): CommitOutcome = tx { db ->
        if (aiSession != null) {
            val authorization = authorize(db, aiSession, draftId)
            if (authorization != null) return@tx authorization
        } else {
            // The audit actor string is not a capability. AI publication must carry the exact
            // live, one-use session created after the reviewed snapshot was explicitly approved.
            if (actor == "ai") return@tx CommitOutcome.NotAuthorized
            if (isLocked(db)) return@tx CommitOutcome.Locked
        }

        // Idempotency: a card has at most one commit record; repeating the click returns it.
        // A caller-supplied key (AI task, retry) is honoured before touching anything.
        val existingSql = if (idempotencyKey != null) {
            "SELECT event_seq,new_revision,commit_id FROM commits WHERE draft_id=? OR commit_id=? LIMIT 1"
        } else {
            "SELECT event_seq,new_revision,commit_id FROM commits WHERE draft_id=?"
        }
        val existingArgs = if (idempotencyKey != null) {
            arrayOf(draftId.toString(), idempotencyKey)
        } else {
            arrayOf(draftId.toString())
        }
        db.rawQuery(existingSql, existingArgs).use { cursor ->
            if (cursor.moveToFirst()) {
                return@tx CommitOutcome.Committed(
                    cursor.getLong(0), cursor.getLong(1), cursor.getString(2), duplicate = true
                )
            }
        }

        val draft = readDraft(db, draftId) ?: return@tx CommitOutcome.Invalid("草稿不存在")
        if (!draft.isActionable) return@tx CommitOutcome.Invalid("草稿已经处理")
        if (draft.form.recurrenceEditScope != null || draft.form.editOccurrenceKey != null) {
            return@tx CommitOutcome.Invalid("请从原重复事件继续编辑此范围草稿，不能将其作为普通草稿发布")
        }

        val current = readObject(db, draft.eventSeq)
        val retired = isRetired(db, draft.eventSeq)

        if (draft.operation.requiresExisting && current == null) {
            return@tx if (retired) CommitOutcome.NeedsRestore(draft.eventSeq)
            else CommitOutcome.Invalid("目标事件不存在")
        }
        if (draft.operation == DraftOperation.CREATE && current != null) {
            db.execSQL("UPDATE drafts SET status='INVALID', updated_ms=? WHERE draft_id=?",
                arrayOf(System.currentTimeMillis(), draftId))
            return@tx CommitOutcome.Invalid("该事件序号已存在正式对象，不能用新建操作覆盖")
        }
        if (draft.operation == DraftOperation.CREATE && retired) {
            db.execSQL("UPDATE drafts SET status='CONFLICT' WHERE draft_id=?", arrayOf(draftId))
            return@tx CommitOutcome.NeedsRestore(draft.eventSeq)
        }

        val stale = CommitRules.isStale(draft.baseRevision, current?.revision)
        if (stale && !approveStale) {
            db.execSQL("UPDATE drafts SET status='CONFLICT' WHERE draft_id=?", arrayOf(draftId))
            return@tx CommitOutcome.Conflict(current)
        }

        val now = System.currentTimeMillis()
        val newRevision = CommitRules.nextRevision(current?.revision)
        if (draft.operation == DraftOperation.DELETE) {
            // The tombstone must carry the new revision too: the payload is the authoritative
            // snapshot everywhere else, and a stale payload would let a later restore reuse a
            // revision number that already existed.
            current?.let { upsertObject(db, it.copy(revision = newRevision, updatedAtMillis = now), retired = true) }
            db.execSQL(
                "UPDATE objects SET revision=?, retired=1, updated_ms=? WHERE event_seq=?",
                arrayOf(newRevision, now, draft.eventSeq)
            )
            db.execSQL(
                "UPDATE identities SET retired_ms=? WHERE event_seq=?",
                arrayOf(now, draft.eventSeq)
            )
            cancelIntents(db, draft.eventSeq, now)
        } else {
            val built = draft.form.build(draft.eventSeq, newRevision, now)
            when (built) {
                is FormBuild.Incomplete -> {
                    db.execSQL("UPDATE drafts SET status='INVALID', updated_ms=? WHERE draft_id=?",
                        arrayOf(now, draftId))
                    return@tx CommitOutcome.Invalid(built.message)
                }
                is FormBuild.Invalid -> {
                    db.execSQL("UPDATE drafts SET status='INVALID', updated_ms=? WHERE draft_id=?",
                        arrayOf(now, draftId))
                    return@tx CommitOutcome.Invalid(built.reason)
                }
                is FormBuild.Ready -> {
                    val record = built.record.copy(
                        // Creation time is identity metadata, not user content: never rewritten
                        // by a later whole-snapshot replacement.
                        createdAtMillis = current?.createdAtMillis?.takeIf { it > 0 } ?: now,
                        updatedAtMillis = now
                    )
                    upsertObject(db, record)
                    db.execSQL(
                        "UPDATE identities SET retired_ms=NULL WHERE event_seq=?",
                        arrayOf(draft.eventSeq)
                    )
                }
            }
        }

        val commitId = idempotencyKey ?: UUID.randomUUID().toString()
        db.insertOrThrow("commits", null, ContentValues().apply {
            put("commit_id", commitId)
            put("draft_id", draftId)
            put("event_seq", draft.eventSeq)
            if (current == null) putNull("previous_revision") else put("previous_revision", current.revision)
            put("new_revision", newRevision)
            put("actor", actor)
            put("operation", draft.operation.name)
            put("created_ms", now)
        })
        finishEventDrafts(db, draft.eventSeq, draftId, now)
        if (draft.operation != DraftOperation.DELETE) {
            writeDesiredIntent(db, readObject(db, draft.eventSeq), now)
        }
        CommitOutcome.Committed(draft.eventSeq, newRevision, commitId)
    }

    /**
     * Explicit resurrection of a retired identity, driven by the card that proposed it. The card is
     * committed in the same transaction, so it cannot survive as a stale duplicate proposal.
     * AI cannot restore: resurrection always requires an explicit human confirmation on a card.
     */
    @Synchronized
    fun restoreDraft(
        draftId: Long,
        form: DraftForm,
        actor: String = "user"
    ): CommitOutcome = tx { db ->
        if (isLocked(db)) return@tx CommitOutcome.Locked
        if (actor != "user") return@tx CommitOutcome.NotAuthorized
        val card = readDraft(db, draftId) ?: return@tx CommitOutcome.Invalid("草稿不存在")
        if (!card.isActionable) return@tx CommitOutcome.Invalid("草稿已经处理")
        if (card.form.recurrenceEditScope != null || card.form.editOccurrenceKey != null) {
            return@tx CommitOutcome.Invalid("重复范围草稿不能用于普通恢复；请从原事件继续编辑")
        }
        if (card.operation != DraftOperation.REPLACE && card.operation != DraftOperation.CONVERT) {
            return@tx CommitOutcome.Invalid("恢复必须由修改或类型转换草稿发起")
        }
        val eventSeq = card.eventSeq
        val retiredRecord = readObject(db, eventSeq, includeRetired = true)
            ?: return@tx CommitOutcome.Invalid("该事件序号从未存在")
        if (!isRetired(db, eventSeq)) return@tx CommitOutcome.Invalid("该事件未被删除，无需恢复")
        val now = System.currentTimeMillis()
        val newRevision = CommitRules.nextRevision(retiredRecord.revision)
        return@tx when (val built = form.build(eventSeq, newRevision, now)) {
            is FormBuild.Incomplete -> {
                db.execSQL("UPDATE drafts SET status='INVALID', updated_ms=? WHERE draft_id=?",
                    arrayOf(now, draftId))
                CommitOutcome.Invalid(built.message)
            }
            is FormBuild.Invalid -> {
                db.execSQL("UPDATE drafts SET status='INVALID', updated_ms=? WHERE draft_id=?",
                    arrayOf(now, draftId))
                CommitOutcome.Invalid(built.reason)
            }
            is FormBuild.Ready -> {
                upsertObject(db, built.record.copy(
                    createdAtMillis = retiredRecord.createdAtMillis.takeIf { it > 0 } ?: now,
                    updatedAtMillis = now
                ))
                db.execSQL("UPDATE identities SET retired_ms=NULL WHERE event_seq=?", arrayOf(eventSeq))
                val commitId = UUID.randomUUID().toString()
                db.insertOrThrow("commits", null, ContentValues().apply {
                    put("commit_id", commitId)
                    put("draft_id", draftId)
                    put("event_seq", eventSeq)
                    put("previous_revision", retiredRecord.revision)
                    put("new_revision", newRevision)
                    put("actor", actor)
                    put("operation", "RESTORE")
                    put("created_ms", now)
                })
                finishEventDrafts(db, eventSeq, draftId, now)
                writeDesiredIntent(db, readObject(db, eventSeq), now)
                CommitOutcome.Committed(eventSeq, newRevision, commitId)
            }
        }
    }

    /**
     * Explicit Save from the in-memory formal editor. It never creates a replacement draft.
     * A semantic no-op still clears the event's actionable checkpoint, but creates no revision.
     */
    @Synchronized
    fun saveFormalEdit(
        eventSeq: Long,
        expectedRevision: Long,
        form: DraftForm,
        editorToken: String
    ): DirectEditOutcome = tx { db ->
        if (isLocked(db) || !ownsHumanEditor(db, editorToken)) return@tx DirectEditOutcome.Locked
        if (form.recurrenceEditScope != null || form.editOccurrenceKey != null) {
            return@tx DirectEditOutcome.Invalid("正式事件保存必须先将重复范围应用到实例或序列")
        }
        val current = readObject(db, eventSeq) ?: return@tx DirectEditOutcome.Conflict(null)
        if (current.revision != expectedRevision) return@tx DirectEditOutcome.Conflict(current)
        val now = System.currentTimeMillis()
        val candidate = when (val built = form.build(eventSeq, current.revision + 1, now)) {
            is FormBuild.Incomplete -> return@tx DirectEditOutcome.Invalid(built.message)
            is FormBuild.Invalid -> return@tx DirectEditOutcome.Invalid(built.reason)
            is FormBuild.Ready -> built.record.copy(
                createdAtMillis = current.createdAtMillis,
                updatedAtMillis = now
            )
        }
        val changed = !sameFormalContent(current, candidate)
        if (changed) {
            upsertObject(db, candidate)
            db.insertOrThrow("commits", null, ContentValues().apply {
                put("commit_id", UUID.randomUUID().toString())
                putNull("draft_id")
                put("event_seq", eventSeq)
                put("previous_revision", current.revision)
                put("new_revision", candidate.revision)
                put("actor", "user")
                put("operation", DraftOperation.REPLACE.name)
                put("created_ms", now)
            })
            writeDesiredIntent(db, candidate, now)
        }
        db.execSQL(
            """UPDATE drafts SET status='DISCARDED', updated_ms=?
               WHERE event_seq=? AND status IN ('PENDING','CONFLICT','INVALID')""",
            arrayOf(now, eventSeq)
        )
        DirectEditOutcome.Saved(eventSeq, if (changed) candidate.revision else current.revision, changed)
    }

    /**
     * Applies a repeated-event edit from one original slot forward. The old series is truncated
     * before that slot and the edited future becomes a new independent event identity, all in one
     * SQLite transaction. Only future occurrence state that is still a generated slot in the new
     * rule is carried across; no cross-event inheritance link is stored.
     * If the selected slot is the first active occurrence, the edit is equivalent to a whole-series
     * edit and keeps the existing identity because there is no earlier active segment to split off.
     *
     * [form] must describe the new series with its anchor at the selected occurrence's edited
     * start. When its count is unchanged from the old rule, the new series receives only the old
     * rule's remaining count from the selected slot onward.
     */
    @Synchronized
    fun saveThisAndFutureRecurringEdit(
        eventSeq: Long,
        expectedRevision: Long,
        selectedOccurrenceKey: String,
        form: DraftForm,
        editorToken: String
    ): RecurrenceSplitOutcome = tx { db ->
        if (isLocked(db) || !ownsHumanEditor(db, editorToken)) {
            return@tx RecurrenceSplitOutcome.Locked
        }
        if (form.recurrenceEditScope != null || form.editOccurrenceKey != null) {
            return@tx RecurrenceSplitOutcome.Invalid("保存拆分前必须移除编辑器的临时范围元数据")
        }
        val current = readObject(db, eventSeq)
            ?: return@tx RecurrenceSplitOutcome.Conflict(null)
        if (current.revision != expectedRevision) {
            return@tx RecurrenceSplitOutcome.Conflict(current)
        }
        if (!current.recurrence.isRepeating) {
            return@tx RecurrenceSplitOutcome.Invalid("只有重复事件可以按此实例拆分后续序列")
        }
        val selectedIndex = RecurrenceEngine.slotIndexForKey(current, selectedOccurrenceKey)
            ?: return@tx RecurrenceSplitOutcome.Invalid("所选实例不是该重复规则生成的原始槽位")
        if (!RecurrenceEngine.activeOnOriginalKey(current.recurrence, selectedOccurrenceKey) ||
            RecurrenceEngine.occurrenceForKey(current, selectedOccurrenceKey) == null
        ) {
            return@tx RecurrenceSplitOutcome.Invalid("所选实例已跳过或不在重复日期范围内")
        }
        val selectedDate = runCatching {
            LocalDate.parse(selectedOccurrenceKey.substringBefore('T'))
        }.getOrNull() ?: return@tx RecurrenceSplitOutcome.Invalid("所选实例键格式无效")
        if (!RecurrenceEngine.hasEarlierActiveOccurrence(current, selectedOccurrenceKey)) {
            return@tx saveWholeRecurringSeriesInTransaction(
                db,
                current,
                form,
                System.currentTimeMillis(),
                selectedIndex,
                selectedOccurrenceKey
            )
        }
        val cutoffDate = selectedDate.minusDays(1)
        val existingUntil = current.recurrence.activeUntilDate?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val oldUntil = minOf(cutoffDate, existingUntil ?: cutoffDate)
        val now = System.currentTimeMillis()
        val oldRule = current.recurrence.copy(
            count = selectedIndex,
            activeUntilDate = oldUntil.toString(),
            exceptions = current.recurrence.exceptions.filterTo(mutableSetOf()) {
                it < selectedOccurrenceKey
            },
            overrides = current.recurrence.overrides.filterKeys { it < selectedOccurrenceKey }
        )
        val oldRecord = current.copy(
            revision = current.revision + 1,
            recurrence = oldRule,
            completedKeys = current.completedKeys.filterTo(mutableSetOf()) {
                it < selectedOccurrenceKey
            },
            updatedAtMillis = now
        )
        oldRecord.validate()?.let {
            return@tx RecurrenceSplitOutcome.Invalid("原系列截断后无效：$it")
        }

        val newEventSeq = metaLong(db, "next_seq")
        val built = when (val result = form.build(newEventSeq, 1L, now)) {
            is FormBuild.Incomplete -> return@tx RecurrenceSplitOutcome.Invalid(result.message)
            is FormBuild.Invalid -> return@tx RecurrenceSplitOutcome.Invalid(result.reason)
            is FormBuild.Ready -> result.record
        }
        if (!built.recurrence.isRepeating) {
            return@tx RecurrenceSplitOutcome.Invalid("此实例及后续必须保留为重复序列")
        }
        val newCount = if (current.recurrence.count != null &&
            form.recurrence.count == current.recurrence.count
        ) {
            current.recurrence.count - selectedIndex
        } else {
            built.recurrence.count
        }
        if (newCount != null && newCount < 1) {
            return@tx RecurrenceSplitOutcome.Invalid("新序列必须至少包含所选实例")
        }
        val baseRule = built.recurrence.copy(
            count = newCount,
            exceptions = emptySet(),
            overrides = emptyMap()
        )
        val baseRecord = built.copy(recurrence = baseRule, completedKeys = emptySet())
        val newAnchor = baseRecord.anchorMillis()
            ?: return@tx RecurrenceSplitOutcome.Invalid("新序列缺少开始锚点")
        val newAnchorKey = RecurrenceEngine.keyFor(
            Instant.ofEpochMilli(newAnchor).atZone(baseRecord.zone).toLocalDateTime()
        )
        if (!RecurrenceEngine.activeOnOriginalKey(baseRule, newAnchorKey) ||
            RecurrenceEngine.slotIndexForKey(baseRecord, newAnchorKey) == null
        ) {
            return@tx RecurrenceSplitOutcome.Invalid("新规则未包含所选实例对应的新系列锚点")
        }

        fun compatibleFutureKey(key: String): Boolean =
            key >= selectedOccurrenceKey &&
                RecurrenceEngine.activeOnOriginalKey(baseRule, key) &&
                RecurrenceEngine.slotIndexForKey(baseRecord, key) != null

        val migratedExceptions = (current.recurrence.exceptions + form.recurrence.exceptions)
            .filterTo(mutableSetOf(), ::compatibleFutureKey)
        // The selected occurrence's effective values are now the new series base. Keeping its
        // old override would shadow an edited base title (and can retain stale per-slot timing).
        val migratedOverrides = (current.recurrence.overrides + form.recurrence.overrides)
            .filterKeys { it != selectedOccurrenceKey && compatibleFutureKey(it) }
        val migratedCompleted = (current.completedKeys + form.completedKeys)
            .filterTo(mutableSetOf(), ::compatibleFutureKey)
        val newRecord = baseRecord.copy(
            recurrence = baseRule.copy(
                exceptions = migratedExceptions,
                overrides = migratedOverrides
            ),
            completedKeys = migratedCompleted
        )
        newRecord.validate()?.let {
            return@tx RecurrenceSplitOutcome.Invalid("新系列无效：$it")
        }

        check(allocateIdentity(db, newRecord.source) == newEventSeq) {
            "新序列身份分配与事务高水位不一致"
        }
        upsertObject(db, oldRecord)
        upsertObject(db, newRecord)
        db.insertOrThrow("commits", null, ContentValues().apply {
            put("commit_id", UUID.randomUUID().toString())
            putNull("draft_id")
            put("event_seq", eventSeq)
            put("previous_revision", current.revision)
            put("new_revision", oldRecord.revision)
            put("actor", "user")
            put("operation", DraftOperation.REPLACE.name)
            put("created_ms", now)
        })
        db.insertOrThrow("commits", null, ContentValues().apply {
            put("commit_id", UUID.randomUUID().toString())
            putNull("draft_id")
            put("event_seq", newEventSeq)
            putNull("previous_revision")
            put("new_revision", newRecord.revision)
            put("actor", "user")
            put("operation", DraftOperation.CREATE.name)
            put("created_ms", now)
        })
        db.execSQL(
            "UPDATE drafts SET status='DISCARDED',updated_ms=? WHERE event_seq=? AND status IN ('PENDING','CONFLICT','INVALID')",
            arrayOf(now, eventSeq)
        )
        writeDesiredIntent(db, oldRecord, now)
        writeDesiredIntent(db, newRecord, now)
        RecurrenceSplitOutcome.Applied(eventSeq, oldRecord.revision, newEventSeq, newRecord.revision)
    }

    private fun saveWholeRecurringSeriesInTransaction(
        db: SQLiteDatabase,
        current: TemporalRecord,
        form: DraftForm,
        now: Long,
        selectedIndex: Int,
        selectedOccurrenceKey: String
    ): RecurrenceSplitOutcome {
        val builtRecord = when (val built = form.build(current.eventSeq, current.revision + 1, now)) {
            is FormBuild.Incomplete -> return RecurrenceSplitOutcome.Invalid(built.message)
            is FormBuild.Invalid -> return RecurrenceSplitOutcome.Invalid(built.reason)
            is FormBuild.Ready -> built.record.copy(
                createdAtMillis = current.createdAtMillis,
                updatedAtMillis = now
            )
        }
        if (!builtRecord.recurrence.isRepeating) {
            return RecurrenceSplitOutcome.Invalid("此实例及后续必须保留为重复序列")
        }

        val remainingCount = if (current.recurrence.count != null &&
            form.recurrence.count == current.recurrence.count
        ) {
            current.recurrence.count - selectedIndex
        } else {
            builtRecord.recurrence.count
        }
        if (remainingCount != null && remainingCount < 1) {
            return RecurrenceSplitOutcome.Invalid("新序列必须至少包含所选实例")
        }
        val baseRule = builtRecord.recurrence.copy(
            count = remainingCount,
            exceptions = emptySet(),
            overrides = emptyMap()
        )
        val baseRecord = builtRecord.copy(recurrence = baseRule, completedKeys = emptySet())
        val newAnchor = baseRecord.anchorMillis()
            ?: return RecurrenceSplitOutcome.Invalid("新序列缺少开始锚点")
        val newAnchorKey = RecurrenceEngine.keyFor(
            Instant.ofEpochMilli(newAnchor).atZone(baseRecord.zone).toLocalDateTime()
        )
        if (!RecurrenceEngine.activeOnOriginalKey(baseRule, newAnchorKey) ||
            RecurrenceEngine.slotIndexForKey(baseRecord, newAnchorKey) == null
        ) {
            return RecurrenceSplitOutcome.Invalid("新规则未包含所选实例对应的新系列锚点")
        }

        fun compatibleFutureKey(key: String): Boolean =
            key >= selectedOccurrenceKey &&
                RecurrenceEngine.activeOnOriginalKey(baseRule, key) &&
                RecurrenceEngine.slotIndexForKey(baseRecord, key) != null

        val candidate = baseRecord.copy(
            recurrence = baseRule.copy(
                exceptions = (current.recurrence.exceptions + form.recurrence.exceptions)
                    .filterTo(mutableSetOf(), ::compatibleFutureKey),
                overrides = (current.recurrence.overrides + form.recurrence.overrides)
                    .filterKeys(::compatibleFutureKey)
            ),
            completedKeys = (current.completedKeys + form.completedKeys)
                .filterTo(mutableSetOf(), ::compatibleFutureKey)
        )
        candidate.validate()?.let {
            return RecurrenceSplitOutcome.Invalid("新系列无效：$it")
        }

        val changed = !sameFormalContent(current, candidate)
        if (changed) {
            upsertObject(db, candidate)
            db.insertOrThrow("commits", null, ContentValues().apply {
                put("commit_id", UUID.randomUUID().toString())
                putNull("draft_id")
                put("event_seq", current.eventSeq)
                put("previous_revision", current.revision)
                put("new_revision", candidate.revision)
                put("actor", "user")
                put("operation", DraftOperation.REPLACE.name)
                put("created_ms", now)
            })
            writeDesiredIntent(db, candidate, now)
        }
        db.execSQL(
            "UPDATE drafts SET status='DISCARDED',updated_ms=? WHERE event_seq=? AND status IN ('PENDING','CONFLICT','INVALID')",
            arrayOf(now, current.eventSeq)
        )
        return RecurrenceSplitOutcome.Applied(
            current.eventSeq,
            if (changed) candidate.revision else current.revision,
            current.eventSeq,
            if (changed) candidate.revision else current.revision
        )
    }

    /** Direct, confirmed formal delete with tombstone, schedule cancellation and draft cleanup. */
    @Synchronized
    fun deleteFormalEvent(
        eventSeq: Long,
        expectedRevision: Long,
        editorToken: String
    ): DirectEditOutcome = tx { db ->
        if (isLocked(db) || !ownsHumanEditor(db, editorToken)) return@tx DirectEditOutcome.Locked
        val current = readObject(db, eventSeq) ?: return@tx DirectEditOutcome.Conflict(null)
        if (current.revision != expectedRevision) return@tx DirectEditOutcome.Conflict(current)
        val now = System.currentTimeMillis()
        val revision = current.revision + 1
        upsertObject(db, current.copy(revision = revision, updatedAtMillis = now), retired = true)
        db.execSQL("UPDATE identities SET retired_ms=? WHERE event_seq=?", arrayOf(now, eventSeq))
        cancelIntents(db, eventSeq, now)
        db.execSQL(
            """UPDATE drafts SET status='DISCARDED', updated_ms=?
               WHERE event_seq=? AND status IN ('PENDING','CONFLICT','INVALID')""",
            arrayOf(now, eventSeq)
        )
        db.insertOrThrow("commits", null, ContentValues().apply {
            put("commit_id", UUID.randomUUID().toString()); putNull("draft_id")
            put("event_seq", eventSeq); put("previous_revision", current.revision)
            put("new_revision", revision); put("actor", "user")
            put("operation", DraftOperation.DELETE.name); put("created_ms", now)
        })
        DirectEditOutcome.Saved(eventSeq, revision, true)
    }

    /** Persist one meaningful exit checkpoint, reusing the event's sole active draft. */
    @Synchronized
    fun checkpointFormalEdit(
        eventSeq: Long,
        expectedRevision: Long,
        original: DraftForm,
        current: DraftForm,
        editorToken: String
    ): DraftCard? = tx { db ->
            check(!isLocked(db) && ownsHumanEditor(db, editorToken)) { "Editor lease is not writable" }
            val formal = readObject(db, eventSeq) ?: error("Event no longer exists")
            check(formal.revision == expectedRevision) { "Event changed while editing" }
            if (original == current) {
                db.execSQL(
                    "UPDATE drafts SET status='DISCARDED',updated_ms=? WHERE event_seq=? AND status IN ('PENDING','CONFLICT','INVALID')",
                    arrayOf(System.currentTimeMillis(), eventSeq)
                )
                return@tx null
            }
            val active = db.rawQuery(
                """SELECT draft_id FROM drafts WHERE event_seq=?
                   AND status IN ('PENDING','CONFLICT','INVALID') LIMIT 1""",
                arrayOf(eventSeq.toString())
            ).use { if (it.moveToFirst()) it.getLong(0) else null }
            if (active != null) {
                val old = requireNotNull(readDraft(db, active))
                if (old.form != current || old.operation != DraftOperation.REPLACE) {
                    db.execSQL(
                        """UPDATE drafts SET payload=?,op='REPLACE',draft_version=draft_version+1,
                           status='PENDING',updated_ms=? WHERE draft_id=?""",
                        arrayOf(encodeForm(current), System.currentTimeMillis(), active)
                    )
                }
                return@tx requireNotNull(readDraft(db, active))
            }
            val order = bump(db, "next_order")
            val now = System.currentTimeMillis()
            val id = db.insertOrThrow("drafts", null, ContentValues().apply {
                put("event_seq", eventSeq); put("queue_order", order); put("base_rev", expectedRevision)
                put("op", DraftOperation.REPLACE.name); put("payload", encodeForm(current))
                put("status", DraftStatus.PENDING.name); put("draft_version", 0L)
                put("created_ms", now); put("updated_ms", now)
            })
            requireNotNull(readDraft(db, id))
        }

    private fun sameFormalContent(a: TemporalRecord, b: TemporalRecord): Boolean =
        a.copy(revision = 0, createdAtMillis = 0, updatedAtMillis = 0) ==
            b.copy(revision = 0, createdAtMillis = 0, updatedAtMillis = 0)

    private fun upsertObject(db: SQLiteDatabase, record: TemporalRecord, retired: Boolean = false) {
        val values = ContentValues().apply {
            put("event_seq", record.eventSeq)
            put("revision", record.revision)
            put("kind", record.kind.name)
            put("anchor_ms", record.anchorMillis())
            put("span_end_ms", record.spanEndMillis())
            put("payload", encodeRecord(record))
            put("retired", if (retired) 1 else 0)
            put("created_ms", record.createdAtMillis)
            put("updated_ms", record.updatedAtMillis)
        }
        db.insertWithOnConflict("objects", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------------------------------------------------------------- formal reads

    @Synchronized
    fun get(eventSeq: Long): TemporalRecord? = readObject(readableDatabase, eventSeq)

    /** Explicit user checkbox only; version checked, atomic payload and audit commit. */
    @Synchronized
    fun setCompleted(eventSeq: Long, occurrenceKey: String, completed: Boolean,
                     expectedRevision: Long): Boolean = tx { db ->
        if (isLocked(db)) return@tx false
        val current = readObject(db, eventSeq) ?: return@tx false
        if (current.revision != expectedRevision || !current.trackingEnabled) return@tx false
        if (current.recurrence.isRepeating) {
            if (RecurrenceEngine.occurrenceForKey(current, occurrenceKey) == null) return@tx false
        } else if (occurrenceKey != "single") return@tx false
        val keys = if (completed) current.completedKeys + occurrenceKey
            else current.completedKeys - occurrenceKey
        if (keys == current.completedKeys) return@tx true
        val now = System.currentTimeMillis()
        val updated = current.copy(revision = current.revision + 1,
            completedKeys = keys, updatedAtMillis = now)
        upsertObject(db, updated)
        db.insertOrThrow("commits", null, ContentValues().apply {
            put("commit_id", UUID.randomUUID().toString())
            putNull("draft_id")
            put("event_seq", eventSeq)
            put("previous_revision", current.revision)
            put("new_revision", updated.revision)
            put("actor", "user")
            put("operation", if (completed) "COMPLETE" else "REOPEN")
            put("created_ms", now)
        })
        true
    }

    @Synchronized
    fun getIncludingRetired(eventSeq: Long): TemporalRecord? =
        readObject(readableDatabase, eventSeq, includeRetired = true)

    @Synchronized
    fun isRetired(eventSeq: Long): Boolean = isRetired(readableDatabase, eventSeq)

    /** All active formal objects, ordered by axis anchor for the timeline. */
    @Synchronized
    fun all(): List<TemporalRecord> =
        readableDatabase.rawQuery(
            "SELECT payload FROM objects WHERE retired=0 ORDER BY CASE WHEN anchor_ms IS NULL THEN 1 ELSE 0 END, anchor_ms ASC",
            null
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(decodeRecord(cursor.getString(0))) }
        }

    /** Rebuild only floating daily Alarm intents after a system-zone change or recovery event. */
    @Synchronized
    fun rebuildLocalDailyAlarmSchedules(
        localZone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): Int = tx { db ->
        val alarms = db.rawQuery(
            "SELECT payload FROM objects WHERE retired=0 AND kind='ALARM' ORDER BY event_seq",
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val record = decodeRecord(cursor.getString(0))
                    if (record.recurrence.freq == RecurrenceFreq.DAILY) add(record)
                }
            }
        }
        alarms.forEach { writeDesiredIntent(db, it, nowMillis, localZone) }
        alarms.size
    }

    @Synchronized
    fun activeBetween(fromMillis: Long, toMillis: Long): List<TemporalRecord> =
        readableDatabase.rawQuery(
            """SELECT payload FROM objects
               WHERE retired=0 AND anchor_ms IS NOT NULL
                 AND anchor_ms <= ? AND COALESCE(span_end_ms, anchor_ms) >= ?
               ORDER BY anchor_ms ASC""",
            arrayOf(toMillis.toString(), fromMillis.toString())
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(decodeRecord(cursor.getString(0))) }
        }

    @Synchronized
    fun history(eventSeq: Long, limit: Int = 50): List<CommitRecord> =
        readableDatabase.rawQuery(
            """SELECT commit_id,event_seq,previous_revision,new_revision,actor,operation,created_ms
               FROM commits WHERE event_seq=? ORDER BY created_ms DESC LIMIT ?""",
            arrayOf(eventSeq.toString(), limit.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CommitRecord(
                            cursor.getString(0), cursor.getLong(1),
                            if (cursor.isNull(2)) null else cursor.getLong(2),
                            cursor.getLong(3), cursor.getString(4), cursor.getString(5), cursor.getLong(6)
                        )
                    )
                }
            }
        }

    data class CommitRecord(
        val commitId: String,
        val eventSeq: Long,
        val previousRevision: Long?,
        val newRevision: Long,
        val actor: String,
        val operation: String,
        val createdAtMillis: Long
    )

    @Synchronized
    fun identityCount(): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM identities", null).use {
            it.moveToFirst(); it.getLong(0)
        }

    /** Read-only row counts used to verify zero-write editor exits and recovery checkpoints. */
    @Synchronized
    internal fun draftCount(): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM drafts", null).use {
            it.moveToFirst(); it.getLong(0)
        }

    @Synchronized
    internal fun commitCount(): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM commits", null).use {
            it.moveToFirst(); it.getLong(0)
        }

    @Synchronized
    fun nextSequencePreview(): Long = metaLong(readableDatabase, "next_seq")

    @Synchronized
    internal fun nextQueueOrderPreview(): Long = metaLong(readableDatabase, "next_order")

    // ---------------------------------------------------------------- AI batch

    @Synchronized
    fun isLocked(): Boolean = isLocked(readableDatabase)
    private fun isLocked(db: SQLiteDatabase): Boolean = metaLong(db, "ai_locked") == 1L
    private fun currentEpoch(db: SQLiteDatabase): Long = metaLong(db, "ai_epoch")

    @Synchronized
    fun currentAiSession(): AiSession? {
        val session = readableDatabase.rawQuery(
            """SELECT session_id,lock_epoch,scope,allowed_ops,state,started_ms,updated_ms,report
               FROM ai_sessions ORDER BY started_ms DESC LIMIT 1""",
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.toAiSession()
        } ?: return null
        return session.copy(
            reviewedVersions = reviewedVersionsFor(readableDatabase, session.sessionId)
        )
    }

    private fun android.database.Cursor.toAiSession(): AiSession {
        val ops = getString(3).split(',').mapNotNull {
            runCatching { DraftOperation.valueOf(it) }.getOrNull()
        }.toSet()
        return AiSession(
            sessionId = getString(0),
            lockEpoch = getLong(1),
            scope = getString(2).split(',').mapNotNull(String::toLongOrNull).toSet(),
            allowedOperations = ops,
            state = runCatching { AiState.valueOf(getString(4)) }.getOrDefault(AiState.FAILED),
            startedAtMillis = getLong(5),
            updatedAtMillis = getLong(6),
            report = if (isNull(7)) null else getString(7)
        )
    }

    /**
     * Acquires the single-AI deck lock **for an exact reviewed snapshot**.
     *
     * `reviewed` is the map of `draftId -> draftVersion` the user actually saw. Everything is
     * checked inside one transaction: the card still exists, it is still actionable, its operation
     * is authorized, and its `draftVersion` still equals the reviewed one. Any edit, discard or
     * replacement between review and authorization therefore makes the call fail instead of
     * silently widening what the AI may commit.
     *
     * Only one batch can exist; the epoch fences writes authorized by an earlier batch.
     */
    @Synchronized
    fun beginAiBatch(
        reviewed: Map<Long, Long>,
        allowedOperations: Set<DraftOperation>,
        sessionId: String = UUID.randomUUID().toString()
    ): AiSession = tx { db ->
        require(reviewed.isNotEmpty()) { "请选择要交给 AI 的草稿" }
        require(allowedOperations.isNotEmpty()) { "请选择允许 AI 执行的操作" }
        check(!isLocked(db)) { "已有 AI 批次正在运行" }
        check(!hasHumanEditor(db)) { "A human editor is active; AI mutation cannot start" }
        val cards = reviewed.mapValues { (draftId, reviewedVersion) ->
            val card = readDraft(db, draftId)
            require(card != null && card.isActionable) {
                "批次范围内包含不存在或已处理的草稿 #$draftId"
            }
            require(card.operation in allowedOperations) {
                "草稿 #$draftId 的 ${card.operation.label} 操作未获得授权"
            }
            require(card.form.recurrenceEditScope == null && card.form.editOccurrenceKey == null) {
                "重复范围编辑必须从原事件继续，不能交给普通 AI 草稿批次提交"
            }
            require(card.draftVersion == reviewedVersion) {
                "草稿 #$draftId 在审阅后被修改（当前 v${card.draftVersion}，审阅 v$reviewedVersion），请重新审阅后再授权"
            }
            card
        }
        val epoch = currentEpoch(db) + 1
        db.execSQL("UPDATE meta SET ai_locked=1, ai_epoch=? WHERE id=1", arrayOf(epoch))
        val now = System.currentTimeMillis()
        db.insertOrThrow("ai_sessions", null, ContentValues().apply {
            put("session_id", sessionId)
            put("lock_epoch", epoch)
            put("scope", reviewed.keys.sorted().joinToString(","))
            put("allowed_ops", allowedOperations.map { it.name }.sorted().joinToString(","))
            put("state", AiState.RUNNING.name)
            put("owner_token", processToken)
            put("started_ms", now)
            put("updated_ms", now)
        })
        reviewed.forEach { (draftId, version) ->
            db.insertOrThrow("ai_session_drafts", null, ContentValues().apply {
                put("session_id", sessionId)
                put("draft_id", draftId)
                put("draft_version", version)
                put("operation", cards.getValue(draftId).operation.name)
            })
        }
        AiSession(
            sessionId = sessionId,
            lockEpoch = epoch,
            scope = reviewed.keys,
            allowedOperations = allowedOperations,
            state = AiState.RUNNING,
            startedAtMillis = now,
            updatedAtMillis = now,
            reviewedVersions = reviewed
        )
    }

    /** Reviewed versions recorded for a session, empty for sessions created by older builds. */
    private fun reviewedVersionsFor(db: SQLiteDatabase, sessionId: String): Map<Long, Long> =
        db.rawQuery(
            "SELECT draft_id,draft_version FROM ai_session_drafts WHERE session_id=?",
            arrayOf(sessionId)
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getLong(0), cursor.getLong(1))
            }
        }

    /** Re-reads the persisted session so callers cannot keep a stale in-memory snapshot. */
    @Synchronized
    fun refreshSession(sessionId: String): AiSession? {
        val session = readableDatabase.rawQuery(
            """SELECT session_id,lock_epoch,scope,allowed_ops,state,started_ms,updated_ms,report
               FROM ai_sessions WHERE session_id=?""",
            arrayOf(sessionId)
        ).use { if (it.moveToFirst()) it.toAiSession() else null } ?: return null
        return session.copy(
            reviewedVersions = reviewedVersionsFor(readableDatabase, sessionId)
        )
    }

    @Synchronized
    fun heartbeat(sessionId: String): Boolean = tx { db ->
        val updated = db.compileStatement(
            "UPDATE ai_sessions SET updated_ms=? WHERE session_id=? AND state IN ('RUNNING','CANCEL_REQUESTED')"
        ).apply {
            bindLong(1, System.currentTimeMillis())
            bindString(2, sessionId)
        }.executeUpdateDelete()
        updated == 1
    }

    @Synchronized
    fun requestAiCancel(sessionId: String): Boolean = tx { db ->
        val updated = db.compileStatement(
            "UPDATE ai_sessions SET state='CANCEL_REQUESTED', updated_ms=? WHERE session_id=? AND state='RUNNING'"
        ).apply {
            bindLong(1, System.currentTimeMillis())
            bindString(2, sessionId)
        }.executeUpdateDelete()
        updated == 1
    }

    /**
     * Terminal state for a batch. The epoch is advanced **before** the lock opens, so any late
     * network response still carrying the old epoch is rejected even in the same millisecond.
     */
    @Synchronized
    fun finishAiBatch(
        sessionId: String,
        state: AiState = AiState.COMPLETED,
        report: JSONObject? = null
    ): Boolean = tx { db ->
        var persistedState = ""
        db.rawQuery("SELECT lock_epoch,owner_token,state FROM ai_sessions WHERE session_id=?",
            arrayOf(sessionId)).use { cursor ->
            if (!cursor.moveToFirst()) return@tx false
            val epoch = cursor.getLong(0)
            val owner = cursor.getString(1)
            persistedState = cursor.getString(2)
            if (owner != processToken) return@tx false
            if (epoch != currentEpoch(db)) return@tx false
        }
        // Already terminal: finishing twice would bump the epoch again and mask a bug.
        if (persistedState in setOf(
                AiState.COMPLETED.name, AiState.CANCELLED.name,
                AiState.FAILED.name, AiState.INTERRUPTED.name
            )
        ) {
            return@tx false
        }
        // A cancelled batch cannot be reported as a clean completion.
        val effective = if (persistedState == AiState.CANCEL_REQUESTED.name && state == AiState.COMPLETED) {
            AiState.CANCELLED
        } else {
            state
        }
        val now = System.currentTimeMillis()
        db.execSQL(
            "UPDATE ai_sessions SET state=?, updated_ms=?, report=? WHERE session_id=?",
            arrayOf<Any?>(effective.name, now, report?.toString(), sessionId)
        )
        db.execSQL("UPDATE meta SET ai_locked=0, ai_epoch=? WHERE id=1", arrayOf(currentEpoch(db) + 1))
        true
    }

    /**
     * Startup/runtime recovery. A session is an orphan when it is owned by another process, or when
     * its heartbeat is older than [staleAfterMillis] (a cancelled coroutine, a dead worker or a
     * frozen process). Recovery is explicit rather than a constructor side effect, and it always
     * moves the epoch so late responses cannot continue.
     */
    @Synchronized
    fun recoverInterruptedAi(staleAfterMillis: Long = DEFAULT_STALE_MS): Boolean = tx { db ->
        if (!isLocked(db)) return@tx false
        val now = System.currentTimeMillis()
        val orphan = db.rawQuery(
            """SELECT session_id FROM ai_sessions
               WHERE state IN ('REQUESTED','RUNNING','CANCEL_REQUESTED')
                 AND (owner_token<>? OR updated_ms< ?)""",
            arrayOf(processToken, (now - staleAfterMillis).toString())
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        if (orphan.isEmpty()) return@tx false
        orphan.forEach { id ->
            db.execSQL(
                "UPDATE ai_sessions SET state='INTERRUPTED', updated_ms=? WHERE session_id=?",
                arrayOf(now, id)
            )
        }
        db.execSQL("UPDATE meta SET ai_locked=0, ai_epoch=? WHERE id=1", arrayOf(currentEpoch(db) + 1))
        true
    }

    /** Explicit user escape hatch for a stuck batch; fences the epoch before unlocking. */
    @Synchronized
    fun releaseStuckAiBatch(): Boolean = tx { db ->
        if (!isLocked(db)) return@tx false
        val now = System.currentTimeMillis()
        db.execSQL(
            "UPDATE ai_sessions SET state='FAILED', updated_ms=?, report=? WHERE state IN ('REQUESTED','RUNNING','CANCEL_REQUESTED')",
            arrayOf<Any?>(now, "{\"released\":\"user\"}")
        )
        db.execSQL("UPDATE meta SET ai_locked=0, ai_epoch=? WHERE id=1", arrayOf(currentEpoch(db) + 1))
        true
    }

    /**
     * `null` means allowed. Returning a [CommitOutcome] keeps the call sites single-branch.
     * Order matters: lock -> session liveness -> epoch -> scope -> operation allow-list.
     */
    private fun authorize(db: SQLiteDatabase, session: AiSession?, draftId: Long?): CommitOutcome? {
        if (session == null) {
            return if (isLocked(db)) CommitOutcome.Locked else null
        }
        if (!isLocked(db)) return CommitOutcome.NotAuthorized
        return db.rawQuery(
            "SELECT state,lock_epoch,scope,allowed_ops,owner_token FROM ai_sessions WHERE session_id=?",
            arrayOf(session.sessionId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use CommitOutcome.NotAuthorized
            val state = runCatching { AiState.valueOf(cursor.getString(0)) }.getOrDefault(AiState.FAILED)
            val epoch = cursor.getLong(1)
            val scope = cursor.getString(2).split(',').mapNotNull(String::toLongOrNull).toSet()
            val ops = cursor.getString(3).split(',').mapNotNull {
                runCatching { DraftOperation.valueOf(it) }.getOrNull()
            }.toSet()
            val owner = cursor.getString(4)
            when {
                state != AiState.RUNNING -> CommitOutcome.NotAuthorized
                owner != processToken -> CommitOutcome.NotAuthorized
                epoch != currentEpoch(db) || epoch != session.lockEpoch -> CommitOutcome.NotAuthorized
                draftId == null -> null
                draftId !in scope -> CommitOutcome.NotAuthorized
                else -> {
                    val card = readDraft(db, draftId)
                    // The reviewed version is part of the authorization. A card edited after the
                    // user confirmed the batch cannot be committed under that decision.
                    val reviewed = db.rawQuery(
                        "SELECT draft_version,operation FROM ai_session_drafts WHERE session_id=? AND draft_id=?",
                        arrayOf(session.sessionId, draftId.toString())
                    ).use { cursor ->
                        if (!cursor.moveToFirst()) null
                        else cursor.getLong(0) to cursor.getString(1)
                    }
                    when {
                        card == null -> CommitOutcome.Invalid("草稿不存在")
                        reviewed == null -> CommitOutcome.NotAuthorized
                        card.draftVersion != reviewed.first -> CommitOutcome.NotAuthorized
                        card.operation.name != reviewed.second -> CommitOutcome.NotAuthorized
                        card.operation !in ops -> CommitOutcome.NotAuthorized
                        else -> null
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- statistics

    /** Truthful counters for the completion card, derived from durable records. */
    @Synchronized
    fun stats(sinceMillis: Long, actor: String? = null): DeckStats {
        val db = readableDatabase
        // Only card-driven commits count: imports and restorations are not "published" cards and
        // would inflate a completion claim (G10-08 wants accurate numbers, not a score).
        val published = db.rawQuery(
            "SELECT COUNT(*) FROM commits WHERE created_ms>=? AND draft_id IS NOT NULL" +
                if (actor != null) " AND actor=?" else "",
            if (actor != null) arrayOf(sinceMillis.toString(), actor)
            else arrayOf(sinceMillis.toString())
        ).use { it.moveToFirst(); it.getInt(0) }
        val discarded = db.rawQuery(
            "SELECT COUNT(*) FROM drafts WHERE status='DISCARDED' AND updated_ms>=?",
            arrayOf(sinceMillis.toString())
        ).use { it.moveToFirst(); it.getInt(0) }
        val conflicts = db.rawQuery(
            "SELECT COUNT(*) FROM drafts WHERE status='CONFLICT'", null
        ).use { it.moveToFirst(); it.getInt(0) }
        return DeckStats(published, discarded, conflicts)
    }

    @Synchronized
    fun pendingCount(): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM drafts WHERE status IN ('PENDING','CONFLICT','INVALID')", null
        ).use { it.moveToFirst(); it.getInt(0) }

    // ---------------------------------------------------------------- scheduling intent

    /**
     * Durable desired schedule for the next occurrence. Android side effects are reconciled
     * later; this row is what makes "saved" vs "scheduled" vs "blocked" honest.
     */
    private fun writeDesiredIntent(
        db: SQLiteDatabase,
        record: TemporalRecord?,
        now: Long,
        localZone: ZoneId = ZoneId.systemDefault()
    ) {
        if (record == null) return
        cancelIntents(db, record.eventSeq, now)
        if (!record.enabled) return
        // The user chose date-only deadline 00:00 notifications. Date precision is preserved:
        // midnight is a reminder POLICY, never an invented event deadline time.
        val occurrence = RecurrenceEngine.nextOccurrence(record, now, localZone) ?: return
        val triggerInstant = ReminderPolicy.instant(record, occurrence.startMillis) ?: return
        val requirement = if (record.reminderMethod == ReminderMethod.ALARM) {
            TriggerRequirement.EXACT
        } else {
            TriggerRequirement.INEXACT
        }
        db.insertWithOnConflict("schedule_intents", null, ContentValues().apply {
            put("event_seq", record.eventSeq)
            put("trigger_id", ReminderPolicy.PRIMARY_TRIGGER_ID)
            put("revision", record.revision)
            put("occurrence_key", occurrence.key)
            put("instant_ms", triggerInstant)
            put("requirement", requirement.name)
            put("desired_state", DesiredSchedule.PENDING.name)
            putNull("android_hint")
            put("updated_ms", now)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun cancelIntents(db: SQLiteDatabase, eventSeq: Long, now: Long) {
        db.execSQL(
            "UPDATE schedule_intents SET desired_state='CANCELLED', updated_ms=? WHERE event_seq=?",
            arrayOf(now, eventSeq)
        )
    }

    @Synchronized
    fun dueIntents(limit: Int = 400): List<ScheduleIntent> =
        readableDatabase.rawQuery(
            """SELECT event_seq,trigger_id,revision,occurrence_key,instant_ms,requirement,desired_state,android_hint
               FROM schedule_intents WHERE desired_state IN ('PENDING','SCHEDULED')
               ORDER BY instant_ms ASC LIMIT ?""",
            arrayOf(limit.toString())
        ).use { cursor -> readIntents(cursor) }

    /**
     * Intents eligible for a reconciliation pass. Each state group gets its own bounded batch so
     * unrecoverable BLOCKED rows cannot starve PENDING or SCHEDULED intents. BLOCKED rows are
     * included so a later permission or system-state change can recover them without an edit.
     */
    @Synchronized
    fun reconciliationIntents(limit: Int = 400): List<ScheduleIntent> {
        require(limit > 0) { "limit must be positive" }
        fun readStates(states: String): List<ScheduleIntent> = readableDatabase.rawQuery(
            """SELECT event_seq,trigger_id,revision,occurrence_key,instant_ms,requirement,desired_state,android_hint
               FROM schedule_intents WHERE desired_state IN ($states)
               ORDER BY instant_ms ASC,event_seq ASC,trigger_id ASC LIMIT ?""",
            arrayOf(limit.toString())
        ).use { cursor -> readIntents(cursor) }

        return readStates("'PENDING','SCHEDULED'") + readStates("'BLOCKED'")
    }

    @Synchronized
    fun intentsInState(state: DesiredSchedule, limit: Int = 100): List<ScheduleIntent> =
        readableDatabase.rawQuery(
            """SELECT event_seq,trigger_id,revision,occurrence_key,instant_ms,requirement,desired_state,android_hint
               FROM schedule_intents WHERE desired_state=? ORDER BY instant_ms ASC LIMIT ?""",
            arrayOf(state.name, limit.toString())
        ).use { cursor -> readIntents(cursor) }

    @Synchronized
    fun deleteIntent(eventSeq: Long, triggerId: String) {
        writableDatabase.delete(
            "schedule_intents", "event_seq=? AND trigger_id=?",
            arrayOf(eventSeq.toString(), triggerId)
        )
    }

    private fun readIntents(cursor: android.database.Cursor): List<ScheduleIntent> = buildList {
        while (cursor.moveToNext()) {
            add(
                ScheduleIntent(
                    eventSeq = cursor.getLong(0),
                    triggerId = cursor.getString(1),
                    revision = cursor.getLong(2),
                    occurrenceKey = cursor.getString(3),
                    instantMillis = cursor.getLong(4),
                    requirement = runCatching {
                        TriggerRequirement.valueOf(cursor.getString(5))
                    }.getOrDefault(TriggerRequirement.INEXACT),
                    desired = runCatching {
                        DesiredSchedule.valueOf(cursor.getString(6))
                    }.getOrDefault(DesiredSchedule.PENDING),
                    androidHint = if (cursor.isNull(7)) null else cursor.getString(7)
                )
            )
        }
    }

    @Synchronized
    fun intentFor(eventSeq: Long, triggerId: String = ReminderPolicy.PRIMARY_TRIGGER_ID): ScheduleIntent? =
        readableDatabase.rawQuery(
            """SELECT event_seq,trigger_id,revision,occurrence_key,instant_ms,requirement,desired_state,android_hint
               FROM schedule_intents WHERE event_seq=? AND trigger_id=?""",
            arrayOf(eventSeq.toString(), triggerId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else ScheduleIntent(
                cursor.getLong(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3),
                cursor.getLong(4),
                runCatching { TriggerRequirement.valueOf(cursor.getString(5)) }
                    .getOrDefault(TriggerRequirement.INEXACT),
                runCatching { DesiredSchedule.valueOf(cursor.getString(6)) }
                    .getOrDefault(DesiredSchedule.PENDING),
                if (cursor.isNull(7)) null else cursor.getString(7)
            )
        }

    @Synchronized
    fun markIntent(
        eventSeq: Long,
        triggerId: String,
        state: DesiredSchedule,
        hint: String? = null,
        revision: Long? = null,
        occurrenceKey: String? = null
    ): Boolean {
        val sql = buildString {
            append("UPDATE schedule_intents SET desired_state=?, android_hint=?, updated_ms=? ")
            append("WHERE event_seq=? AND trigger_id=?")
            if (revision != null) append(" AND revision=?")
            if (occurrenceKey != null) append(" AND occurrence_key=?")
        }
        val args = mutableListOf<Any?>(
            state.name, hint, System.currentTimeMillis(), eventSeq, triggerId
        )
        if (revision != null) args.add(revision)
        if (occurrenceKey != null) args.add(occurrenceKey)
        val updated = writableDatabase.compileStatement(sql).apply {
            args.forEachIndexed { index, value ->
                val slot = index + 1
                when (value) {
                    null -> bindNull(slot)
                    is String -> bindString(slot, value)
                    is Long -> bindLong(slot, value)
                    is Int -> bindLong(slot, value.toLong())
                    else -> bindString(slot, value.toString())
                }
            }
        }.executeUpdateDelete()
        // Zero rows means the row was replaced by a newer revision; the caller must not claim it
        // scheduled (or cancelled) the current one.
        return updated == 1
    }

    /** Advances a fired occurrence to the next slot without touching the committed snapshot. */
    @Synchronized
    fun advanceIntentAfterFiring(eventSeq: Long, firedKey: String) {
        val record = get(eventSeq)
        if (record == null) {
            cancelIntents(writableDatabase, eventSeq, System.currentTimeMillis())
            return
        }
        val next = RecurrenceEngine.nextOccurrence(record, System.currentTimeMillis() + 1_000)
        tx { db ->
            if (next == null || next.key == firedKey) {
                db.execSQL(
                    "UPDATE schedule_intents SET desired_state='CANCELLED', updated_ms=? WHERE event_seq=?",
                    arrayOf(System.currentTimeMillis(), eventSeq)
                )
            } else {
                writeDesiredIntent(db, record, System.currentTimeMillis())
            }
        }
    }

    @Synchronized
    fun recordExecution(
        eventSeq: Long,
        revision: Long,
        occurrenceKey: String,
        triggerId: String,
        result: String,
        detail: String = ""
    ) {
        writableDatabase.insertOrThrow("executions", null, ContentValues().apply {
            put("execution_id", UUID.randomUUID().toString())
            put("event_seq", eventSeq)
            put("revision", revision)
            put("occurrence_key", occurrenceKey)
            put("trigger_id", triggerId)
            put("result", result)
            put("detail", detail)
            put("at_ms", System.currentTimeMillis())
        })
    }

    @Synchronized
    fun lastExecution(eventSeq: Long): String? =
        readableDatabase.rawQuery(
            "SELECT result FROM executions WHERE event_seq=? ORDER BY at_ms DESC LIMIT 1",
            arrayOf(eventSeq.toString())
        ).use { if (it.moveToFirst()) it.getString(0) else null }

    // ---------------------------------------------------------------- structured import

    /**
     * Direct publish for strictly validated structured data. Repeat imports are idempotent via
     * (source, externalId). Imported alarms start disabled so an OEM clock cannot double-ring.
     */
    @Synchronized
    fun importStructured(
        record: TemporalRecord,
        externalId: String,
        batchId: String,
        source: String = record.source,
        alarmNeedsHandoff: Boolean = true
    ): ImportOutcome = tx { db ->
        require(externalId.isNotBlank()) { "外部记录缺少稳定标识" }
        val existing = db.rawQuery(
            "SELECT event_seq FROM source_links WHERE source=? AND external_id=?",
            arrayOf(source, externalId)
        ).use { if (it.moveToFirst()) it.getLong(0) else null }
        if (existing != null) {
            db.execSQL(
                "UPDATE migrations SET skipped=skipped+1 WHERE batch_id=?",
                arrayOf(batchId)
            )
            return@tx ImportOutcome.AlreadyImported(existing)
        }
        if (isLocked(db)) {
            return@tx ImportOutcome.Rejected("AI 批次运行中，暂不接受结构化导入")
        }
        val now = System.currentTimeMillis()
        val prefersDisabled = record.kind == TemporalKind.ALARM && alarmNeedsHandoff
        // Validate BEFORE allocating: a rejected import must not burn a permanent event number.
        val candidate = record.copy(
            eventSeq = 0L,
            revision = 1L,
            enabled = record.enabled && !prefersDisabled,
            source = source,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        candidate.validate()?.let { return@tx ImportOutcome.Rejected(it) }
        val seq = allocateIdentity(db, source)
        val stored = candidate.copy(eventSeq = seq)
        upsertObject(db, stored)
        db.insertOrThrow("source_links", null, ContentValues().apply {
            put("source", source)
            put("external_id", externalId)
            put("event_seq", seq)
        })
        db.insertOrThrow("commits", null, ContentValues().apply {
            put("commit_id", UUID.randomUUID().toString())
            putNull("draft_id") // structured imports own no card
            put("event_seq", seq)
            putNull("previous_revision")
            put("new_revision", 1L)
            put("actor", "import:$source")
            put("operation", "IMPORT")
            put("created_ms", now)
        })
        db.execSQL(
            "UPDATE migrations SET imported=imported+1 WHERE batch_id=?",
            arrayOf(batchId)
        )
        writeDesiredIntent(db, stored, now)
        ImportOutcome.Imported(seq, candidate.enabled)
    }

    sealed interface ImportOutcome {
        data class Imported(val eventSeq: Long, val enabled: Boolean) : ImportOutcome
        data class AlreadyImported(val eventSeq: Long) : ImportOutcome
        data class Rejected(val reason: String) : ImportOutcome
    }

    @Synchronized
    fun beginMigration(source: String): String {
        val batchId = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("migrations", null, ContentValues().apply {
            put("batch_id", batchId)
            put("source", source)
            put("started_ms", System.currentTimeMillis())
            put("warnings", "[]")
        })
        return batchId
    }

    @Synchronized
    fun finishMigration(batchId: String, warnings: List<String> = emptyList()) {
        writableDatabase.execSQL(
            "UPDATE migrations SET finished_ms=?, warnings=? WHERE batch_id=?",
            arrayOf<Any?>(
                System.currentTimeMillis(),
                JSONArray(warnings).toString(),
                batchId
            )
        )
    }

    @Synchronized
    fun migrationSummary(batchId: String): Triple<Int, Int, List<String>>? =
        readableDatabase.rawQuery(
            "SELECT imported,skipped,warnings FROM migrations WHERE batch_id=?", arrayOf(batchId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val warnings = runCatching {
                val array = JSONArray(cursor.getString(2))
                (0 until array.length()).map { array.getString(it) }
            }.getOrDefault(emptyList())
            Triple(cursor.getInt(0), cursor.getInt(1), warnings)
        }

    @Synchronized
    fun sourceLink(source: String, externalId: String): Long? =
        readableDatabase.rawQuery(
            "SELECT event_seq FROM source_links WHERE source=? AND external_id=?",
            arrayOf(source, externalId)
        ).use { if (it.moveToFirst()) it.getLong(0) else null }

    /**
     * BackupEnvelope v1 from one SQLite snapshot. It deliberately excludes AI authorization,
     * Android PendingIntent handles and API keys. The payload hash is created only after every
     * logical table and both high-water marks were read in the same transaction.
     */
    @Synchronized
    fun exportBackup(): JSONObject = readSnapshot { db ->
        val exportedAt = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("identities", JSONArray().apply {
                db.rawQuery(
                    "SELECT event_seq,allocated_ms,retired_ms,origin FROM identities ORDER BY event_seq", null
                ).use { cursor ->
                    while (cursor.moveToNext()) put(JSONObject().apply {
                        put("seq", cursor.getLong(0))
                        put("allocatedMs", cursor.getLong(1))
                        if (!cursor.isNull(2)) put("retiredMs", cursor.getLong(2))
                        put("origin", cursor.getString(3))
                    })
                }
            })
            put("objects", JSONArray().apply {
                db.rawQuery("SELECT payload,retired FROM objects ORDER BY event_seq", null).use { cursor ->
                    while (cursor.moveToNext()) put(JSONObject().apply {
                        put("payload", JSONObject(cursor.getString(0)))
                        put("retired", cursor.getInt(1))
                    })
                }
            })
            put("drafts", JSONArray().apply {
                db.rawQuery(
                    """SELECT draft_id,event_seq,queue_order,base_rev,op,payload,status,draft_version,created_ms,updated_ms
                       FROM drafts ORDER BY queue_order""", null
                ).use { cursor ->
                    while (cursor.moveToNext()) put(JSONObject().apply {
                        put("draftId", cursor.getLong(0))
                        put("eventSeq", cursor.getLong(1))
                        put("queueOrder", cursor.getLong(2))
                        if (cursor.isNull(3)) put("baseRev", JSONObject.NULL) else put("baseRev", cursor.getLong(3))
                        put("op", cursor.getString(4))
                        put("payload", JSONObject(cursor.getString(5)))
                        put("status", cursor.getString(6))
                        put("draftVersion", cursor.getLong(7))
                        put("createdMs", cursor.getLong(8))
                        put("updatedMs", cursor.getLong(9))
                    })
                }
            })
            put("commits", JSONArray().apply {
                db.rawQuery(
                    """SELECT commit_id,draft_id,event_seq,previous_revision,new_revision,actor,operation,created_ms
                       FROM commits ORDER BY created_ms,commit_id""", null
                ).use { cursor ->
                    while (cursor.moveToNext()) put(JSONObject().apply {
                        put("commitId", cursor.getString(0))
                        if (cursor.isNull(1)) put("draftId", JSONObject.NULL) else put("draftId", cursor.getLong(1))
                        put("eventSeq", cursor.getLong(2))
                        if (cursor.isNull(3)) put("previousRevision", JSONObject.NULL)
                        else put("previousRevision", cursor.getLong(3))
                        put("revision", cursor.getLong(4))
                        put("actor", cursor.getString(5))
                        put("operation", cursor.getString(6))
                        put("createdMs", cursor.getLong(7))
                    })
                }
            })
            put("sourceLinks", JSONArray().apply {
                db.rawQuery(
                    "SELECT source,external_id,event_seq FROM source_links ORDER BY source,external_id", null
                ).use { cursor ->
                    while (cursor.moveToNext()) put(JSONObject().apply {
                        put("source", cursor.getString(0))
                        put("externalId", cursor.getString(1))
                        put("eventSeq", cursor.getLong(2))
                    })
                }
            })
            put("migrations", JSONArray().apply {
                db.rawQuery(
                    """SELECT batch_id,source,started_ms,finished_ms,imported,skipped,warnings
                       FROM migrations ORDER BY started_ms,batch_id""", null
                ).use { cursor ->
                    while (cursor.moveToNext()) put(JSONObject().apply {
                        put("batchId", cursor.getString(0))
                        put("source", cursor.getString(1))
                        put("startedMs", cursor.getLong(2))
                        if (cursor.isNull(3)) put("finishedMs", JSONObject.NULL)
                        else put("finishedMs", cursor.getLong(3))
                        put("imported", cursor.getInt(4))
                        put("skipped", cursor.getInt(5))
                        put("warnings", cursor.getString(6))
                    })
                }
            })
            put(BackupEnvelope.AI_TASK_RECEIPTS, JSONArray().apply {
                db.rawQuery(
                    """SELECT task_id,task_revision,item_index,fingerprint,event_seq,draft_id,state,
                              idempotency_key,commit_id,created_ms,updated_ms
                       FROM ai_task_receipts ORDER BY task_id,task_revision,item_index""", null
                ).use { cursor ->
                    while (cursor.moveToNext()) put(JSONObject().apply {
                        put("taskId", cursor.getString(0))
                        put("taskRevision", cursor.getInt(1))
                        put("itemIndex", cursor.getInt(2))
                        put("fingerprint", cursor.getString(3))
                        put("eventSeq", cursor.getLong(4))
                        put("draftId", cursor.getLong(5))
                        put("state", cursor.getString(6))
                        put("idempotencyKey", cursor.getString(7))
                        if (cursor.isNull(8)) put("commitId", JSONObject.NULL)
                        else put("commitId", cursor.getString(8))
                        put("createdMs", cursor.getLong(9))
                        put("updatedMs", cursor.getLong(10))
                    })
                }
            })
            put("nextSeq", metaLong(db, "next_seq"))
            put("nextOrder", metaLong(db, "next_order"))
        }
        // The writer must never produce a file the same build would refuse to restore.
        (BackupEnvelope.TABLES + BackupEnvelope.OPTIONAL_TABLES).forEach { table ->
            val array = payload.optJSONArray(table) ?: return@forEach
            val rows = array.length()
            require(rows <= MAX_BACKUP_ROWS) {
                "数据量超过单次备份上限 $MAX_BACKUP_ROWS（$table=$rows）；当前版本不支持自动归档，" +
                    "请先导出并清理部分历史数据，或联系维护者处理"
            }
        }
        BackupEnvelope.create(
            payload = payload,
            databaseSchemaVersion = VERSION,
            appVersionCode = installedVersionCode(),
            exportedAtEpochMs = exportedAt,
            storeLineageId = metaString(db, "lineage_id")
        )
    }

    // ---------------------------------------------------------------- native backup

    data class RestoreReport(
        val identities: Int,
        val objects: Int,
        val drafts: Int,
        val commits: Int,
        val legacyUnverified: Boolean = false,
        val warnings: List<String> = emptyList()
    )

    /**
     * Restores a verified BackupEnvelope v1 (or an explicitly confirmed legacy flat v2 file) into an
     * EMPTY store only. The envelope is verified and the payload's referential integrity is checked
     * before a single row is written; the insert itself is one transaction, so an interrupted
     * restore rolls back to the empty store. AI authority and Android handles are never restored.
     */
    @Synchronized
    fun restoreBackup(input: JSONObject, allowLegacyV2: Boolean = false): RestoreReport =
        restoreBackupInternal(input, allowLegacyV2, afterPreflight = null)

    /** Deterministic race seam for the unit test of the transactional non-empty guard. */
    @Synchronized
    internal fun restoreBackupWithPreflightHookForTest(
        input: JSONObject,
        afterPreflight: () -> Unit
    ): RestoreReport = restoreBackupInternal(input, allowLegacyV2 = false, afterPreflight)

    private fun restoreBackupInternal(
        input: JSONObject,
        allowLegacyV2: Boolean,
        afterPreflight: (() -> Unit)?
    ): RestoreReport {
        // Cheap rejection first: never parse/verify a large envelope against a live user database.
        if (hasAnyUserData(readableDatabase)) throw NonEmptyRestoreTargetException()
        val verified = BackupEnvelope.verify(input, VERSION, allowLegacyV2)
        val payload = verified.payload
        validateBackupPayload(payload, verified.databaseSchemaVersion)
        afterPreflight?.invoke()
        return tx { db ->
            // Re-check inside the transaction: another connection may have written since preflight.
            if (hasAnyUserData(db)) throw NonEmptyRestoreTargetException()
            val identities = payload.getJSONArray("identities")
            for (index in 0 until identities.length()) {
                val item = identities.getJSONObject(index)
                db.insertOrThrow("identities", null, ContentValues().apply {
                    put("event_seq", item.getLong("seq"))
                    put("allocated_ms", item.optLong("allocatedMs", System.currentTimeMillis()))
                    if (item.has("retiredMs") && !item.isNull("retiredMs")) {
                        put("retired_ms", item.getLong("retiredMs"))
                    }
                    put("origin", item.optString("origin", "local"))
                })
            }

            val objects = payload.getJSONArray("objects")
            for (index in 0 until objects.length()) {
                val wrapper = objects.getJSONObject(index)
                val snapshot = wrapper.getJSONObject("payload")
                val record = decodeRecord(snapshot.toString())
                val retired = wrapper.optInt("retired", 0) == 1
                db.insertOrThrow("objects", null, ContentValues().apply {
                    put("event_seq", record.eventSeq)
                    put("revision", record.revision)
                    put("kind", record.kind.name)
                    put("anchor_ms", record.anchorMillis())
                    put("span_end_ms", record.spanEndMillis())
                    put("payload", snapshot.toString())
                    put("retired", if (retired) 1 else 0)
                    put("created_ms", record.createdAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis())
                    put("updated_ms", record.updatedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis())
                })
            }

            val drafts = payload.getJSONArray("drafts")
            // Normalize old backups before insertion into v4's active-draft unique index.
            val survivors = mutableMapOf<Long, Pair<Long, List<Long>>>()
            for (index in 0 until drafts.length()) {
                val item = drafts.getJSONObject(index)
                if (item.optString("status", "PENDING") !in
                    setOf("PENDING", "CONFLICT", "INVALID")) continue
                val seq = item.getLong("eventSeq")
                val rank = listOf(item.optLong("updatedMs", 0L),
                    item.optLong("draftVersion", 0L), item.getLong("queueOrder"),
                    item.getLong("draftId"))
                val previous = survivors[seq]
                val newer = previous == null || run {
                    var compare = 0
                    for (i in rank.indices) {
                        if (rank[i] != previous.second[i]) {
                            compare = rank[i].compareTo(previous.second[i])
                            break
                        }
                    }
                    compare > 0
                }
                if (newer) survivors[seq] = item.getLong("draftId") to rank
            }
            var deduplicated = 0
            for (index in 0 until drafts.length()) {
                val item = drafts.getJSONObject(index)
                val status = item.optString("status", "PENDING")
                val normalized = if (status in setOf("PENDING", "CONFLICT", "INVALID")
                    && survivors[item.getLong("eventSeq")]?.first != item.getLong("draftId")) {
                    deduplicated++
                    "DISCARDED"
                } else status
                db.insertOrThrow("drafts", null, ContentValues().apply {
                    put("draft_id", item.getLong("draftId"))
                    put("event_seq", item.getLong("eventSeq"))
                    put("queue_order", item.getLong("queueOrder"))
                    if (item.isNull("baseRev")) putNull("base_rev") else put("base_rev", item.getLong("baseRev"))
                    put("op", item.getString("op"))
                    put("payload", item.getJSONObject("payload").toString())
                    put("status", normalized)
                    put("draft_version", item.optLong("draftVersion", 0L))
                    put("created_ms", item.optLong("createdMs", System.currentTimeMillis()))
                    put("updated_ms", item.optLong("updatedMs", System.currentTimeMillis()))
                })
                if (normalized != status) {
                    db.insertOrThrow("draft_migration_audit", null, ContentValues().apply {
                        put("draft_id", item.getLong("draftId")); put("old_status", status)
                        put("reason", "v4-backup-duplicate-active-draft")
                        put("migrated_ms", System.currentTimeMillis())
                    })
                }
            }

            val commits = payload.getJSONArray("commits")
            for (index in 0 until commits.length()) {
                val item = commits.getJSONObject(index)
                db.insertOrThrow("commits", null, ContentValues().apply {
                    put("commit_id", item.getString("commitId"))
                    if (!item.has("draftId") || item.isNull("draftId")) putNull("draft_id")
                    else put("draft_id", item.getLong("draftId"))
                    put("event_seq", item.getLong("eventSeq"))
                    if (!item.has("previousRevision") || item.isNull("previousRevision")) {
                        putNull("previous_revision")
                    } else {
                        put("previous_revision", item.getLong("previousRevision"))
                    }
                    put("new_revision", item.getLong("revision"))
                    put("actor", item.optString("actor", "restore"))
                    put("operation", item.optString("operation", "RESTORE"))
                    put("created_ms", item.optLong("createdMs", System.currentTimeMillis()))
                })
            }

            payload.optJSONArray(BackupEnvelope.AI_TASK_RECEIPTS)?.let { receipts ->
                for (index in 0 until receipts.length()) {
                    val item = receipts.getJSONObject(index)
                    db.insertOrThrow("ai_task_receipts", null, ContentValues().apply {
                        put("task_id", item.getString("taskId"))
                        put("task_revision", item.getInt("taskRevision"))
                        put("item_index", item.getInt("itemIndex"))
                        put("fingerprint", item.getString("fingerprint"))
                        put("event_seq", item.getLong("eventSeq"))
                        put("draft_id", item.getLong("draftId"))
                        put("state", item.getString("state"))
                        put("idempotency_key", item.getString("idempotencyKey"))
                        if (item.has("commitId") && !item.isNull("commitId")) {
                            put("commit_id", item.getString("commitId"))
                        } else putNull("commit_id")
                        put("created_ms", item.optLong("createdMs", System.currentTimeMillis()))
                        put("updated_ms", item.optLong("updatedMs", System.currentTimeMillis()))
                    })
                }
            }

            val links = payload.getJSONArray("sourceLinks")
            for (index in 0 until links.length()) {
                val item = links.getJSONObject(index)
                db.insertOrThrow("source_links", null, ContentValues().apply {
                    put("source", item.getString("source"))
                    put("external_id", item.getString("externalId"))
                    put("event_seq", item.getLong("eventSeq"))
                })
            }

            val migrations = payload.getJSONArray("migrations")
            for (index in 0 until migrations.length()) {
                val item = migrations.getJSONObject(index)
                db.insertOrThrow("migrations", null, ContentValues().apply {
                    put("batch_id", item.getString("batchId"))
                    put("source", item.getString("source"))
                    put("started_ms", item.optLong("startedMs", System.currentTimeMillis()))
                    if (item.has("finishedMs") && !item.isNull("finishedMs")) {
                        put("finished_ms", item.getLong("finishedMs"))
                    } else {
                        putNull("finished_ms")
                    }
                    put("imported", item.optInt("imported", 0))
                    put("skipped", item.optInt("skipped", 0))
                    put("warnings", item.optString("warnings", "[]"))
                })
            }

            // Execution history belongs to the old device: never import it, so a restored alarm is
            // not displayed as "already fired/scheduled" on this device.
            db.execSQL("DELETE FROM executions")

            // High-water marks come from the verified payload; they are never recomputed downward.
            db.execSQL(
                """UPDATE meta
                   SET next_seq=?, next_order=?, lineage_id=?, ai_locked=0, ai_epoch=ai_epoch+1
                   WHERE id=1""",
                arrayOf(
                    payload.getLong("nextSeq"),
                    payload.getLong("nextOrder"),
                    verified.storeLineageId
                )
            )
            // Android authority is rebuilt, never restored: no AI session, no stale PendingIntent.
            db.execSQL("DELETE FROM schedule_intents")
            db.execSQL("DELETE FROM ai_sessions")
            db.execSQL("DELETE FROM ai_session_drafts")
            db.rawQuery("SELECT payload FROM objects WHERE retired=0", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val restored = decodeRecord(cursor.getString(0))
                    if (restored.enabled) writeDesiredIntent(db, restored, System.currentTimeMillis())
                }
            }

            RestoreReport(
                identities = identities.length(),
                objects = objects.length(),
                drafts = drafts.length(),
                commits = commits.length(),
                legacyUnverified = verified.legacyUnverified,
                warnings = verified.warnings + if (deduplicated > 0)
                    listOf("$deduplicated duplicate active draft(s) retained as discarded history")
                    else emptyList()
            )
        }
    }

    /** A restore target must have no business row, no high-water movement and no AI authority. */
    private fun hasAnyUserData(db: SQLiteDatabase): Boolean {
        val tables = listOf(
            "identities", "objects", "drafts", "commits", "source_links",
            "migrations", "schedule_intents", "executions", "ai_sessions", "ai_session_drafts",
            "ai_task_receipts"
        )
        tables.forEach { table ->
            val count = db.rawQuery("SELECT COUNT(*) FROM $table", null).use {
                it.moveToFirst()
                it.getLong(0)
            }
            if (count > 0) return true
        }
        return metaLong(db, "next_seq") != 1L || metaLong(db, "next_order") != 1L
    }

    /**
     * Pre-flight validation. It runs before the write transaction, so a corrupted or inconsistent
     * envelope is rejected without touching the destination database.
     */
    private fun validateBackupPayload(payload: JSONObject, backupSchemaVersion: Int) {
        require(!payload.has("ai_sessions") && !payload.has("ai_session_drafts")) {
            "备份包含不应导入的 AI 授权数据"
        }
        val keys = BackupEnvelope.TABLES
        keys.forEach { key ->
            val array = payload.optJSONArray(key)
                ?: throw IllegalArgumentException("备份缺少 $key 数组")
            require(array.length() <= MAX_BACKUP_ROWS) { "备份 $key 数量超过上限 $MAX_BACKUP_ROWS" }
        }
        val receiptArray = payload.optJSONArray(BackupEnvelope.AI_TASK_RECEIPTS)
        if (backupSchemaVersion >= 5) {
            require(receiptArray != null) { "schema v$backupSchemaVersion 备份缺少 AI 任务收据" }
        }
        if (receiptArray != null) {
            require(receiptArray.length() <= MAX_BACKUP_ROWS) {
                "备份 ${BackupEnvelope.AI_TASK_RECEIPTS} 数量超过上限 $MAX_BACKUP_ROWS"
            }
        }
        payload.optJSONArray("executions")?.let {
            // Older v1 files may still carry this array; it is ignored on purpose, not restored.
            require(it.length() <= MAX_BACKUP_ROWS) { "备份 executions 数量超过上限 $MAX_BACKUP_ROWS" }
        }

        val seqs = HashSet<Long>()
        val identities = payload.getJSONArray("identities")
        for (index in 0 until identities.length()) {
            val seq = identities.getJSONObject(index).getLong("seq")
            require(seq > 0 && seqs.add(seq)) { "备份身份序号非法或重复：$seq" }
        }

        val objectSeqs = HashSet<Long>()
        val objects = payload.getJSONArray("objects")
        for (index in 0 until objects.length()) {
            val record = decodeRecord(objects.getJSONObject(index).getJSONObject("payload").toString())
            require(record.eventSeq in seqs) { "对象 #${record.eventSeq} 缺少身份记录" }
            require(objectSeqs.add(record.eventSeq)) { "对象 #${record.eventSeq} 重复" }
        }

        val draftIds = HashSet<Long>()
        val orders = HashSet<Long>()
        val drafts = payload.getJSONArray("drafts")
        for (index in 0 until drafts.length()) {
            val item = drafts.getJSONObject(index)
            val draftId = item.getLong("draftId")
            val order = item.getLong("queueOrder")
            require(draftId > 0 && draftIds.add(draftId)) { "草稿 ID 非法或重复：$draftId" }
            require(order > 0 && orders.add(order)) { "草稿 queueOrder 非法或重复：$order" }
            require(item.getLong("eventSeq") in seqs) { "草稿 #$draftId 引用了不存在的身份" }
            require(runCatching { DraftOperation.valueOf(item.getString("op")) }.isSuccess) {
                "草稿 #$draftId 的操作类型无效"
            }
            require(runCatching { DraftStatus.valueOf(item.getString("status")) }.isSuccess) {
                "草稿 #$draftId 的状态无效"
            }
            // Decoding the form here rejects malformed structured fields before any write.
            decodeForm(item.getJSONObject("payload").toString())
        }

        val commitIds = HashSet<String>()
        val commits = payload.getJSONArray("commits")
        val commitsById = HashMap<String, JSONObject>()
        for (index in 0 until commits.length()) {
            val item = commits.getJSONObject(index)
            val commitId = item.getString("commitId")
            require(commitId.isNotBlank() && commitIds.add(commitId)) { "提交 ID 重复或为空：$commitId" }
            commitsById[commitId] = item
            require(item.getLong("eventSeq") in seqs) { "提交 $commitId 引用了不存在的身份" }
            require(item.getLong("revision") >= 1L) { "提交 $commitId 的修订号非法" }
            if (item.has("draftId") && !item.isNull("draftId")) {
                require(item.getLong("draftId") in draftIds) { "提交 $commitId 引用了不存在的草稿" }
            }
        }

        if (receiptArray != null) {
            val draftsById = (0 until drafts.length()).associate { index ->
                val item = drafts.getJSONObject(index)
                item.getLong("draftId") to item
            }
            val receiptKeys = HashSet<Triple<String, Int, Int>>()
            val receiptDraftIds = HashSet<Long>()
            val idempotencyKeys = HashSet<String>()
            for (index in 0 until receiptArray.length()) {
                val receipt = receiptArray.getJSONObject(index)
                val taskId = receipt.getString("taskId")
                val taskRevision = receipt.getInt("taskRevision")
                val itemIndex = receipt.getInt("itemIndex")
                val eventSeq = receipt.getLong("eventSeq")
                val draftId = receipt.getLong("draftId")
                val state = receipt.getString("state")
                val idempotencyKey = receipt.getString("idempotencyKey")
                val key = Triple(taskId, taskRevision, itemIndex)
                require(taskId.matches(Regex("[A-Za-z0-9_-]{1,100}")) && taskRevision > 0 &&
                    itemIndex in 0..99 && receiptKeys.add(key)) {
                    "AI 任务收据标识无效或重复"
                }
                require(receipt.optString("fingerprint").matches(Regex("[0-9a-f]{64}"))) {
                    "AI 任务收据 fingerprint 无效"
                }
                require(idempotencyKey == "ai-task-v1/$taskId/$taskRevision/$itemIndex" &&
                    idempotencyKeys.add(idempotencyKey)) {
                    "AI 任务收据 idempotencyKey 无效或重复"
                }
                require(eventSeq in seqs && receiptDraftIds.add(draftId)) {
                    "AI 任务收据引用的身份或草稿重复/无效"
                }
                val draft = draftsById[draftId]
                    ?: throw IllegalArgumentException("AI 任务收据引用的草稿不存在")
                require(draft.getLong("eventSeq") == eventSeq) {
                    "AI 任务收据的事件序号与草稿不匹配"
                }
                when (state) {
                    "PENDING" -> require(draft.getString("status") in
                        setOf("PENDING", "CONFLICT", "INVALID")) {
                        "待处理 AI 任务收据引用了已结束草稿"
                    }
                    "DISCARDED" -> require(draft.getString("status") == "DISCARDED" &&
                        (!receipt.has("commitId") || receipt.isNull("commitId"))) {
                        "已拒绝 AI 任务收据状态与草稿不一致"
                    }
                    "COMMITTED" -> {
                        val commitId = receipt.optString("commitId")
                        val commit = commitsById[commitId]
                        require(draft.getString("status") == "COMMITTED" && commit != null &&
                            !commit.isNull("draftId") && commit.getLong("draftId") == draftId &&
                            commit.getLong("eventSeq") == eventSeq) {
                            "已提交 AI 任务收据缺少对应提交记录"
                        }
                    }
                    else -> throw IllegalArgumentException("AI 任务收据状态无效")
                }
            }
        }

        val links = payload.getJSONArray("sourceLinks")
        for (index in 0 until links.length()) {
            val item = links.getJSONObject(index)
            require(item.getLong("eventSeq") in seqs) { "来源链接引用了不存在的身份" }
        }

        val maxSeq = seqs.maxOrNull() ?: 0L
        val maxOrder = orders.maxOrNull() ?: 0L
        val restoredNextSeq = payload.optLong("nextSeq", -1L)
        val restoredNextOrder = payload.optLong("nextOrder", -1L)
        require(restoredNextSeq in (maxSeq + 1)..MAX_WATERMARK) {
            "备份 nextSeq 低于已分配序号或超出合理上限，可能已损坏"
        }
        require(restoredNextOrder in (maxOrder + 1)..MAX_WATERMARK) {
            "备份 nextOrder 低于草稿队列高水位或超出合理上限，可能已损坏"
        }
    }

    companion object {
        const val DATABASE_NAME = "powercal-v2.db"
        const val VERSION = 5

        /** Defensive bound for restore input; a personal calendar stays far below this. */
        const val MAX_BACKUP_ROWS = 200_000

        /** Sanity ceiling for restored counters: far above any real calendar, below overflow. */
        private const val MAX_WATERMARK = 1L shl 62

        /** A batch whose heartbeat is older than this cannot still be running. */
        const val DEFAULT_STALE_MS = 10 * 60_000L

        /** One token per OS process; every PowerStore instance in the process shares it. */
        private val PROCESS_TOKEN: String = UUID.randomUUID().toString()
    }
}
