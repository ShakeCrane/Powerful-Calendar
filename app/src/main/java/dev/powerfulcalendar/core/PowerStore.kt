package dev.powerfulcalendar.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
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
 *  - draftId/queueOrder are independent of eventSeq; one event may own many cards (I-02).
 *  - exactly one current snapshot per eventSeq, whole-object replacement only (I-03/I-04).
 *  - a stale card cannot commit without explicit human re-approval; it is never merged (I-18).
 *  - no unauthorized write may touch a formal object while the single AI batch holds the deck
 *    lock; every AI write is fenced by session + lock epoch + scope (I-06/I-07).
 *  - commit records are idempotent; re-clicking checkmark cannot double-publish (I-15).
 */
class PowerStore(
    context: Context,
    databaseName: String = DATABASE_NAME
) : SQLiteOpenHelper(context, databaseName, null, VERSION) {

    /**
     * Distinguishes this process from an earlier one, so a restart can prove a batch is orphaned.
     * It must be process-wide: a receiver or boot handler opening its own [PowerStore] must not
     * mistake the running UI's AI batch for an orphan and unlock the deck behind its back.
     */
    private val processToken: String get() = PROCESS_TOKEN

    init {
        // Any session owned by another process token is unrecoverable: fence it before the UI can
        // start a new batch. This is why a crash can never leave a permanent deck lock.
        runCatching { recoverInterruptedAi() }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE meta(
                 id INTEGER PRIMARY KEY CHECK(id=1),
                 next_seq INTEGER NOT NULL,
                 next_order INTEGER NOT NULL,
                 ai_epoch INTEGER NOT NULL DEFAULT 0,
                 ai_locked INTEGER NOT NULL DEFAULT 0
               )"""
        )
        db.execSQL("INSERT INTO meta(id,next_seq,next_order) VALUES(1,1,1)")
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never improvise a migration that could drop snapshots, drafts or the sequence high-water
        // mark. A real migration must be written, reviewed and versioned explicitly.
        error("PowerCal 数据库需要显式迁移 (v$oldVersion → v$newVersion)，已拒绝破坏性升级")
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
        put("enabled", form.enabled)
        put("source", form.source)
        put("zone", form.zoneId)
        put("recurrence", encodeRecurrence(form.recurrence))
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
            reminderMinutes = json.optInt("reminder", -1),
            enabled = json.optBoolean("enabled", true),
            source = json.optString("source", "local"),
            zoneId = json.optString("zone", ZoneId.systemDefault().id),
            recurrence = decodeRecurrence(json.optJSONObject("recurrence"))
        )
    }

    private fun encodeRecurrence(rule: Recurrence): JSONObject = JSONObject().apply {
        put("freq", rule.freq.name)
        put("interval", rule.interval)
        rule.count?.let { put("count", it) }
        rule.untilMillis?.let { put("until", it) }
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
        put("reminder", record.reminderMinutes)
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
            reminderMinutes = json.optInt("reminder", -1),
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

    private inline fun <T> tx(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
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

    enum class AiState { REQUESTED, RUNNING, CANCEL_REQUESTED, COMPLETED, CANCELLED, FAILED, INTERRUPTED }

    data class AiSession(
        val sessionId: String,
        val lockEpoch: Long,
        val scope: Set<Long>,
        val allowedOperations: Set<DraftOperation>,
        val state: AiState,
        val startedAtMillis: Long,
        val updatedAtMillis: Long,
        val report: String? = null
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
    ): DraftCard = tx { db ->
        // Cards are produced *before* an authorized batch, never during it. A batch scope is
        // frozen at authorization time and must not silently grow with newly arriving cards.
        check(!isLocked(db)) { "待处理卡组正由 AI 独占，批量结束后再添加草稿" }
        val seq: Long
        val base: Long?
        if (eventSeq == null) {
            require(operation == DraftOperation.CREATE) { "只有新建操作可以分配新序号" }
            seq = allocateIdentity(db, form.source)
            base = null
        } else {
            require(operation != DraftOperation.CREATE) { "已有事件不能用新建操作创建第二张卡" }
            val existing = readObject(db, eventSeq)
            requireNotNull(existing) { "事件不存在或已删除，需先显式恢复" }
            seq = eventSeq
            base = existing.revision
        }
        val order = bump(db, "next_order")
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("event_seq", seq)
            put("queue_order", order)
            if (base == null) putNull("base_rev") else put("base_rev", base)
            put("op", operation.name)
            put("payload", encodeForm(form))
            put("status", DraftStatus.PENDING.name)
            put("draft_version", 0L)
            put("created_ms", now)
            put("updated_ms", now)
        }
        val id = db.insertOrThrow("drafts", null, values)
        requireNotNull(readDraft(db, id)) { "草稿写入失败" }
    }

    /** Creates a fresh draft for an existing event, inheriting its current snapshot. */
    @Synchronized
    fun proposeReplace(
        eventSeq: Long,
        operation: DraftOperation = DraftOperation.REPLACE,
        actor: String = "user"
    ): DraftCard {
        val record = get(eventSeq) ?: error("事件不存在或已删除")
        return newDraft(DraftForm.from(record), eventSeq, operation, actor)
    }

    /**
     * Optimistic concurrency: a save only lands if the caller edited the version it read.
     * A mismatch returns the newer card instead of overwriting someone else's text.
     */
    @Synchronized
    fun stageDraft(
        draftId: Long,
        form: DraftForm,
        expectedVersion: Long,
        aiSession: AiSession? = null
    ): StageResult = tx { db ->
        if (aiSession != null) {
            val denial = authorize(db, aiSession, draftId)
            if (denial != null) return@tx StageResult.NotAuthorized
        } else if (isLocked(db)) {
            return@tx StageResult.Locked
        }
        val updated = db.compileStatement(
            """UPDATE drafts SET payload=?, draft_version=draft_version+1, updated_ms=?
               WHERE draft_id=? AND draft_version=? AND status IN ('PENDING','CONFLICT')"""
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
            "SELECT draft_id FROM drafts WHERE status IN ('PENDING','CONFLICT') ORDER BY queue_order ASC",
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
               WHERE event_seq=? AND status IN ('PENDING','CONFLICT') ORDER BY queue_order ASC""",
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
        db.execSQL(
            "UPDATE drafts SET status='DISCARDED', updated_ms=? WHERE draft_id=? AND status IN ('PENDING','CONFLICT')",
            arrayOf(System.currentTimeMillis(), draftId)
        )
        true
    }

    /** Undo keeps the same draftId and queueOrder, so the card returns to its old place. */
    @Synchronized
    fun undoDiscard(draftId: Long): Boolean = tx { db ->
        if (isLocked(db)) return@tx false
        val updated = db.compileStatement(
            "UPDATE drafts SET status='PENDING', updated_ms=? WHERE draft_id=? AND status='DISCARDED'"
        ).apply {
            bindLong(1, System.currentTimeMillis())
            bindLong(2, draftId)
        }.executeUpdateDelete()
        updated == 1
    }

    // ---------------------------------------------------------------- commits

    @Synchronized
    fun commit(
        draftId: Long,
        approveStale: Boolean = false,
        actor: String = "user",
        aiSession: AiSession? = null
    ): CommitOutcome = tx { db ->
        if (aiSession != null) {
            val authorization = authorize(db, aiSession, draftId)
            if (authorization != null) return@tx authorization
        } else if (isLocked(db)) {
            return@tx CommitOutcome.Locked
        }

        // Idempotency: a card has at most one commit record; repeating the click returns it.
        db.rawQuery(
            "SELECT event_seq,new_revision,commit_id FROM commits WHERE draft_id=?",
            arrayOf(draftId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return@tx CommitOutcome.Committed(
                    cursor.getLong(0), cursor.getLong(1), cursor.getString(2), duplicate = true
                )
            }
        }

        val draft = readDraft(db, draftId) ?: return@tx CommitOutcome.Invalid("草稿不存在")
        if (!draft.isActionable) return@tx CommitOutcome.Invalid("草稿已经处理")

        val current = readObject(db, draft.eventSeq)
        val retired = isRetired(db, draft.eventSeq)

        if (draft.operation.requiresExisting && current == null) {
            return@tx if (retired) CommitOutcome.NeedsRestore(draft.eventSeq)
            else CommitOutcome.Invalid("目标事件不存在")
        }
        if (draft.operation == DraftOperation.CREATE && current != null) {
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
                is FormBuild.Incomplete -> return@tx CommitOutcome.Invalid(built.message)
                is FormBuild.Invalid -> return@tx CommitOutcome.Invalid(built.reason)
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

        val commitId = UUID.randomUUID().toString()
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
        db.execSQL("UPDATE drafts SET status='COMMITTED', updated_ms=? WHERE draft_id=?",
            arrayOf(now, draftId))
        if (draft.operation != DraftOperation.DELETE) {
            writeDesiredIntent(db, readObject(db, draft.eventSeq), now)
        }
        CommitOutcome.Committed(draft.eventSeq, newRevision, commitId)
    }

    /**
     * Explicit resurrection of a retired identity. Never implicit: the user has decided to
     * reuse the SAME eventSeq, and the new snapshot becomes the next revision.
     */
    @Synchronized
    fun restore(
        eventSeq: Long,
        form: DraftForm,
        actor: String = "user",
        aiSession: AiSession? = null
    ): CommitOutcome = tx { db ->
        if (aiSession != null) {
            val check = authorize(db, aiSession, null)
            if (check != null) return@tx check
        } else if (isLocked(db)) {
            return@tx CommitOutcome.Locked
        }
        val retiredRecord = readObject(db, eventSeq, includeRetired = true)
            ?: return@tx CommitOutcome.Invalid("该事件序号从未存在")
        if (!isRetired(db, eventSeq)) return@tx CommitOutcome.Invalid("该事件未被删除，无需恢复")
        val now = System.currentTimeMillis()
        val newRevision = CommitRules.nextRevision(retiredRecord.revision)
        val built = form.build(eventSeq, newRevision, now)
        return@tx when (built) {
            is FormBuild.Incomplete -> CommitOutcome.Invalid(built.message)
            is FormBuild.Invalid -> CommitOutcome.Invalid(built.reason)
            is FormBuild.Ready -> {
                upsertObject(db, built.record.copy(
                    createdAtMillis = retiredRecord.createdAtMillis.takeIf { it > 0 } ?: now,
                    updatedAtMillis = now
                ))
                db.execSQL("UPDATE identities SET retired_ms=NULL WHERE event_seq=?", arrayOf(eventSeq))
                val commitId = UUID.randomUUID().toString()
                db.insertOrThrow("commits", null, ContentValues().apply {
                    put("commit_id", commitId)
                    putNull("draft_id") // restoration has no card; NULL keeps UNIQUE honest
                    put("event_seq", eventSeq)
                    put("previous_revision", retiredRecord.revision)
                    put("new_revision", newRevision)
                    put("actor", actor)
                    put("operation", "RESTORE")
                    put("created_ms", now)
                })
                writeDesiredIntent(db, readObject(db, eventSeq), now)
                CommitOutcome.Committed(eventSeq, newRevision, commitId)
            }
        }
    }

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

    @Synchronized
    fun getIncludingRetired(eventSeq: Long): TemporalRecord? =
        readObject(readableDatabase, eventSeq, includeRetired = true)

    @Synchronized
    fun isRetired(eventSeq: Long): Boolean = isRetired(readableDatabase, eventSeq)

    /** All active formal objects, ordered by axis anchor for the timeline. */
    @Synchronized
    fun all(): List<TemporalRecord> =
        readableDatabase.rawQuery(
            "SELECT payload FROM objects WHERE retired=0 AND anchor_ms IS NOT NULL ORDER BY anchor_ms ASC",
            null
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(decodeRecord(cursor.getString(0))) }
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

    @Synchronized
    fun nextSequencePreview(): Long = metaLong(readableDatabase, "next_seq")

    // ---------------------------------------------------------------- AI batch

    @Synchronized
    fun isLocked(): Boolean = isLocked(readableDatabase)
    private fun isLocked(db: SQLiteDatabase): Boolean = metaLong(db, "ai_locked") == 1L
    private fun currentEpoch(db: SQLiteDatabase): Long = metaLong(db, "ai_epoch")

    @Synchronized
    fun currentAiSession(): AiSession? =
        readableDatabase.rawQuery(
            """SELECT session_id,lock_epoch,scope,allowed_ops,state,started_ms,updated_ms,report
               FROM ai_sessions ORDER BY started_ms DESC LIMIT 1""",
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.toAiSession()
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
     * Acquires the single-AI deck lock. Only one batch can exist; the epoch fences every write
     * that was authorized by an earlier batch.
     */
    @Synchronized
    fun beginAiBatch(
        scope: Set<Long>,
        allowedOperations: Set<DraftOperation>,
        sessionId: String = UUID.randomUUID().toString()
    ): AiSession = tx { db ->
        require(scope.isNotEmpty()) { "请选择要交给 AI 的草稿" }
        require(allowedOperations.isNotEmpty()) { "请选择允许 AI 执行的操作" }
        check(!isLocked(db)) { "已有 AI 批次正在运行" }
        scope.forEach { draftId ->
            val card = readDraft(db, draftId)
            require(card != null && card.isActionable) { "批次范围内包含不存在或已处理的草稿 #$draftId" }
            require(card.operation in allowedOperations) {
                "草稿 #$draftId 的 ${card.operation.label} 操作未获得授权"
            }
        }
        val epoch = currentEpoch(db) + 1
        db.execSQL("UPDATE meta SET ai_locked=1, ai_epoch=? WHERE id=1", arrayOf(epoch))
        val now = System.currentTimeMillis()
        db.insertOrThrow("ai_sessions", null, ContentValues().apply {
            put("session_id", sessionId)
            put("lock_epoch", epoch)
            put("scope", scope.sorted().joinToString(","))
            put("allowed_ops", allowedOperations.map { it.name }.sorted().joinToString(","))
            put("state", AiState.RUNNING.name)
            put("owner_token", processToken)
            put("started_ms", now)
            put("updated_ms", now)
        })
        AiSession(sessionId, epoch, scope, allowedOperations, AiState.RUNNING, now, now)
    }

    /** Re-reads the persisted session so callers cannot keep a stale in-memory snapshot. */
    @Synchronized
    fun refreshSession(sessionId: String): AiSession? =
        readableDatabase.rawQuery(
            """SELECT session_id,lock_epoch,scope,allowed_ops,state,started_ms,updated_ms,report
               FROM ai_sessions WHERE session_id=?""",
            arrayOf(sessionId)
        ).use { if (it.moveToFirst()) it.toAiSession() else null }

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
        db.rawQuery("SELECT lock_epoch,owner_token FROM ai_sessions WHERE session_id=?",
            arrayOf(sessionId)).use { cursor ->
            if (!cursor.moveToFirst()) return@tx false
            val epoch = cursor.getLong(0)
            val owner = cursor.getString(1)
            if (owner != processToken) return@tx false
            if (epoch != currentEpoch(db)) return@tx false
        }
        val now = System.currentTimeMillis()
        db.execSQL(
            "UPDATE ai_sessions SET state=?, updated_ms=?, report=? WHERE session_id=?",
            arrayOf(state.name, now, report?.toString(), sessionId)
        )
        db.execSQL("UPDATE meta SET ai_locked=0, ai_epoch=? WHERE id=1", arrayOf(currentEpoch(db) + 1))
        true
    }

    /**
     * Startup recovery. A session owned by a previous process cannot still be running, so it is
     * fenced and unlocked exactly once. This is safe to call while a live batch from THIS process
     * is running: the owner token matches and nothing is touched.
     */
    @Synchronized
    fun recoverInterruptedAi(): Boolean = tx { db ->
        if (!isLocked(db)) return@tx false
        val orphan = db.rawQuery(
            "SELECT session_id,lock_epoch,owner_token FROM ai_sessions WHERE state IN ('REQUESTED','RUNNING','CANCEL_REQUESTED')",
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getString(2) != processToken) {
                        add(cursor.getString(0) to cursor.getLong(1))
                    }
                }
            }
        }
        if (orphan.isEmpty()) return@tx false
        val now = System.currentTimeMillis()
        orphan.forEach { (id, _) ->
            db.execSQL(
                "UPDATE ai_sessions SET state='INTERRUPTED', updated_ms=? WHERE session_id=?",
                arrayOf(now, id)
            )
        }
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
                    when {
                        card == null -> CommitOutcome.Invalid("草稿不存在")
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
        val published = db.rawQuery(
            "SELECT COUNT(*) FROM commits WHERE created_ms>=?" + if (actor != null) " AND actor=?" else "",
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
            "SELECT COUNT(*) FROM drafts WHERE status IN ('PENDING','CONFLICT')", null
        ).use { it.moveToFirst(); it.getInt(0) }

    // ---------------------------------------------------------------- scheduling intent

    /**
     * Durable desired schedule for the next occurrence. Android side effects are reconciled
     * later; this row is what makes "saved" vs "scheduled" vs "blocked" honest.
     */
    private fun writeDesiredIntent(db: SQLiteDatabase, record: TemporalRecord?, now: Long) {
        if (record == null) return
        cancelIntents(db, record.eventSeq, now)
        if (!record.enabled) return
        val occurrence = RecurrenceEngine.nextOccurrence(record, now) ?: return
        val offset = when {
            record.kind == TemporalKind.ALARM && record.reminderMinutes < 0 -> 0L
            record.reminderMinutes >= 0 -> record.reminderMinutes.toLong() * 60_000L
            else -> return
        }
        val triggerInstant = occurrence.startMillis - offset
        val requirement = if (record.kind == TemporalKind.ALARM) {
            TriggerRequirement.EXACT
        } else {
            TriggerRequirement.INEXACT
        }
        db.insertWithOnConflict("schedule_intents", null, ContentValues().apply {
            put("event_seq", record.eventSeq)
            put("trigger_id", "primary")
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
    fun dueIntents(limit: Int = 64): List<ScheduleIntent> =
        readableDatabase.rawQuery(
            """SELECT event_seq,trigger_id,revision,occurrence_key,instant_ms,requirement,desired_state,android_hint
               FROM schedule_intents WHERE desired_state IN ('PENDING','SCHEDULED')
               ORDER BY instant_ms ASC LIMIT ?""",
            arrayOf(limit.toString())
        ).use { cursor ->
            buildList {
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
        }

    @Synchronized
    fun intentFor(eventSeq: Long, triggerId: String = "primary"): ScheduleIntent? =
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
        hint: String? = null
    ) {
        writableDatabase.execSQL(
            "UPDATE schedule_intents SET desired_state=?, android_hint=?, updated_ms=? WHERE event_seq=? AND trigger_id=?",
            arrayOf(state.name, hint, System.currentTimeMillis(), eventSeq, triggerId)
        )
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
        val now = System.currentTimeMillis()
        val seq = allocateIdentity(db, source)
        val prefersDisabled = record.kind == TemporalKind.ALARM && alarmNeedsHandoff
        val candidate = record.copy(
            eventSeq = seq,
            revision = 1L,
            enabled = record.enabled && !prefersDisabled,
            source = source,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        candidate.validate()?.let { return@tx ImportOutcome.Rejected(it) }
        upsertObject(db, candidate)
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
        writeDesiredIntent(db, candidate, now)
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
            arrayOf(
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

    /** Full native snapshot for backup; deliberately excludes live Android handles. */
    @Synchronized
    fun exportBackup(): JSONObject = JSONObject().apply {
        put("format", "powercal-native")
        put("version", VERSION)
        put("exportedMs", System.currentTimeMillis())
        put("nextSeq", nextSequencePreview())
        put("identities", JSONArray().apply {
            readableDatabase.rawQuery(
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
            readableDatabase.rawQuery("SELECT payload,retired FROM objects", null).use { cursor ->
                while (cursor.moveToNext()) put(JSONObject().apply {
                    put("payload", JSONObject(cursor.getString(0)))
                    put("retired", cursor.getInt(1))
                })
            }
        })
        put("drafts", JSONArray().apply {
            readableDatabase.rawQuery(
                """SELECT event_seq,queue_order,base_rev,op,payload,status,draft_version,created_ms,updated_ms
                   FROM drafts ORDER BY queue_order""", null
            ).use { cursor ->
                while (cursor.moveToNext()) put(JSONObject().apply {
                    put("eventSeq", cursor.getLong(0))
                    put("queueOrder", cursor.getLong(1))
                    if (cursor.isNull(2)) put("baseRev", JSONObject.NULL) else put("baseRev", cursor.getLong(2))
                    put("op", cursor.getString(3))
                    put("payload", JSONObject(cursor.getString(4)))
                    put("status", cursor.getString(5))
                    put("draftVersion", cursor.getLong(6))
                    put("createdMs", cursor.getLong(7))
                    put("updatedMs", cursor.getLong(8))
                })
            }
        })
        put("commits", JSONArray().apply {
            readableDatabase.rawQuery(
                "SELECT commit_id,event_seq,new_revision,actor,operation,created_ms FROM commits", null
            ).use { cursor ->
                while (cursor.moveToNext()) put(JSONObject().apply {
                    put("commitId", cursor.getString(0))
                    put("eventSeq", cursor.getLong(1))
                    put("revision", cursor.getLong(2))
                    put("actor", cursor.getString(3))
                    put("operation", cursor.getString(4))
                    put("createdMs", cursor.getLong(5))
                })
            }
        })
        put("sourceLinks", JSONArray().apply {
            readableDatabase.rawQuery("SELECT source,external_id,event_seq FROM source_links", null).use {
                while (it.moveToNext()) put(JSONObject().apply {
                    put("source", it.getString(0))
                    put("externalId", it.getString(1))
                    put("eventSeq", it.getLong(2))
                })
            }
        })
        // AI authority is intentionally NOT exported: restoring a backup must never revive a
        // stale batch lock or a stale authorization scope.
        put("note", "AI 批次授权、Android 调度句柄与 API 密钥不包含在备份中")
    }

    // ---------------------------------------------------------------- native backup

    data class RestoreReport(
        val identities: Int,
        val objects: Int,
        val drafts: Int,
        val commits: Int
    )

    /**
     * Restores a native snapshot into an EMPTY store only. Overwriting a live database would be an
     * irreversible action the user did not ask for, so it is refused instead of "helpfully" wiped.
     * AI authority is never restored: the new install starts unlocked and unscheduled.
     */
    @Synchronized
    fun restoreBackup(snapshot: JSONObject): RestoreReport = tx { db ->
        require(identityCount() == 0L && nextSequencePreview() == 1L) {
            "目标数据库已有事件：为避免覆盖，恢复只允许在空数据库中进行"
        }
        require(snapshot.optString("format") == "powercal-native") { "不是 PowerCal 原生备份" }
        val identities = snapshot.optJSONArray("identities") ?: JSONArray()
        val objects = snapshot.optJSONArray("objects") ?: JSONArray()
        var maxSeq = 1L
        for (index in 0 until identities.length()) {
            val item = identities.getJSONObject(index)
            val seq = item.getLong("seq")
            maxSeq = maxOf(maxSeq, seq + 1)
            db.insertOrThrow("identities", null, ContentValues().apply {
                put("event_seq", seq)
                put("allocated_ms", item.optLong("allocatedMs", System.currentTimeMillis()))
                if (item.has("retiredMs")) put("retired_ms", item.getLong("retiredMs"))
                put("origin", item.optString("origin", "local"))
            })
        }
        var objectCount = 0
        for (index in 0 until objects.length()) {
            val wrapper = objects.getJSONObject(index)
            val payload = wrapper.getJSONObject("payload")
            val record = decodeRecord(payload.toString())
            val retired = wrapper.optInt("retired", 0) == 1
            val values = ContentValues().apply {
                put("event_seq", record.eventSeq)
                put("revision", record.revision)
                put("kind", record.kind.name)
                put("anchor_ms", record.anchorMillis())
                put("span_end_ms", record.spanEndMillis())
                put("payload", payload.toString())
                put("retired", if (retired) 1 else 0)
                put("created_ms", record.createdAtMillis)
                put("updated_ms", record.updatedAtMillis)
            }
            db.insertWithOnConflict("objects", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            objectCount++
        }
        val drafts = snapshot.optJSONArray("drafts") ?: JSONArray()
        var draftCount = 0
        var maxOrder = 1L
        for (index in 0 until drafts.length()) {
            val item = drafts.getJSONObject(index)
            val order = item.getLong("queueOrder")
            maxOrder = maxOf(maxOrder, order + 1)
            db.insertOrThrow("drafts", null, ContentValues().apply {
                put("event_seq", item.getLong("eventSeq"))
                put("queue_order", order)
                if (item.isNull("baseRev")) putNull("base_rev") else put("base_rev", item.getLong("baseRev"))
                put("op", item.getString("op"))
                put("payload", item.getJSONObject("payload").toString())
                put("status", item.optString("status", "PENDING"))
                put("draft_version", item.optLong("draftVersion", 0L))
                put("created_ms", item.optLong("createdMs", System.currentTimeMillis()))
                put("updated_ms", item.optLong("updatedMs", System.currentTimeMillis()))
            })
            draftCount++
        }
        val commits = snapshot.optJSONArray("commits") ?: JSONArray()
        var commitCount = 0
        for (index in 0 until commits.length()) {
            val item = commits.getJSONObject(index)
            db.insertOrThrow("commits", null, ContentValues().apply {
                put("commit_id", item.getString("commitId"))
                putNull("draft_id")
                put("event_seq", item.getLong("eventSeq"))
                putNull("previous_revision")
                put("new_revision", item.getLong("revision"))
                put("actor", item.optString("actor", "restore"))
                put("operation", item.optString("operation", "RESTORE"))
                put("created_ms", item.optLong("createdMs", System.currentTimeMillis()))
            })
            commitCount++
        }
        val links = snapshot.optJSONArray("sourceLinks") ?: JSONArray()
        for (index in 0 until links.length()) {
            val item = links.getJSONObject(index)
            db.insertWithOnConflict("source_links", null, ContentValues().apply {
                put("source", item.getString("source"))
                put("external_id", item.getString("externalId"))
                put("event_seq", item.getLong("eventSeq"))
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
        db.execSQL(
            "UPDATE meta SET next_seq=?, next_order=?, ai_locked=0, ai_epoch=ai_epoch+1 WHERE id=1",
            arrayOf(maxSeq, maxOrder)
        )
        // Schedules are rebuilt from scratch after a restore; never replay stale Android handles.
        db.execSQL("DELETE FROM schedule_intents")
        db.execSQL("DELETE FROM ai_sessions")
        RestoreReport(identities.length(), objectCount, draftCount, commitCount)
    }

    companion object {
        const val DATABASE_NAME = "powercal-v2.db"
        const val VERSION = 1

        /** One token per OS process; every PowerStore instance in the process shares it. */
        private val PROCESS_TOKEN: String = UUID.randomUUID().toString()
    }
}
