package dev.powerfulcalendar.core.api.v1

/** Stable todo occurrence projection. No Store or Core entity type crosses this contract. */
data class MemoEntryV1(
    val eventSeq: Long,
    val revision: Long,
    val kind: EventKindV1,
    val occurrenceKey: String,
    val startMillis: Long?,
    val title: String,
    val completed: Boolean,
    val zoneId: String,
    val repeating: Boolean
) {
    /** Presentation action key; the Host still resolves and revalidates every command. */
    val rowKey: String get() = "$eventSeq:$occurrenceKey"
}

data class MemoQuerySnapshotV1(
    val featureRevision: Long,
    val nowMillis: Long,
    val includesCompleted: Boolean,
    val entries: List<MemoEntryV1>
)

/** Stable continuation after one displayed Memo occurrence. */
data class MemoQueryCursorV1(
    val featureRevision: Long,
    val nowMillis: Long,
    val includesCompleted: Boolean,
    val undated: Boolean,
    val startMillis: Long?,
    val eventSeq: Long,
    val occurrenceKey: String,
    val serviceInstanceId: String
)

/** Bounded Memo result page. Counts cover the whole query while [entries] is limited. */
data class MemoQueryPageV1(
    val featureRevision: Long,
    val nowMillis: Long,
    val includesCompleted: Boolean,
    val entries: List<MemoEntryV1>,
    val totalEntries: Int,
    val openEntries: Int,
    val nextCursor: MemoQueryCursorV1? = null,
    val stale: Boolean = false
)

/** Unified, bounded todo projection and current-occurrence visibility query. */
interface MemoFeatureApiV1 {
    val apiVersion: Int

    /** Projects a supplied immutable Event snapshot, preserving its feature revision. */
    fun queryEntries(
        source: EventQuerySnapshotV1,
        nowMillis: Long,
        includeCompleted: Boolean = false
    ): MemoQuerySnapshotV1

    /**
     * Streams formal owners from Store pages and returns one globally ordered result page.
     * Continuations are fenced by query parameters and the Event feature revision.
     */
    fun queryEntriesPage(
        nowMillis: Long,
        includeCompleted: Boolean = false,
        cursor: MemoQueryCursorV1? = null,
        limit: Int = DEFAULT_PAGE_SIZE
    ): MemoQueryPageV1

    /** Re-reads one current owner and returns the occurrence only when it is still visible. */
    fun queryCurrentEntry(
        eventSeq: Long,
        occurrenceKey: String,
        nowMillis: Long,
        includeCompleted: Boolean = true
    ): MemoEntryV1?

    companion object {
        const val DEFAULT_PAGE_SIZE = 250
        const val MAX_PAGE_SIZE = 250
    }
}
