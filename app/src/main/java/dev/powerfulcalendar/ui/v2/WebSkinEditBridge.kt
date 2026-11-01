package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.EditorSelection
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftSourceRevisionMismatchException
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftQueueSearch
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.FormBuild
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.ReminderTiming
import dev.powerfulcalendar.core.TemporalConversion
import dev.powerfulcalendar.core.api.v1.EventDeleteApprovalV1
import dev.powerfulcalendar.core.api.v1.EventEditorHandleV1
import dev.powerfulcalendar.core.api.v1.EventEditorOpenResultV1
import dev.powerfulcalendar.core.api.v1.EventEditorSnapshotV1
import dev.powerfulcalendar.core.api.v1.EventEditorUpdateStatusV1
import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.core.api.v1.EventRecurrenceScopeV1
import dev.powerfulcalendar.core.api.v1.EventSelectionV1
import dev.powerfulcalendar.core.api.v1.EventFeatureApiV1
import dev.powerfulcalendar.core.api.v1.EventSearchHitV1
import dev.powerfulcalendar.core.api.v1.EventMutationReceiptV1
import dev.powerfulcalendar.core.api.v1.EventMutationStatusV1
import dev.powerfulcalendar.core.api.v1.DraftOperationV1
import dev.powerfulcalendar.core.api.v1.toCoreTemporalRecord
import dev.powerfulcalendar.core.api.v1.DraftReviewFeatureApiV1
import dev.powerfulcalendar.core.api.v1.DraftReviewReceiptV1
import dev.powerfulcalendar.core.api.v1.DraftReviewReceiptStatusV1
import dev.powerfulcalendar.core.api.v1.DraftReviewConflictV1
import dev.powerfulcalendar.core.api.v1.DraftReviewRestoreV1
import dev.powerfulcalendar.core.api.v1.DraftReviewCardV1
import dev.powerfulcalendar.core.api.v1.EventFormV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
import dev.powerfulcalendar.core.api.v1.toCoreDraftForm
import dev.powerfulcalendar.core.api.v1.toEventFormV1
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Host-owned, capability-neutral editing contract.
 * The bundled Clear skin and imported skins use the exact same endpoint.
 *
 * Deliberately exposes a small *patch* whitelist, never the raw DraftForm:
 * provenance, completion keys, reminder authorization, timezone, or lease
 * tokens cannot be supplied or rewritten by an untrusted skin.
 */
internal sealed interface WebEditCommand {
    val requestId: String
    data class OpenEvent(override val requestId: String, val eventSeq: Long,
        val revision: Long, val occurrenceKey: String? = null): WebEditCommand
    data class MoveTimelineEvent(override val requestId: String, val eventSeq: Long,
        val expectedRevision: Long, val targetMillis: Long): WebEditCommand
    data class SelectScope(override val requestId: String, val sessionId: String,
        val scope: RecurrenceEditScope): WebEditCommand
    data class DiscardEventChanges(override val requestId: String, val sessionId: String): WebEditCommand
    data class PatchEvent(override val requestId: String, val sessionId: String,
        val field: String, val value: String): WebEditCommand
    data class SaveEvent(override val requestId: String, val sessionId: String): WebEditCommand
    data class DeleteEvent(override val requestId: String, val sessionId: String): WebEditCommand
    data class CloseEvent(override val requestId: String, val sessionId: String): WebEditCommand
    data class OpenDeck(override val requestId: String, val draftId: Long?): WebEditCommand
    data class SearchDeck(override val requestId: String, val query: String): WebEditCommand
    data class LoadRetiredPage(override val requestId: String, val offset: Int): WebEditCommand
    data class JumpDeck(override val requestId: String, val steps: Int): WebEditCommand
    data class ProposeReplaceDeck(override val requestId: String, val eventSeq: Long,
        val expectedRevision: Long): WebEditCommand
    data class BeginRestoreDeck(override val requestId: String, val eventSeq: Long,
        val expectedRevision: Long): WebEditCommand
    data class KeepConflictDeck(override val requestId: String, val draftId: Long,
        val draftVersion: Long, val eventSeq: Long, val expectedRevision: Long): WebEditCommand
    data class RequestConflictReviewDeck(override val requestId: String, val draftId: Long,
        val draftVersion: Long, val eventSeq: Long, val expectedRevision: Long): WebEditCommand
    data class KeepRestoreDeck(override val requestId: String, val draftId: Long,
        val draftVersion: Long, val eventSeq: Long, val expectedRevision: Long): WebEditCommand
    data class RequestRestoreReviewDeck(override val requestId: String, val draftId: Long,
        val draftVersion: Long, val eventSeq: Long, val expectedRevision: Long): WebEditCommand
    data class CreateDeck(override val requestId: String, val kind: TemporalKind): WebEditCommand
    data class PatchDeck(override val requestId: String, val draftId: Long,
        val draftVersion: Long, val field: String, val value: String,
        val stateRevision: Long? = null): WebEditCommand
    data class SelectDeck(override val requestId: String, val draftId: Long): WebEditCommand
    data class FlushDeck(override val requestId: String): WebEditCommand
    data class CloseDeck(override val requestId: String): WebEditCommand
    data class CommitDeck(override val requestId: String, val draftId: Long,
        val draftVersion: Long): WebEditCommand
    data class DiscardDeck(override val requestId: String, val draftId: Long,
        val draftVersion: Long): WebEditCommand
    data class UndoDeck(override val requestId: String): WebEditCommand
}

internal object WebEditWire {
    const val MAX_FRAME = 8192
    private val request = Regex("[A-Za-z0-9_-]{8,64}")
    private val session = Regex("[a-f0-9-]{36}")
    private val occurrence = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}")
    private val fields = mapOf("kind" to 10, "title" to 240, "startText" to 40,
        "endText" to 40, "notes" to 2000, "location" to 240,
        "reminderMinutes" to 6, "reminderMethod" to 20, "reminderTiming" to 20,
        "enabled" to 5, "trackingEnabled" to 5,
        "recurrenceFreq" to 20, "recurrenceInterval" to 3,
        "recurrenceFrom" to 10, "recurrenceUntil" to 10)
    fun decode(raw: String): WebEditCommand? {
        if (raw.length !in 1..MAX_FRAME) return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (json.keys().asSequence().toSet() != setOf("version", "type", "payload") ||
            json.optInt("version") != 1 || json.optString("type") != "command") return null
        val payload = json.optJSONObject("payload") ?: return null
        val keys = payload.keys().asSequence().toSet()
        val name = payload.opt("name") as? String ?: return null
        val id = payload.opt("requestId") as? String ?: return null
        if (!request.matches(id)) return null
        fun exact(vararg other: String) = keys == (setOf("name", "requestId") + other.toSet())
        fun number(key: String, zeroAllowed: Boolean = false): Long? {
            val value = payload.opt(key)
            if (value !is Int && value !is Long) return null
            val n = (value as Number).toLong()
            return n.takeIf { if (zeroAllowed) it >= 0 else it > 0 }
        }
        fun signedLong(key: String): Long? {
            val value = payload.opt(key)
            if (value !is Int && value !is Long) return null
            return (value as Number).toLong()
        }
        fun boundedInt(key: String, min: Int, max: Int): Int? {
            val value = payload.opt(key)
            if (value !is Int && value !is Long) return null
            return (value as Number).toLong().takeIf { it in min.toLong()..max.toLong() }?.toInt()
        }
        fun field(): Pair<String, String>? {
            val key = payload.opt("field") as? String ?: return null
            val value = payload.opt("value") as? String ?: return null
            if (value.length > (fields[key] ?: return null) || '\u0000' in value) return null
            return key to value
        }
        fun sessionId(): String? =
            (payload.opt("sessionId") as? String)?.takeIf(session::matches)
        return when(name) {
            "editor.open" -> {
                if (!exact("eventSeq","expectedRevision") &&
                    !exact("eventSeq","expectedRevision","occurrenceKey")) return null
                val key = if ("occurrenceKey" in keys) {
                    (payload.opt("occurrenceKey") as? String)?.takeIf(occurrence::matches) ?: return null
                } else null
                WebEditCommand.OpenEvent(id,number("eventSeq")?:return null,
                    number("expectedRevision")?:return null,key)
            }
            "timeline.move" -> {
                if (!exact("eventSeq", "expectedRevision", "targetMillis")) return null
                WebEditCommand.MoveTimelineEvent(
                    id,
                    number("eventSeq") ?: return null,
                    number("expectedRevision") ?: return null,
                    signedLong("targetMillis") ?: return null
                )
            }
            "editor.scope" -> {
                if (!exact("sessionId","scope")) return null
                val scope = (payload.opt("scope") as? String)?.let {
                    RecurrenceEditScope.entries.firstOrNull { candidate -> candidate.name == it }
                } ?: return null
                WebEditCommand.SelectScope(id,sessionId()?:return null,scope)
            }
            "editor.discardChanges" -> if (exact("sessionId"))
                WebEditCommand.DiscardEventChanges(id,sessionId()?:return null) else null
            "editor.patch" -> {
                if (!exact("sessionId","field","value")) return null
                val f=field()?:return null
                WebEditCommand.PatchEvent(id,sessionId()?:return null,f.first,f.second)
            }
            "editor.save" -> if (exact("sessionId"))
                WebEditCommand.SaveEvent(id,sessionId()?:return null) else null
            "editor.delete" -> if (exact("sessionId"))
                WebEditCommand.DeleteEvent(id,sessionId()?:return null) else null
            "editor.close" -> if (exact("sessionId"))
                WebEditCommand.CloseEvent(id,sessionId()?:return null) else null
            "deck.open" -> if(exact("draftId")) {
                val rawId=payload.opt("draftId")
                if(rawId==JSONObject.NULL) WebEditCommand.OpenDeck(id,null)
                else WebEditCommand.OpenDeck(id,number("draftId")?:return null)
            } else null
            "deck.search" -> if(exact("query")) {
                val query = payload.opt("query") as? String ?: return null
                if(query.length > 120 || query.any { it == '\u0000' || it.isISOControl() }) null
                else WebEditCommand.SearchDeck(id, query)
            } else null
            "deck.retired" -> if(exact("offset"))
                WebEditCommand.LoadRetiredPage(id, boundedInt("offset", 0, 1_000_000) ?: return null)
            else null
            "deck.jump" -> if(exact("steps"))
                WebEditCommand.JumpDeck(id, boundedInt("steps", -1000, 1000) ?: return null)
            else null
            "deck.beginRestore" -> if(exact("eventSeq", "expectedRevision"))
                WebEditCommand.BeginRestoreDeck(id, number("eventSeq") ?: return null,
                    number("expectedRevision") ?: return null)
            else null
            "deck.keepConflict", "deck.requestConflictReview",
            "deck.keepRestore", "deck.requestRestoreReview" -> {
                if (!exact("draftId", "draftVersion", "eventSeq", "expectedRevision")) return null
                val draftId = number("draftId") ?: return null
                val draftVersion = number("draftVersion", true) ?: return null
                val eventSeq = number("eventSeq") ?: return null
                val expectedRevision = number("expectedRevision") ?: return null
                when (name) {
                    "deck.keepConflict" -> WebEditCommand.KeepConflictDeck(
                        id, draftId, draftVersion, eventSeq, expectedRevision)
                    "deck.requestConflictReview" -> WebEditCommand.RequestConflictReviewDeck(
                        id, draftId, draftVersion, eventSeq, expectedRevision)
                    "deck.keepRestore" -> WebEditCommand.KeepRestoreDeck(
                        id, draftId, draftVersion, eventSeq, expectedRevision)
                    else -> WebEditCommand.RequestRestoreReviewDeck(
                        id, draftId, draftVersion, eventSeq, expectedRevision)
                }
            }
            "deck.proposeReplace" -> if(exact("eventSeq", "expectedRevision"))
                WebEditCommand.ProposeReplaceDeck(
                    id,
                    number("eventSeq") ?: return null,
                    number("expectedRevision") ?: return null
                )
            else null
            "deck.create" -> {
                if(!exact("kind")) return null
                val kind = (payload.opt("kind") as? String)?.let {
                    runCatching { TemporalKind.valueOf(it) }.getOrNull()
                } ?: return null
                WebEditCommand.CreateDeck(id,kind)
            }
            "deck.patch" -> {
                if (!exact("draftId","draftVersion","stateRevision","field","value")) return null
                val f=field()?:return null
                WebEditCommand.PatchDeck(id,number("draftId")?:return null,
                    number("draftVersion",true)?:return null,
                    f.first,f.second,number("stateRevision",true)?:return null)
            }
            "deck.select" -> if(exact("draftId"))
                WebEditCommand.SelectDeck(id,number("draftId")?:return null) else null
            "deck.flush" -> if(exact()) WebEditCommand.FlushDeck(id) else null
            "deck.close" -> if(exact()) WebEditCommand.CloseDeck(id) else null
            "deck.commit", "deck.discard" -> {
                if(!exact("draftId","draftVersion")) return null
                val draftId=number("draftId")?:return null
                val version=number("draftVersion",true)?:return null
                if(name=="deck.commit") WebEditCommand.CommitDeck(id,draftId,version)
                else WebEditCommand.DiscardDeck(id,draftId,version)
            }
            "deck.undo" -> if(exact()) WebEditCommand.UndoDeck(id) else null
            else -> null
        }
    }

    /** A semantic patch may change a permitted field only, never replace hidden metadata. */
    fun patch(form: DraftForm, field: String, value: String): DraftForm = when(field) {
        "kind" -> {
            val target = TemporalKind.entries.firstOrNull { it.name == value }
                ?: throw IllegalArgumentException("Invalid event kind")
            TemporalConversion.convert(form, target).form
        }
        "title" -> form.copy(title=value)
        "startText" -> form.copy(startText=value)
        "endText" -> form.copy(endText=value)
        "notes" -> form.copy(notes=value)
        "location" -> form.copy(location=value)
        "enabled" -> form.copy(enabled=requireBoolean(value))
        "trackingEnabled" -> form.copy(
            trackingEnabled=if(form.kind==TemporalKind.MEMO) true else requireBoolean(value))
        "reminderMinutes" -> {
            val minutes=value.toIntOrNull()?.takeIf { it in -1..10080 }
                ?: throw IllegalArgumentException("Invalid reminder offset")
            form.copy(reminderMinutes=minutes)
        }
        "reminderMethod" -> form.copy(reminderMethod=
            ReminderMethod.entries.firstOrNull { it.name==value }
                ?: throw IllegalArgumentException("Invalid reminder method"))
        "reminderTiming" -> form.copy(reminderTiming=
            ReminderTiming.entries.firstOrNull { it.name==value }
                ?: throw IllegalArgumentException("Invalid reminder timing"))
        "recurrenceFreq" -> {
            val freq=RecurrenceFreq.entries.firstOrNull { it.name==value }
                ?: throw IllegalArgumentException("Invalid recurrence frequency")
            form.copy(recurrence=if(freq==RecurrenceFreq.NONE) Recurrence.NONE
                else form.recurrence.copy(freq=freq))
        }
        "recurrenceInterval" -> {
            val interval=value.toIntOrNull()?.takeIf { it in 1..365 }
                ?: throw IllegalArgumentException("Invalid recurrence interval")
            form.copy(recurrence=form.recurrence.copy(interval=interval))
        }
        "recurrenceFrom" -> form.copy(recurrence=form.recurrence.copy(
            activeFromDate=readDate(value)))
        "recurrenceUntil" -> form.copy(recurrence=form.recurrence.copy(
            activeUntilDate=readDate(value)))
        else -> throw IllegalArgumentException("Field is not writable")
    }

    private fun requireBoolean(value:String):Boolean = when(value) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("Expected boolean")
    }

    private fun readDate(value:String): String? = value.ifBlank { return null }.also {
        require(LocalDate.parse(it).toString()==it) { "Expected ISO local date" }
    }

    fun form(form: DraftForm): JSONObject = JSONObject()
        .put("kind",form.kind.name).put("title",form.title)
        .put("startText",form.startText).put("endText",form.endText)
        .put("notes",form.notes).put("location",form.location)
        .put("zoneId",form.zoneId).put("source",form.source)
        .put("reminderMinutes",form.reminderMinutes)
        .put("reminderMethod",form.reminderMethod.name)
        .put("reminderTiming",form.reminderTiming.name)
        .put("enabled",form.enabled)
        .put("trackingEnabled",form.trackingEnabled)
        .put("recurrenceFreq",form.recurrence.freq.name)
        .put("recurrenceInterval",form.recurrence.interval)
        .put("recurrenceFrom",form.recurrence.activeFromDate)
        .put("recurrenceUntil",form.recurrence.activeUntilDate)

    fun form(form: EventFormV1): JSONObject = form(form.toCoreDraftForm())
}

private val DraftReviewFeatureApiV1.state: dev.powerfulcalendar.core.api.v1.DraftReviewSnapshotV1
    get() = snapshot

private fun DraftReviewCardV1.toCoreDraftCard() = DraftCard(
    draftId = draftId,
    eventSeq = eventSeq,
    queueOrder = queueOrder,
    operation = DraftOperation.valueOf(operation.name),
    form = form.toCoreDraftForm(),
    status = DraftStatus.valueOf(status.name),
    baseRevision = baseRevision,
    draftVersion = draftVersion,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

/** Never projected to JavaScript: only a trusted host dialog can resolve this snapshot. */
internal data class WebFormalWriteReview(
    val token: String,
    val command: WebEditCommand,
    val operation: DraftOperation,
    val current: TemporalRecord?,
    val before: TemporalRecord?,
    val proposed: DraftForm?,
    val scope: RecurrenceEditScope? = null,
    val occurrenceKey: String? = null,
    val editorSession: WebFormalEditor? = null,
    val editorFormSnapshot: DraftForm? = null,
    val newEventSession: WebNewEventEditor? = null,
    val deckCard: DraftCard? = null,
    val deckForm: DraftForm? = null,
    val deckVersion: Long? = null,
    val deckStateRevision: Long? = null,
    val conflictPrompt: DraftReviewConflictV1? = null,
    val restorePrompt: DraftReviewRestoreV1? = null
)

/** Host-only adapter state. The Core editor object never crosses the versioned API boundary. */
internal class WebFormalEditor(
    val handle: EventEditorHandleV1,
    var snapshot: EventEditorSnapshotV1,
    var form: DraftForm
) {
    val eventSeq: Long get() = requireNotNull(snapshot.eventSeq)
    val requiresRecurrenceScope: Boolean get() = snapshot.recurrenceScopeRequired
    val canEditSelectedOccurrence: Boolean get() = snapshot.selectedOccurrenceKey != null
    val selectedOccurrenceKey: String? get() = snapshot.selectedOccurrenceKey
    val recurrenceScope: RecurrenceEditScope? get() = form.recurrenceEditScope
    val meaningfulDiff: Boolean get() = snapshot.meaningfulChanges
    val lastError: String? get() = snapshot.lastError
}

/** Host-only adapter state for one temporary new-event editor. */
internal class WebNewEventEditor(
    val handle: EventEditorHandleV1,
    var snapshot: EventEditorSnapshotV1,
    var form: DraftForm
) {
    val clientDraftId: Long get() = requireNotNull(snapshot.clientDraftId)
    val version: Long get() = snapshot.draftVersion ?: 0L
    val persistedCard: Boolean get() = snapshot.hasPersistedDraft
    val meaningfulDiff: Boolean get() = snapshot.meaningfulChanges
    val lastError: String? get() = snapshot.lastError
    fun previewCard() = DraftCard(
        draftId = clientDraftId,
        eventSeq = snapshot.eventSeq ?: 0L,
        queueOrder = 0L,
        operation = DraftOperation.CREATE,
        form = form,
        status = DraftStatus.PENDING,
        baseRevision = null,
        draftVersion = version
    )
}

/** Host-owned timeline camera and zone; skins never choose the drag snap grid. */
internal data class TimelineMoveContext(
    val camera: TimelineCoordinate.Camera,
    val zone: ZoneId,
    val visibleEventRevisions: Map<Long, Long>
)

internal class WebSkinEditService(
    private val store: PowerStore,
    private val deck: DraftReviewFeatureApiV1,
    private val externalEditorActive: () -> Boolean = { false },
    private val coreFeatureApi: PowerCalCoreFeatureApiV1
) {
    internal constructor(
        store: PowerStore,
        deckController: dev.powerfulcalendar.core.DeckController,
        externalEditorActive: () -> Boolean = { false },
        coreFeatureApi: PowerCalCoreFeatureApiV1
    ) : this(store, coreFeatureApi.adaptDeckController(deckController), externalEditorActive, coreFeatureApi)

    private val eventFeatures: EventFeatureApiV1 = coreFeatureApi.events
    private val gate = Mutex()
    private var session: WebFormalEditor? = null
    private var sessionId: String? = null
    private var newEventSession: WebNewEventEditor? = null
    private var deckOpen = false
    private var deckSearchQuery = ""
    private var formalSearchResults: List<EventSearchHitV1> = emptyList()
    private var retiredOffset = 0
    private var retiredTotal = 0
    private var retiredResults: List<TemporalRecord> = emptyList()
    private var retiredLoaded = false
    private var restoreTombstone: TemporalRecord? = null
    private val retiredPageSize = 50
    var pendingWriteReview by mutableStateOf<WebFormalWriteReview?>(null)
        private set
    // Compose-observable lifetime signal for reliable Android Back interception.
    private var hasActiveUiSession by mutableStateOf(false)
    // In-process replay protection; capacity bounded. One instance per host, NOT per WebView.
    private val completed = LinkedHashMap<String, Pair<WebEditCommand, JSONObject>>()
    @Volatile private var latest: JSONObject? = null

    private fun result(id: String, status: String, detail: String? = null): JSONObject =
        JSONObject().put("requestId",id).put("status",status)
            .apply { if(detail!=null)put("detail",detail.take(240)) }

    private fun result(receipt: EventMutationReceiptV1): JSONObject {
        val status = when (receipt.status) {
            EventMutationStatusV1.CREATED -> "committed"
            EventMutationStatusV1.STAGED -> "staged"
            EventMutationStatusV1.UPDATED, EventMutationStatusV1.DELETED,
            EventMutationStatusV1.NO_CHANGE -> "saved"
            EventMutationStatusV1.CONFLICT -> "conflict"
            EventMutationStatusV1.INVALID -> "invalid"
            EventMutationStatusV1.LOCKED -> "locked"
            EventMutationStatusV1.NEEDS_RESTORE -> "restore_required"
            EventMutationStatusV1.NOT_AUTHORIZED -> "not_authorized"
            EventMutationStatusV1.FAILED -> "failed"
            EventMutationStatusV1.DUPLICATE_REQUEST_ID -> "duplicate_request_id"
        }
        val detail = when (receipt.status) {
            EventMutationStatusV1.STAGED -> receipt.draftId?.toString() ?: receipt.message
            EventMutationStatusV1.CREATED -> receipt.eventSeq?.toString() ?: receipt.message
            EventMutationStatusV1.UPDATED, EventMutationStatusV1.NO_CHANGE ->
                receipt.revision?.toString() ?: receipt.message
            EventMutationStatusV1.DELETED -> receipt.eventSeq?.toString() ?: receipt.message
            else -> receipt.message ?: receipt.revision?.toString()
                ?: receipt.draftId?.toString() ?: receipt.eventSeq?.toString()
        }
        return result(receipt.requestId, status, detail)
            .put("featureRevision", eventFeatures.featureRevision)
            .put("refreshRequired", receipt.refreshRequired)
            .put("duplicate", receipt.duplicate)
            .put("eventSeq", receipt.eventSeq ?: JSONObject.NULL)
            .put("revision", receipt.revision ?: JSONObject.NULL)
            .put("draftId", receipt.draftId ?: JSONObject.NULL)
            .put("draftVersion", receipt.draftVersion ?: JSONObject.NULL)
            .put("relatedEventSeq", receipt.relatedEventSeq ?: JSONObject.NULL)
    }

    private fun result(receipt: DraftReviewReceiptV1): JSONObject {
        val status = if (receipt.errorCode == "SOURCE_REVISION_STALE") "stale" else when (receipt.status) {
            DraftReviewReceiptStatusV1.APPLIED, DraftReviewReceiptStatusV1.NO_CHANGE -> "applied"
            DraftReviewReceiptStatusV1.REVIEW_REQUIRED, DraftReviewReceiptStatusV1.CONFLICT -> "conflict"
            DraftReviewReceiptStatusV1.RESTORE_REQUIRED -> "restore_required"
            DraftReviewReceiptStatusV1.LOCKED -> "locked"
            DraftReviewReceiptStatusV1.NOT_FOUND -> "missing"
            DraftReviewReceiptStatusV1.INVALID -> "invalid"
            DraftReviewReceiptStatusV1.FAILED -> "failed"
            DraftReviewReceiptStatusV1.DUPLICATE_REQUEST_ID -> "duplicate_request_id"
        }
        return result(receipt.requestId, status,
            receipt.message ?: receipt.errorCode ?: receipt.draftId?.toString())
            .put("featureRevision", receipt.featureRevision)
            .put("duplicate", receipt.duplicate)
            .put("eventSeq", receipt.eventSeq ?: JSONObject.NULL)
            .put("revision", receipt.revision ?: JSONObject.NULL)
            .put("draftId", receipt.draftId ?: JSONObject.NULL)
            .put("draftVersion", receipt.draftVersion ?: JSONObject.NULL)
    }

    private fun clearDeckSearch() {
        deckSearchQuery = ""
        formalSearchResults = emptyList()
        retiredOffset = 0
        retiredTotal = 0
        retiredResults = emptyList()
        retiredLoaded = false
    }

    private suspend fun loadRetiredPage(requestedOffset: Int): Boolean {
        var offset = requestedOffset / retiredPageSize * retiredPageSize
        var page = withContext(Dispatchers.IO) {
            store.retiredPage(offset = offset, limit = retiredPageSize)
        }
        if (page.total > 0 && offset >= page.total) {
            offset = (page.total - 1) / retiredPageSize * retiredPageSize
            page = withContext(Dispatchers.IO) {
                store.retiredPage(offset = offset, limit = retiredPageSize)
            }
        } else if (page.total == 0) {
            offset = 0
        }
        retiredOffset = offset
        retiredTotal = page.total
        retiredResults = page.records
        retiredLoaded = true
        return true
    }

    private suspend fun loadRestoreTombstoneIfNeeded() {
        val prompt = deck.state.restore ?: run {
            restoreTombstone = null
            return
        }
        restoreTombstone = withContext(Dispatchers.IO) {
            synchronized(store) {
                store.getIncludingRetired(prompt.eventSeq)?.takeIf {
                    store.isRetired(prompt.eventSeq)
                }
            }
        }
    }

    private fun editorMatches(id: String) = session != null && sessionId == id
    private fun deckState(): JSONObject {
        newEventSession?.let { active ->
            val preview = active.previewCard()
            val items = org.json.JSONArray().put(JSONObject()
                .put("draftId", preview.draftId)
                .put("eventSeq", active.snapshot.eventSeq ?: JSONObject.NULL)
                .put("draftVersion", active.version)
                .put("title", preview.form.title.take(240))
                .put("kind", preview.form.kind.name)
                .put("status", preview.status.name))
            return JSONObject().put("open", deckOpen)
                .put("transient", true)
                .put("lastDiscardId", JSONObject.NULL)
                .put("cards", items).put("truncated", false)
                .put("cardCount", 1).put("position", 1)
                .put("canGoPrevious", false).put("canGoNext", false)
                .put("searchQuery", "").put("formalMatches", org.json.JSONArray())
                .put("currentId", active.clientDraftId)
                .put("version", active.version).put("dirty", active.meaningfulDiff)
                .put("busy", false).put("aiLocked", false)
                .put("error", active.lastError)
                .put("notice", JSONObject.NULL).put("completion", JSONObject.NULL)
                .put("form", WebEditWire.form(active.form))
                .put("hasConflict", false).put("hasRestore", false)
                .put("conflict", JSONObject.NULL).put("restore", JSONObject.NULL)
                .put("retiredResults", org.json.JSONArray())
                .put("retiredOffset", 0).put("retiredTotal", 0)
                .put("retiredHasPrevious", false).put("retiredHasNext", false)
                .put("retiredLoaded", false)
        }
        val state = deck.state
        val items = org.json.JSONArray()
        val coreCards = state.cards.map(DraftReviewCardV1::toCoreDraftCard)
        val matches = if (deckSearchQuery.isBlank()) coreCards
            else DraftQueueSearch.filter(coreCards, deckSearchQuery)
        val cardLimit = if (deckSearchQuery.isBlank()) 80 else 4
        matches.take(cardLimit).forEach { card ->
            items.put(JSONObject().put("draftId",card.draftId)
                .put("eventSeq",card.eventSeq).put("draftVersion",card.draftVersion)
                .put("title",card.form.title.take(240))
                .put("kind",card.form.kind.name).put("status",card.status.name))
        }
        val formalItems = org.json.JSONArray()
        formalSearchResults.take(4).forEach { record ->
            formalItems.put(JSONObject()
                .put("eventSeq", record.eventSeq)
                .put("revision", record.revision)
                .put("title", record.title.take(240))
                .put("kind", record.kind.name))
        }
        val retiredItems = org.json.JSONArray()
        retiredResults.forEach { record ->
            val form = DraftForm.from(record)
            retiredItems.put(JSONObject()
                .put("eventSeq", record.eventSeq)
                .put("revision", record.revision)
                .put("title", record.title.take(240))
                .put("kind", record.kind.name)
                .put("startText", form.startText.take(40))
                .put("endText", form.endText.take(40)))
        }
        val conflict = state.conflict?.let { prompt ->
            JSONObject()
                .put("draftId", prompt.draftId)
                .put("eventSeq", prompt.eventSeq)
                .put("draftVersion", prompt.card.draftVersion)
                .put("currentRevision", prompt.currentRevision ?: JSONObject.NULL)
                .put("operation", prompt.card.operation.name)
                .put("current", prompt.current?.let { WebEditWire.form(DraftForm.from(it.toCoreTemporalRecord())) }
                    ?: JSONObject.NULL)
                .put("proposed", WebEditWire.form(prompt.approvedForm))
                .put("changes", org.json.JSONArray().apply {
                    prompt.changes.forEach { change ->
                        put(JSONObject().put("label", change.label)
                            .put("before", change.before).put("after", change.after))
                    }
                })
        } ?: JSONObject.NULL
        val restore = state.restore?.let { prompt ->
            val tombstone = restoreTombstone?.takeIf { it.eventSeq == prompt.eventSeq }
            JSONObject()
                .put("eventSeq", prompt.eventSeq)
                .put("draftId", prompt.card.draftId)
                .put("draftVersion", state.version)
                .put("expectedRevision", tombstone?.revision ?: JSONObject.NULL)
                .put("message", prompt.message)
                .put("current", tombstone?.let { WebEditWire.form(DraftForm.from(it)) }
                    ?: JSONObject.NULL)
                .put("proposed", state.form?.let(WebEditWire::form) ?: JSONObject.NULL)
        } ?: JSONObject.NULL
        val completion = state.completion?.let { value ->
            JSONObject().put("published", value.published)
                .put("discarded", value.discarded).put("conflicts", value.conflicts)
        } ?: JSONObject.NULL
        return JSONObject().put("open",deckOpen)
            .put("transient",false)
            .put("lastDiscardId",state.lastDiscardId)
            .put("cards",items).put("truncated",matches.size>cardLimit)
            .put("searchTruncated",deckSearchQuery.isNotBlank() && matches.size>cardLimit)
            .put("cardCount",state.cards.size)
            .put("position",if(state.cards.isEmpty()) 0 else state.position+1)
            .put("canGoPrevious",state.canGoPrevious)
            .put("canGoNext",state.canGoNext)
            .put("searchQuery",deckSearchQuery).put("formalMatches",formalItems)
            .put("currentId",state.currentId)
            .put("version",state.version).put("dirty",state.dirty)
                .put("stateRevision",state.stateRevision)
            .put("busy",state.busy).put("aiLocked",state.aiLocked)
            .put("error",state.error).put("notice",state.notice)
            .put("completion",completion)
            .put("form",state.form?.let(WebEditWire::form))
            .put("hasConflict",state.conflict!=null)
            .put("hasRestore",state.restore!=null)
            .put("conflict",conflict).put("restore",restore)
            .put("retiredResults",retiredItems)
            .put("retiredOffset",retiredOffset).put("retiredTotal",retiredTotal)
            .put("retiredHasPrevious",retiredOffset>0)
            .put("retiredHasNext",retiredOffset+retiredResults.size<retiredTotal)
            .put("retiredLoaded",retiredLoaded)
    }

    /** Snapshots contain no lease token or reference to raw PowerStore objects. */
    fun snapshot(): JSONObject {
        val current=session
        return JSONObject()
            .put("editor", JSONObject().put("open",current!=null)
                .put("sessionId",sessionId)
                .put("eventSeq",current?.eventSeq)
                .put("form",current?.form?.let(WebEditWire::form))
                .put("requiresRecurrenceScope",current?.requiresRecurrenceScope ?: false)
                .put("canEditSelectedOccurrence",current?.canEditSelectedOccurrence ?: false)
                .put("recurrenceScope",current?.recurrenceScope?.name ?: JSONObject.NULL)
                .put("awaitingConfirmation",pendingWriteReview != null)
                .put("dirty",current?.meaningfulDiff ?: false)
                .put("error",current?.lastError))
            .put("deck",deckState())
            .put("lastResult",latest)
    }

    private suspend fun stageTimelineMove(
        cmd: WebEditCommand.MoveTimelineEvent,
        context: TimelineMoveContext?
    ): JSONObject {
        if (context == null)
            return result(cmd.requestId, "unsupported", "只能从时间轴移动定时事件")
        if (session != null || newEventSession != null || externalEditorActive() || deckOpen)
            return result(cmd.requestId, "locked", "请先关闭当前编辑器或草稿")
        if (withContext(Dispatchers.IO) { store.isLocked() })
            return result(cmd.requestId, "locked", "当前有受保护的业务操作")

        val record = withContext(Dispatchers.IO) { store.get(cmd.eventSeq) }
            ?: return result(cmd.requestId, "stale", "事件已不存在")
        if (record.revision != cmd.expectedRevision)
            return result(cmd.requestId, "stale", "事件版本已变化，请刷新时间轴")
        if (context.visibleEventRevisions[cmd.eventSeq] != cmd.expectedRevision)
            return result(cmd.requestId, "stale", "事件已离开当前时间轴投影，请刷新后重试")
        if (record.recurrence.isRepeating)
            return result(cmd.requestId, "unsupported", "重复事件暂不支持拖动；请打开卡片选择编辑范围")
        if (record.kind == TemporalKind.MEMO ||
            (record.kind == TemporalKind.DEADLINE && record.isDateOnlyDeadline()))
            return result(cmd.requestId, "unsupported", "此类事项没有可拖动的时间轴时刻")

        val currentAnchor = record.anchorMillis()
            ?: return result(cmd.requestId, "unsupported", "事件没有可移动的时间锚点")
        val snappedTarget = TimelineCoordinate.snap(
            cmd.targetMillis, context.camera.hours, context.zone
        )
        val targetYear = Instant.ofEpochMilli(snappedTarget).atZone(record.zone).year
        if (targetYear !in 1900..2200)
            return result(cmd.requestId, "invalid", "移动后的日期超出支持范围")
        if (snappedTarget == currentAnchor)
            return result(cmd.requestId, "unchanged", "事件已位于该时间")

        val form = movedDraftForm(record, snappedTarget)
        val staged = withContext(Dispatchers.IO) {
            eventFeatures.stageFormalReplacement(
                record.eventSeq, cmd.expectedRevision, form.toEventFormV1(), cmd.requestId
            )
        }
        if (staged.status != EventMutationStatusV1.STAGED) return result(staged)
        val draftId = staged.draftId ?: return result(cmd.requestId, "failed", "草稿暂存回执缺少 ID")
        // The formal event is untouched; the existing queue owns the replacement candidate.
        deck.open(draftId)
        deckOpen = true
        return result(cmd.requestId, "staged", draftId.toString())
    }

    suspend fun execute(
        cmd: WebEditCommand,
        newEventZone: ZoneId = ZoneId.systemDefault(),
        newEventSelection: EditorSelection? = null,
        timelineMoveContext: TimelineMoveContext? = null
    ): JSONObject = gate.withLock {
        completed[cmd.requestId]?.let { (original, reply) ->
            // A caller cannot rebind an already seen request ID to a different write.
            return@withLock if(original==cmd) JSONObject(reply.toString())
                else result(cmd.requestId,"duplicate_request_id")
        }
        if (pendingWriteReview != null) {
            val reply=result(cmd.requestId,"locked","请先处理宿主确认")
            completed[cmd.requestId]=cmd to JSONObject(reply.toString())
            if(completed.size>64)completed.remove(completed.keys.first())
            latest=reply
            return@withLock reply
        }
        val reply = try {
            when(cmd) {
                is WebEditCommand.OpenEvent -> {
                    if(session!=null || externalEditorActive() || deckOpen)
                        result(cmd.requestId,"locked")
                    else when (val opened = withContext(Dispatchers.IO) {
                        eventFeatures.openFormalEvent(cmd.eventSeq, cmd.revision, cmd.occurrenceKey)
                    }) {
                        is EventEditorOpenResultV1.Opened -> {
                            val editor = opened.editor
                            session = WebFormalEditor(
                                editor.handle, editor, editor.form.toCoreDraftForm()
                            )
                            sessionId=UUID.randomUUID().toString()
                            result(cmd.requestId,"opened")
                        }
                        is EventEditorOpenResultV1.Stale ->
                            result(cmd.requestId,"stale", opened.currentRevision?.toString())
                        EventEditorOpenResultV1.Missing ->
                            result(cmd.requestId,"stale")
                        EventEditorOpenResultV1.Locked ->
                            result(cmd.requestId,"locked")
                        is EventEditorOpenResultV1.Invalid ->
                            result(cmd.requestId,"invalid", opened.message)
                    }
                }
                is WebEditCommand.MoveTimelineEvent ->
                    stageTimelineMove(cmd, timelineMoveContext)
                is WebEditCommand.SelectScope -> {
                    val current=session
                    if(!editorMatches(cmd.sessionId) || current==null) result(cmd.requestId,"stale")
                    else if(!current.requiresRecurrenceScope || current.meaningfulDiff ||
                        (cmd.scope != RecurrenceEditScope.WHOLE_SERIES && !current.canEditSelectedOccurrence))
                        result(cmd.requestId,"invalid","请先放弃修改并选择有效的重复范围")
                    else {
                        val updated = withContext(Dispatchers.IO) {
                            eventFeatures.selectFormalRecurrenceScope(
                                current.handle, EventRecurrenceScopeV1.valueOf(cmd.scope.name)
                            )
                        }
                        if (updated.status == EventEditorUpdateStatusV1.UPDATED) {
                            eventFeatures.editorSnapshot(current.handle)?.let { current.snapshot = it }
                            current.form = current.snapshot.form.toCoreDraftForm()
                            result(cmd.requestId,"selected")
                        } else result(cmd.requestId,"invalid",updated.message)
                    }
                }
                is WebEditCommand.DiscardEventChanges -> {
                    if(!editorMatches(cmd.sessionId)) result(cmd.requestId,"stale")
                    else {
                        val active = session!!
                        val updated = withContext(Dispatchers.IO) {
                            eventFeatures.discardFormalChanges(active.handle)
                        }
                        eventFeatures.editorSnapshot(active.handle)?.let { active.snapshot = it }
                        active.form = active.snapshot.form.toCoreDraftForm()
                        if (updated.status == EventEditorUpdateStatusV1.UPDATED)
                            result(cmd.requestId,"discarded_changes")
                        else result(cmd.requestId,"failed",updated.message)
                    }
                }
                is WebEditCommand.PatchEvent -> {
                    if(!editorMatches(cmd.sessionId)) result(cmd.requestId,"stale")
                    else if(session!!.requiresRecurrenceScope && session!!.recurrenceScope == null)
                        result(cmd.requestId,"invalid","请先选择重复事件的编辑范围")
                    else if(session!!.recurrenceScope == RecurrenceEditScope.SINGLE_OCCURRENCE &&
                        cmd.field !in setOf("title","startText","endText"))
                        result(cmd.requestId,"invalid","仅本次只能修改标题和时间")
                    else {
                        val active = session!!
                        val patched = WebEditWire.patch(active.form,cmd.field,cmd.value)
                        val updated = withContext(Dispatchers.IO) {
                            eventFeatures.updateFormalEvent(active.handle, patched.toEventFormV1())
                        }
                        if (updated.status == EventEditorUpdateStatusV1.UPDATED) {
                            active.form = patched
                            eventFeatures.editorSnapshot(active.handle)?.let { active.snapshot = it }
                            result(cmd.requestId,"edited")
                        } else result(cmd.requestId,"failed",updated.message)
                    }
                }
                is WebEditCommand.SaveEvent -> {
                    if(!editorMatches(cmd.sessionId)) result(cmd.requestId,"stale")
                    else if(session!!.requiresRecurrenceScope && session!!.recurrenceScope == null)
                        result(cmd.requestId,"invalid","请先选择重复事件的编辑范围")
                    else if(session!!.meaningfulDiff) {
                        val active=session!!
                        val current=withContext(Dispatchers.IO){store.get(active.eventSeq)}
                        val scope=active.recurrenceScope
                        val selected=active.selectedOccurrenceKey
                        val occurrence=if((scope == RecurrenceEditScope.SINGLE_OCCURRENCE ||
                                scope == RecurrenceEditScope.THIS_AND_FUTURE) && selected != null)
                            current?.let { withContext(Dispatchers.IO){RecurrenceEngine.occurrenceForKey(it,selected)} }
                        else null
                        if(current==null) result(cmd.requestId,"stale")
                        else if((scope == RecurrenceEditScope.SINGLE_OCCURRENCE ||
                                scope == RecurrenceEditScope.THIS_AND_FUTURE) && occurrence==null)
                            result(cmd.requestId,"invalid","所选重复实例不存在")
                        else {
                            val before=occurrence?.let { occurrenceDisplayRecord(current,it) } ?: current
                            val operation=if(current.kind==active.form.kind)
                                DraftOperation.REPLACE else DraftOperation.CONVERT
                            pendingWriteReview=WebFormalWriteReview(
                                token=UUID.randomUUID().toString(), command=cmd,
                                operation=operation, current=current, before=before,
                                proposed=active.form, scope=scope, occurrenceKey=selected,
                                editorSession=active)
                            result(cmd.requestId,"confirmation_required")
                        }
                    }
                    else if(session!!.recurrenceScope == RecurrenceEditScope.THIS_AND_FUTURE)
                        result(cmd.requestId,"invalid","内容没有有效修改，未拆分重复序列")
                    else result(withContext(Dispatchers.IO) {
                        val active = session!!
                        eventFeatures.saveFormalEvent(
                            active.handle, active.form.toEventFormV1(), cmd.requestId
                        )
                    })
                }
                is WebEditCommand.DeleteEvent -> {
                    if (!editorMatches(cmd.sessionId)) result(cmd.requestId, "stale")
                    else {
                        val active = session!!
                        val current = withContext(Dispatchers.IO) { store.get(active.eventSeq) }
                        if (current == null) result(cmd.requestId, "stale")
                        else {
                            pendingWriteReview = WebFormalWriteReview(
                                token = UUID.randomUUID().toString(),
                                command = cmd,
                                operation = DraftOperation.DELETE,
                                current = current,
                                before = current,
                                proposed = null,
                                editorSession = active,
                                editorFormSnapshot = active.form
                            )
                            result(cmd.requestId, "confirmation_required")
                        }
                    }
                }
                is WebEditCommand.CloseEvent -> {
                    if(!editorMatches(cmd.sessionId)) result(cmd.requestId,"stale")
                    else {
                        val old=session!!
                        val cleanStaleSession = !old.meaningfulDiff &&
                            !withContext(Dispatchers.IO) { eventFeatures.isFormalEventCurrent(old.handle) }
                        val checkpoint = if (cleanStaleSession) null else
                            withContext(Dispatchers.IO) {
                                eventFeatures.checkpointFormalEvent(old.handle, cmd.requestId)
                            }
                        if(!cleanStaleSession && checkpoint != null && checkpoint.status in setOf(
                                EventMutationStatusV1.FAILED,
                                EventMutationStatusV1.CONFLICT,
                                EventMutationStatusV1.INVALID
                            ))
                            result(cmd.requestId,"failed",checkpoint.message ?: old.lastError)
                        else {
                            withContext(Dispatchers.IO){eventFeatures.closeFormalEvent(old.handle)}
                            session=null;sessionId=null
                            result(cmd.requestId,"closed",checkpoint?.draftId?.toString())
                        }
                    }
                }
                is WebEditCommand.CreateDeck -> {
                    if(session!=null || externalEditorActive() || deckOpen)
                        result(cmd.requestId,"locked")
                    else if(withContext(Dispatchers.IO) { store.isLocked() })
                        result(cmd.requestId,"locked")
                    else {
                        val selection = when (newEventSelection) {
                            is EditorSelection.AtInstant ->
                                EventSelectionV1(instantMillis = newEventSelection.millis)
                            is EditorSelection.OnDate ->
                                EventSelectionV1(localDate = newEventSelection.date.toString())
                            null -> null
                        }
                        when (val opened = eventFeatures.openNewEvent(
                            EventKindV1.valueOf(cmd.kind.name), newEventZone.id, selection
                        )) {
                            is EventEditorOpenResultV1.Opened -> {
                                val editor = opened.editor
                                clearDeckSearch()
                                newEventSession = WebNewEventEditor(
                                    editor.handle, editor, editor.form.toCoreDraftForm()
                                )
                                deckOpen = true
                                result(cmd.requestId,"created",editor.clientDraftId.toString())
                            }
                            is EventEditorOpenResultV1.Invalid ->
                                result(cmd.requestId,"invalid",opened.message)
                            EventEditorOpenResultV1.Locked -> result(cmd.requestId,"locked")
                            EventEditorOpenResultV1.Missing, is EventEditorOpenResultV1.Stale ->
                                result(cmd.requestId,"failed","无法创建事件编辑器")
                        }
                    }
                }
                is WebEditCommand.OpenDeck -> {
                    if(session!=null || externalEditorActive()) result(cmd.requestId,"locked")
                    else if(deckOpen) {
                        val activeNew = newEventSession
                        // Reopening a host-owned transient session must not reset its in-memory form.
                        if(activeNew != null && (cmd.draftId == null || cmd.draftId == activeNew.clientDraftId))
                            result(cmd.requestId,"opened")
                        else if(activeNew != null) result(cmd.requestId,"locked")
                        else if(cmd.draftId==null) {
                            loadRetiredPage(0)
                            loadRestoreTombstoneIfNeeded()
                            result(cmd.requestId,"opened")
                        }
                        else if(deck.select(cmd.draftId, cmd.requestId).succeeded) {
                            clearDeckSearch()
                            loadRetiredPage(0)
                            loadRestoreTombstoneIfNeeded()
                            result(cmd.requestId,"selected")
                        }
                        else result(cmd.requestId,"failed",deck.state.error)
                    } else {
                        clearDeckSearch()
                        deck.open(cmd.draftId);deckOpen=true
                        if (newEventSession == null) {
                            loadRetiredPage(0)
                            loadRestoreTombstoneIfNeeded()
                        }
                        result(cmd.requestId,"opened")
                    }
                }
                is WebEditCommand.SearchDeck -> {
                    when {
                        !deckOpen -> result(cmd.requestId,"missing")
                        newEventSession != null -> result(cmd.requestId,"unsupported")
                        else -> {
                            deckSearchQuery = cmd.query
                            formalSearchResults = if (cmd.query.isBlank()) emptyList()
                                else withContext(Dispatchers.IO) {
                                    eventFeatures.searchEvents(cmd.query).hits.take(4)
                                }
                            result(cmd.requestId,"searched")
                        }
                    }
                }
                is WebEditCommand.LoadRetiredPage -> {
                    when {
                        !deckOpen -> result(cmd.requestId,"missing")
                        newEventSession != null -> result(cmd.requestId,"unsupported")
                        deck.state.aiLocked || deck.state.busy -> result(cmd.requestId,"locked")
                        else -> {
                            loadRetiredPage(cmd.offset)
                            result(cmd.requestId,"loaded")
                        }
                    }
                }
                is WebEditCommand.JumpDeck -> {
                    when {
                        !deckOpen -> result(cmd.requestId,"missing")
                        newEventSession != null -> result(cmd.requestId,"unsupported")
                        deck.state.aiLocked || deck.state.busy -> result(cmd.requestId,"locked")
                        deck.jump(cmd.steps, cmd.requestId).succeeded -> {
                            clearDeckSearch()
                            result(cmd.requestId,"moved")
                        }
                        else -> result(cmd.requestId,"failed",deck.state.error)
                    }
                }
                is WebEditCommand.ProposeReplaceDeck -> {
                    when {
                        !deckOpen || newEventSession != null -> result(cmd.requestId,"missing")
                        session != null || externalEditorActive() -> result(cmd.requestId,"locked")
                        withContext(Dispatchers.IO) { store.isLocked() } -> result(cmd.requestId,"locked")
                        formalSearchResults.none {
                            it.eventSeq == cmd.eventSeq && it.revision == cmd.expectedRevision
                        } -> result(cmd.requestId,"stale")
                        else -> {
                            val proposal = deck.beginFormalReplacement(
                                cmd.eventSeq, cmd.expectedRevision, cmd.requestId
                            )
                            if (proposal.status == DraftReviewReceiptStatusV1.APPLIED) {
                                clearDeckSearch()
                                result(cmd.requestId, "opened", proposal.draftId?.toString())
                            } else result(proposal)
                        }
                    }
                }
                is WebEditCommand.BeginRestoreDeck -> {
                    val state = deck.state
                    val listed = retiredResults.firstOrNull {
                        it.eventSeq == cmd.eventSeq && it.revision == cmd.expectedRevision
                    }
                    val stillRetired = if (listed == null) false else withContext(Dispatchers.IO) {
                        synchronized(store) {
                            store.isRetired(cmd.eventSeq) &&
                                store.getIncludingRetired(cmd.eventSeq) == listed
                        }
                    }
                    when {
                        !deckOpen || newEventSession != null -> result(cmd.requestId,"missing")
                        session != null || externalEditorActive() -> result(cmd.requestId,"locked")
                        state.aiLocked || state.busy ||
                            withContext(Dispatchers.IO) { store.isLocked() } -> result(cmd.requestId,"locked")
                        !stillRetired -> result(cmd.requestId,"stale")
                        state.conflict != null || state.restore != null -> result(cmd.requestId,"locked")
                        else -> {
                            val opened = deck.beginRestore(
                                cmd.eventSeq, cmd.expectedRevision, cmd.requestId
                            )
                            val card = deck.state.current
                            if (opened.status != DraftReviewReceiptStatusV1.APPLIED || card == null)
                                result(opened)
                            else {
                                clearDeckSearch()
                                restoreTombstone = null
                                if (card.baseRevision != cmd.expectedRevision)
                                    result(cmd.requestId,"updated",card.draftId.toString())
                                else result(cmd.requestId,"opened",card.draftId.toString())
                            }
                        }
                    }
                }
                is WebEditCommand.KeepConflictDeck -> keepConflict(cmd)
                is WebEditCommand.RequestConflictReviewDeck -> requestConflictReview(cmd)
                is WebEditCommand.KeepRestoreDeck -> keepRestore(cmd)
                is WebEditCommand.RequestRestoreReviewDeck -> requestRestoreReview(cmd)
                is WebEditCommand.PatchDeck -> {
                    val activeNew = newEventSession
                    if(activeNew != null) {
                        if(!deckOpen || activeNew.clientDraftId!=cmd.draftId ||
                            activeNew.version!=cmd.draftVersion) result(cmd.requestId,"stale")
                        else if(withContext(Dispatchers.IO) { store.isLocked() }) result(cmd.requestId,"locked")
                        else {
                            val patched = WebEditWire.patch(activeNew.form,cmd.field,cmd.value)
                            val update = eventFeatures.updateNewEvent(
                                activeNew.handle, patched.toEventFormV1()
                            )
                            if (update.status == EventEditorUpdateStatusV1.UPDATED) {
                                activeNew.form = patched
                                eventFeatures.editorSnapshot(activeNew.handle)?.let {
                                    activeNew.snapshot = it
                                }
                                result(cmd.requestId,"edited")
                            } else result(cmd.requestId,"failed",update.message)
                        }
                    } else {
                        val state=deck.state
                        if(!deckOpen || state.currentId!=cmd.draftId ||
                            state.version!=cmd.draftVersion)result(cmd.requestId,"stale")
                        else if(state.aiLocked || state.busy)result(cmd.requestId,"locked")
                        else {
                            val form=state.form?.toCoreDraftForm()
                            if(form==null) result(cmd.requestId,"missing")
                            else {
                                val updated = WebEditWire.patch(form,cmd.field,cmd.value)
                                val edited = deck.editCurrentDraft(
                                    cmd.draftId, cmd.draftVersion,
                                    cmd.stateRevision ?: state.stateRevision,
                                    updated.toEventFormV1()
                                )
                                if (edited.succeeded) result(cmd.requestId,"edited")
                                else result(edited)
                            }
                        }
                    }
                }
                is WebEditCommand.SelectDeck -> {
                    val activeNew = newEventSession
                    if(!deckOpen) result(cmd.requestId,"missing")
                    else if(activeNew != null && activeNew.clientDraftId == cmd.draftId)
                        result(cmd.requestId,"selected")
                    else if(activeNew != null) result(cmd.requestId,"missing")
                    else if(deck.select(cmd.draftId, cmd.requestId).succeeded) {
                        clearDeckSearch()
                        result(cmd.requestId,"selected")
                    }
                    else result(cmd.requestId,"failed",deck.state.error)
                }
                is WebEditCommand.FlushDeck -> {
                    val activeNew = newEventSession
                    if(!deckOpen)result(cmd.requestId,"missing")
                    else if(activeNew != null) {
                        val checkpoint = withContext(Dispatchers.IO) {
                            eventFeatures.checkpointNewEvent(activeNew.handle, cmd.requestId)
                        }
                        if (checkpoint.status in setOf(
                                EventMutationStatusV1.FAILED,
                                EventMutationStatusV1.CONFLICT,
                                EventMutationStatusV1.INVALID
                            ))
                            result(cmd.requestId,"failed",checkpoint.message)
                        else {
                            eventFeatures.editorSnapshot(activeNew.handle)?.let {
                                activeNew.snapshot = it
                            }
                            result(cmd.requestId,"flushed")
                        }
                    } else {
                        val checkpoint = deck.flush(cmd.requestId)
                        if (checkpoint.succeeded) result(cmd.requestId,"flushed")
                        else result(checkpoint)
                    }
                }
                is WebEditCommand.CloseDeck -> {
                    val activeNew = newEventSession
                    if(!deckOpen)result(cmd.requestId,"closed")
                    else if(activeNew != null) {
                        val saved = withContext(Dispatchers.IO) {
                            eventFeatures.closeNewEvent(activeNew.handle)
                        }
                        if (saved.status in setOf(
                                EventMutationStatusV1.FAILED,
                                EventMutationStatusV1.CONFLICT,
                                EventMutationStatusV1.INVALID
                            ))
                            return@withLock result(cmd.requestId,"failed",saved.message)
                        newEventSession = null
                        deckOpen = false
                        clearDeckSearch()
                        runCatching { deck.open(saved.draftId) }
                        result(cmd.requestId,"closed",saved.draftId?.toString())
                    } else {
                        val flushed = deck.flush(cmd.requestId)
                        if (flushed.succeeded) {
                            deckOpen=false
                            clearDeckSearch()
                            result(cmd.requestId,"closed")
                        } else result(flushed)
                    }
                }
                is WebEditCommand.CommitDeck -> requestCommitReview(cmd)
                is WebEditCommand.DiscardDeck -> {
                    val activeNew = newEventSession
                    if(activeNew != null) {
                        if(!deckOpen || activeNew.clientDraftId!=cmd.draftId ||
                            activeNew.version!=cmd.draftVersion) result(cmd.requestId,"stale")
                        else if(withContext(Dispatchers.IO) {
                                eventFeatures.discardNewEvent(activeNew.handle)
                            }) {
                            newEventSession = null
                            deckOpen = false
                            clearDeckSearch()
                            runCatching { deck.open() }
                            result(cmd.requestId,"discarded")
                        } else result(cmd.requestId,"failed",activeNew.lastError)
                    } else {
                        val s=deck.state
                        if(!deckOpen || s.currentId!=cmd.draftId ||
                            s.version!=cmd.draftVersion) result(cmd.requestId,"stale")
                        else {
                            val discarded = deck.discard(cmd.draftId, s.current?.draftVersion ?: -1L,
                                cmd.requestId)
                            if (discarded.succeeded) result(cmd.requestId,"discarded")
                            else result(discarded)
                        }
                    }
                }
                is WebEditCommand.UndoDeck ->
                    if(!deckOpen)result(cmd.requestId,"missing")
                    else if(newEventSession != null) result(cmd.requestId,"missing")
                    else deck.state.lastDiscardId?.let { draftId ->
                        val undone = deck.undoDiscard(draftId, cmd.requestId)
                        if (undone.succeeded) result(cmd.requestId,"restored") else result(undone)
                    } ?: result(cmd.requestId,"missing")
            }
        } catch(e:kotlinx.coroutines.CancellationException) {throw e}
          catch(e:DraftSourceRevisionMismatchException) {
              result(cmd.requestId,"stale",e.message ?: "事件版本已变化，请刷新后重试")
          }
          catch(e:java.time.DateTimeException) {result(cmd.requestId,"invalid","Invalid date")}
          catch(e:IllegalArgumentException) {result(cmd.requestId,"invalid","Invalid field value")}
          catch(e:Exception){ result(cmd.requestId,"failed","Host operation failed; retry or recover") }
        hasActiveUiSession=session!=null || newEventSession!=null || deckOpen
        latest=reply
        completed[cmd.requestId]=cmd to JSONObject(reply.toString())
        if(completed.size>64)completed.remove(completed.keys.first())
        reply
    }

    private fun matchingConflict(
        draftId: Long, draftVersion: Long, eventSeq: Long, expectedRevision: Long
    ): DraftReviewConflictV1? = deck.state.conflict?.takeIf { prompt ->
        prompt.draftId == draftId && prompt.card.draftVersion == draftVersion &&
            prompt.eventSeq == eventSeq && prompt.currentRevision == expectedRevision &&
            deck.state.currentId == draftId && deck.state.version == draftVersion
    }

    private fun matchingRestore(
        draftId: Long, draftVersion: Long, eventSeq: Long, expectedRevision: Long
    ): DraftReviewRestoreV1? = deck.state.restore?.takeIf { prompt ->
        prompt.card.draftId == draftId && deck.state.currentId == draftId &&
            deck.state.version == draftVersion && prompt.eventSeq == eventSeq &&
            restoreTombstone?.eventSeq == eventSeq &&
            restoreTombstone?.revision == expectedRevision
    }

    private fun matchesPersistedConflictDraft(
        persisted: DraftCard?, prompt: DraftReviewConflictV1
    ): Boolean = persisted != null && persisted.status == DraftStatus.CONFLICT &&
        persisted.draftId == prompt.card.draftId && persisted.eventSeq == prompt.card.eventSeq &&
        persisted.queueOrder == prompt.card.queueOrder &&
        persisted.operation.name == prompt.card.operation.name &&
        persisted.form == prompt.approvedForm.toCoreDraftForm() &&
        persisted.baseRevision == prompt.card.baseRevision &&
        persisted.draftVersion == prompt.card.draftVersion

    private fun keepConflict(cmd: WebEditCommand.KeepConflictDeck): JSONObject {
        if (!deckOpen || matchingConflict(cmd.draftId, cmd.draftVersion, cmd.eventSeq,
                cmd.expectedRevision) == null) return result(cmd.requestId, "stale")
        deck.dismissConflict()
        return result(cmd.requestId, "kept")
    }

    private suspend fun requestConflictReview(
        cmd: WebEditCommand.RequestConflictReviewDeck
    ): JSONObject {
        val state = deck.state
        val prompt = matchingConflict(cmd.draftId, cmd.draftVersion, cmd.eventSeq,
            cmd.expectedRevision) ?: return result(cmd.requestId, "stale")
        if (!deckOpen || newEventSession != null || session != null || externalEditorActive())
            return result(cmd.requestId, "locked")
        if (state.aiLocked || state.busy || state.dirty ||
            withContext(Dispatchers.IO) { store.isLocked() }) return result(cmd.requestId, "locked")
        val live = withContext(Dispatchers.IO) {
            synchronized(store) {
                store.get(prompt.eventSeq)?.takeIf { it.revision == cmd.expectedRevision }
            }
        } ?: return result(cmd.requestId, "stale", "正式事件已变化，请重新提交草稿以刷新差异")
        val persisted = withContext(Dispatchers.IO) { store.draft(prompt.draftId) }
        if (!matchesPersistedConflictDraft(persisted, prompt) || state.form != prompt.approvedForm)
            return result(cmd.requestId, "stale", "草稿内容已变化，请重新检查")
        pendingWriteReview = WebFormalWriteReview(
            token = UUID.randomUUID().toString(), command = cmd,
            operation = DraftOperation.valueOf(prompt.card.operation.name), current = live, before = live,
            proposed = prompt.approvedForm.toCoreDraftForm(), deckCard = persisted,
            deckForm = prompt.approvedForm.toCoreDraftForm(), deckVersion = state.version,
            deckStateRevision = deck.snapshot.stateRevision,
            conflictPrompt = prompt
        )
        return result(cmd.requestId, "confirmation_required")
    }

    private suspend fun keepRestore(cmd: WebEditCommand.KeepRestoreDeck): JSONObject {
        val prompt = matchingRestore(cmd.draftId, cmd.draftVersion, cmd.eventSeq,
            cmd.expectedRevision) ?: return result(cmd.requestId, "stale")
        if (!deckOpen || deck.state.aiLocked || deck.state.busy) return result(cmd.requestId, "locked")
        val unchanged = withContext(Dispatchers.IO) {
            synchronized(store) {
                store.isRetired(prompt.eventSeq) &&
                    store.getIncludingRetired(prompt.eventSeq) == restoreTombstone
            }
        }
        if (!unchanged || withContext(Dispatchers.IO) { store.draft(prompt.card.draftId) } !=
            prompt.card.toCoreDraftCard())
            return result(cmd.requestId, "stale", "墓碑或恢复草稿已变化")
        deck.dismissRestore()
        restoreTombstone = null
        return result(cmd.requestId, "kept")
    }

    private suspend fun requestRestoreReview(
        cmd: WebEditCommand.RequestRestoreReviewDeck
    ): JSONObject {
        val state = deck.state
        val prompt = matchingRestore(cmd.draftId, cmd.draftVersion, cmd.eventSeq,
            cmd.expectedRevision) ?: return result(cmd.requestId, "stale")
        if (!deckOpen || newEventSession != null || session != null || externalEditorActive())
            return result(cmd.requestId, "locked")
        if (state.aiLocked || state.busy || state.dirty ||
            withContext(Dispatchers.IO) { store.isLocked() }) return result(cmd.requestId, "locked")
        val tombstone = withContext(Dispatchers.IO) {
            synchronized(store) {
                store.getIncludingRetired(prompt.eventSeq)?.takeIf {
                    store.isRetired(prompt.eventSeq) && it.revision == cmd.expectedRevision
                }
            }
        } ?: return result(cmd.requestId, "stale", "已删除事件的版本已变化，请重新检查")
        val form = state.form?.toCoreDraftForm() ?: return result(cmd.requestId, "missing")
        val persisted = withContext(Dispatchers.IO) { store.draft(prompt.card.draftId) }
        if (persisted != prompt.card.toCoreDraftCard() ||
            form != prompt.card.form.toCoreDraftForm() || tombstone != restoreTombstone)
            return result(cmd.requestId, "stale", "恢复草稿或墓碑已变化，请重新检查")
        pendingWriteReview = WebFormalWriteReview(
            token = UUID.randomUUID().toString(), command = cmd,
            operation = DraftOperation.valueOf(prompt.card.operation.name), current = tombstone, before = tombstone,
            proposed = form, deckCard = prompt.card.toCoreDraftCard(), deckForm = form,
            deckVersion = state.version, deckStateRevision = deck.snapshot.stateRevision,
            restorePrompt = prompt
        )
        return result(cmd.requestId, "confirmation_required")
    }

    private suspend fun requestCommitReview(cmd: WebEditCommand.CommitDeck): JSONObject {
        val activeNew = newEventSession
        if (activeNew != null) {
            if (!deckOpen || activeNew.clientDraftId != cmd.draftId ||
                activeNew.version != cmd.draftVersion) return result(cmd.requestId, "stale")
            if (!activeNew.meaningfulDiff) return result(cmd.requestId, "no_changes")
            if (withContext(Dispatchers.IO) { store.isLocked() })
                return result(cmd.requestId, "locked",
                    "当前有受保护的业务操作，内容仍保留在编辑器中")
            val validation = eventFeatures.validateNewEvent(activeNew.handle)
            return when (validation.status) {
                dev.powerfulcalendar.core.api.v1.EventValidationStatusV1.READY -> {
                    pendingWriteReview = WebFormalWriteReview(
                        token = UUID.randomUUID().toString(), command = cmd,
                        operation = DraftOperation.CREATE, current = null, before = null,
                        proposed = activeNew.form, newEventSession = activeNew,
                        deckVersion = activeNew.version
                    )
                    result(cmd.requestId, "confirmation_required")
                }
                dev.powerfulcalendar.core.api.v1.EventValidationStatusV1.INCOMPLETE,
                dev.powerfulcalendar.core.api.v1.EventValidationStatusV1.INVALID -> {
                    val saved = withContext(Dispatchers.IO) {
                        eventFeatures.saveNewEvent(
                            activeNew.handle,
                            activeNew.form.toEventFormV1(),
                            cmd.requestId
                        )
                    }
                    eventFeatures.editorSnapshot(activeNew.handle)?.let {
                        activeNew.snapshot = it
                    }
                    if (saved.status == EventMutationStatusV1.FAILED &&
                        withContext(Dispatchers.IO) { store.isLocked() }) {
                        result(cmd.requestId, "locked",
                            "当前有受保护的业务操作，内容仍保留在编辑器中")
                    } else if (saved.status == EventMutationStatusV1.STAGED) {
                        result(cmd.requestId, "staged", activeNew.clientDraftId.toString())
                    } else result(saved)
                }
                dev.powerfulcalendar.core.api.v1.EventValidationStatusV1.NOT_FOUND ->
                    result(cmd.requestId, "stale", validation.message)
            }
        }

        val initial = deck.state
        if (!deckOpen || initial.currentId != cmd.draftId || initial.version != cmd.draftVersion)
            return result(cmd.requestId, "stale")
        if (initial.aiLocked || initial.busy) return result(cmd.requestId, "locked")
        val flush = deck.flush("${cmd.requestId}:flush")
        if (!flush.succeeded) return result(flush)

        val state = deck.state
        val card = state.current ?: return result(cmd.requestId, "missing")
        val form = state.form?.toCoreDraftForm() ?: return result(cmd.requestId, "missing")
        if (state.currentId != cmd.draftId || state.busy || state.aiLocked)
            return result(cmd.requestId, "locked")
        val current = if (card.operation.requiresExisting) {
            withContext(Dispatchers.IO) { store.get(card.eventSeq) }
        } else null
        val ready = form.build(card.eventSeq.coerceAtLeast(1L), (card.baseRevision ?: 0L) + 1L)
        val changedFormalValue = when {
            card.operation == dev.powerfulcalendar.core.api.v1.DraftOperationV1.DELETE -> true
            ready !is FormBuild.Ready -> false
            card.operation == dev.powerfulcalendar.core.api.v1.DraftOperationV1.CREATE -> true
            current == null -> false
            else -> form.hasMeaningfulDiffFrom(DraftForm.from(current))
        }
        if (!changedFormalValue) {
            return when {
                card.operation.requiresExisting && current == null ->
                    commitDeckResult(cmd.requestId, card.draftId, card.draftVersion,
                        deck.snapshot.stateRevision)
                ready !is FormBuild.Ready ->
                    commitDeckResult(cmd.requestId, card.draftId, card.draftVersion,
                        deck.snapshot.stateRevision)
                else -> result(cmd.requestId, "no_changes")
            }
        }
        if (card.operation.requiresExisting && current == null)
            return commitDeckResult(cmd.requestId, card.draftId, card.draftVersion,
                deck.snapshot.stateRevision)

        val persisted = withContext(Dispatchers.IO) { store.draft(card.draftId) }
            ?: return result(cmd.requestId, "stale")
        if (persisted.operation.name != card.operation.name || persisted.form != form ||
            persisted.draftVersion != state.version || state.dirty)
            return result(cmd.requestId, "conflict", "草稿在审查期间发生变化")
        pendingWriteReview = WebFormalWriteReview(
            token = UUID.randomUUID().toString(), command = cmd,
            operation = DraftOperation.valueOf(card.operation.name), current = current, before = current,
            proposed = if (card.operation == dev.powerfulcalendar.core.api.v1.DraftOperationV1.DELETE) null else form,
            deckCard = persisted, deckForm = form, deckVersion = state.version,
            deckStateRevision = deck.snapshot.stateRevision
        )
        return result(cmd.requestId, "confirmation_required")
    }

    private suspend fun commitDeckResult(
        requestId: String,
        draftId: Long,
        expectedDraftVersion: Long,
        expectedStateRevision: Long
    ): JSONObject {
        val committed = deck.commit(draftId, expectedDraftVersion, expectedStateRevision, requestId)
        if (committed.status == DraftReviewReceiptStatusV1.APPLIED) {
            restoreTombstone = null
            return result(requestId, "committed")
        }
        val restore = deck.state.restore
        if (restore != null) {
            restoreTombstone = withContext(Dispatchers.IO) {
                synchronized(store) {
                    store.getIncludingRetired(restore.eventSeq)?.takeIf {
                        store.isRetired(restore.eventSeq)
                    }
                }
            }
        } else restoreTombstone = null
        return result(requestId, when (committed.status) {
            DraftReviewReceiptStatusV1.REVIEW_REQUIRED, DraftReviewReceiptStatusV1.CONFLICT -> "conflict"
            DraftReviewReceiptStatusV1.RESTORE_REQUIRED -> "restore_required"
            DraftReviewReceiptStatusV1.LOCKED -> "locked"
            DraftReviewReceiptStatusV1.NOT_FOUND -> "missing"
            DraftReviewReceiptStatusV1.INVALID -> "invalid"
            else -> "failed"
        }, committed.message ?: committed.errorCode ?: deck.state.error ?: restore?.message)
    }

    private suspend fun resolveDeckCommit(review: WebFormalWriteReview): JSONObject {
        val command = review.command as? WebEditCommand.CommitDeck
            ?: return result(review.command.requestId, "stale")
        val transient = review.newEventSession
        if (transient != null) {
            if (newEventSession !== transient || !deckOpen ||
                transient.clientDraftId != command.draftId ||
                transient.version != review.deckVersion || transient.form != review.proposed)
                return result(command.requestId, "conflict", "新建内容已变化，请重新审查")
            val saved = withContext(Dispatchers.IO) {
                eventFeatures.saveNewEvent(
                    transient.handle, transient.form.toEventFormV1(), command.requestId
                )
            }
            eventFeatures.editorSnapshot(transient.handle)?.let { transient.snapshot = it }
            if (saved.status == EventMutationStatusV1.CREATED) {
                newEventSession = null
                deckOpen = false
                runCatching { deck.open() }
            }
            return result(saved)
        }

        val card = review.deckCard ?: return result(command.requestId, "stale")
        val state = deck.state
        val persisted = withContext(Dispatchers.IO) { store.draft(card.draftId) }
        if (!deckOpen || state.currentId != command.draftId || state.version != review.deckVersion ||
            deck.snapshot.stateRevision != review.deckStateRevision ||
            state.current?.operation?.name != card.operation.name ||
            state.form?.toCoreDraftForm() != review.deckForm || state.dirty ||
            state.busy || state.aiLocked || persisted != card ||
            (card.operation.requiresExisting &&
                withContext(Dispatchers.IO) { store.get(card.eventSeq) } != review.current))
            return result(command.requestId, "conflict", "草稿或正式事件已变化，请重新审查")
        return commitDeckResult(command.requestId, card.draftId, card.draftVersion,
            review.deckStateRevision ?: return result(command.requestId, "stale"))
    }

    private suspend fun resolveConflictReview(
        review: WebFormalWriteReview,
        command: WebEditCommand.RequestConflictReviewDeck,
        approved: Boolean
    ): JSONObject {
        val prompt = review.conflictPrompt ?: return result(command.requestId, "stale")
        val currentPrompt = matchingConflict(command.draftId, command.draftVersion,
            command.eventSeq, command.expectedRevision)
        if (currentPrompt != prompt || !matchesPersistedConflictDraft(review.deckCard, prompt) ||
            review.deckVersion != command.draftVersion ||
            review.deckStateRevision != deck.snapshot.stateRevision ||
            review.deckForm != prompt.approvedForm.toCoreDraftForm())
            return result(command.requestId, "conflict", "冲突卡片或审阅内容已变化")
        val live = withContext(Dispatchers.IO) {
            synchronized(store) { store.get(command.eventSeq) }
        }
        val persisted = withContext(Dispatchers.IO) { store.draft(command.draftId) }
        if (live != review.current || live?.revision != command.expectedRevision ||
            persisted != review.deckCard || !matchesPersistedConflictDraft(persisted, prompt) ||
            deck.state.form?.toCoreDraftForm() != review.deckForm || deck.state.dirty)
            return result(command.requestId, "conflict", "正式事件或草稿已变化，请重新审查")
        if (!approved) {
            return result(command.requestId, "cancelled")
        }
        val confirmed = deck.confirmConflictReplacement(
            command.draftId,
            command.draftVersion,
            review.deckStateRevision ?: return result(command.requestId, "stale"),
            command.expectedRevision,
            command.requestId
        )
        return if (confirmed.status == DraftReviewReceiptStatusV1.APPLIED) {
            if (review.operation == DraftOperation.DELETE)
                result(command.requestId, "saved", command.eventSeq.toString())
            else result(command.requestId, "replaced", command.eventSeq.toString())
        } else result(confirmed)
    }

    private suspend fun resolveRestoreReview(
        review: WebFormalWriteReview,
        command: WebEditCommand.RequestRestoreReviewDeck,
        approved: Boolean
    ): JSONObject {
        val prompt = review.restorePrompt ?: return result(command.requestId, "stale")
        val expectedRevision = review.current?.revision ?: return result(command.requestId, "stale")
        val currentPrompt = matchingRestore(command.draftId, command.draftVersion,
            command.eventSeq, command.expectedRevision)
        if (currentPrompt != prompt || review.deckCard != prompt.card.toCoreDraftCard() ||
            review.deckVersion != command.draftVersion ||
            review.deckStateRevision != deck.snapshot.stateRevision ||
            review.deckForm != deck.state.form?.toCoreDraftForm() ||
            expectedRevision != command.expectedRevision)
            return result(command.requestId, "conflict", "恢复草稿或审阅内容已变化")
        val live = withContext(Dispatchers.IO) {
            synchronized(store) {
                store.getIncludingRetired(command.eventSeq)?.takeIf {
                    store.isRetired(command.eventSeq)
                }
            }
        }
        val persisted = withContext(Dispatchers.IO) { store.draft(command.draftId) }
        if (live != review.current || persisted != review.deckCard ||
            deck.state.form?.toCoreDraftForm() != review.deckForm || deck.state.dirty)
            return result(command.requestId, "conflict", "墓碑或恢复草稿已变化，请重新审查")
        if (!approved) {
            return result(command.requestId, "cancelled")
        }
        val restored = deck.requestRestore(
            prompt.card.draftId,
            prompt.card.draftVersion,
            review.deckStateRevision ?: return result(command.requestId, "stale"),
            command.expectedRevision,
            command.requestId
        )
        return if (restored.status == DraftReviewReceiptStatusV1.APPLIED) {
            restoreTombstone = null
            result(command.requestId, "restored", command.eventSeq.toString())
        } else result(restored)
    }

    /** Called only by the host-owned confirmation UI, never by a Web command. */
    suspend fun resolveFormalWriteReview(token: String, approved: Boolean): JSONObject = gate.withLock {
        val review=pendingWriteReview
            ?: return@withLock result("host-review","stale")
        if(review.token!=token) return@withLock result("host-review","stale")
        val reply = try {
            when (val command = review.command) {
                is WebEditCommand.RequestConflictReviewDeck ->
                    resolveConflictReview(review, command, approved)
                is WebEditCommand.RequestRestoreReviewDeck ->
                    resolveRestoreReview(review, command, approved)
                else -> if(!approved) result(review.command.requestId,"cancelled") else when (command) {
                is WebEditCommand.SaveEvent -> {
                    val active = review.editorSession
                    if(active==null || session!==active || sessionId!=review.command.sessionId ||
                        active.form!=review.proposed || active.recurrenceScope!=review.scope ||
                        withContext(Dispatchers.IO){store.get(review.current!!.eventSeq)}!=review.current)
                        result(review.command.requestId,"conflict","事件或审查内容已变化")
                    else {
                        val saved = withContext(Dispatchers.IO) {
                            eventFeatures.saveFormalEvent(
                                active.handle, active.form.toEventFormV1(), command.requestId
                            )
                        }
                        if (saved.status == EventMutationStatusV1.UPDATED &&
                            review.scope == RecurrenceEditScope.THIS_AND_FUTURE) {
                            withContext(Dispatchers.IO) {
                                eventFeatures.closeFormalEvent(active.handle)
                            }
                            session = null
                            sessionId = null
                        } else {
                            eventFeatures.editorSnapshot(active.handle)?.let { updated ->
                                active.snapshot = updated
                                active.form = updated.form.toCoreDraftForm()
                            }
                        }
                        result(saved)
                    }
                }
                is WebEditCommand.DeleteEvent -> {
                    val active = review.editorSession
                    val current = review.current
                    if (active == null || current == null || session !== active ||
                        sessionId != review.command.sessionId ||
                        review.operation != DraftOperation.DELETE ||
                        active.form != review.editorFormSnapshot ||
                        withContext(Dispatchers.IO) { store.get(current.eventSeq) } != current) {
                        result(review.command.requestId, "conflict", "事件或审查内容已变化")
                    } else {
                        val approval = withContext(Dispatchers.IO) {
                            coreFeatureApi.issueFormalDeleteApproval(active.handle)
                        }
                        if (approval == null) result(review.command.requestId, "conflict")
                        else {
                            val deleted = withContext(Dispatchers.IO) {
                                eventFeatures.deleteFormalEvent(
                                    active.handle, approval, review.command.requestId
                                )
                            }
                            if (deleted.status == EventMutationStatusV1.DELETED) {
                                withContext(Dispatchers.IO) {
                                    eventFeatures.closeFormalEvent(active.handle)
                                }
                                session = null
                                sessionId = null
                            }
                            result(deleted)
                        }
                    }
                }
                is WebEditCommand.CommitDeck -> resolveDeckCommit(review)
                else -> result(review.command.requestId,"stale")
                }
            }
        } catch(e:kotlinx.coroutines.CancellationException) { throw e }
          catch(e:Exception) { result(review.command.requestId,"failed","Host operation failed; retry or recover") }
        pendingWriteReview=null
        completed[review.command.requestId]=review.command to JSONObject(reply.toString())
        latest=reply
        hasActiveUiSession=session!=null || newEventSession!=null || deckOpen
        reply
    }

    /** Host lifecycle checkpoint; never drop dirty state on a failed flush. */
    suspend fun checkpoint(): Boolean = gate.withLock {
        val active=session
        if(active!=null) {
            val checkpoint = withContext(Dispatchers.IO) {
                eventFeatures.checkpointFormalEvent(active.handle, UUID.randomUUID().toString())
            }
            if(checkpoint.status == EventMutationStatusV1.FAILED)return@withLock false
        }
        val newEvent = newEventSession
        if(deckOpen && newEvent != null) {
            val checkpoint = withContext(Dispatchers.IO) {
                eventFeatures.checkpointNewEvent(newEvent.handle, UUID.randomUUID().toString())
            }
            if(checkpoint.status == EventMutationStatusV1.FAILED)return@withLock false
        } else if (deckOpen &&
            !deck.flush("web-checkpoint-${UUID.randomUUID()}").succeeded) return@withLock false
        true
    }

    /**
     * A skin change may replace the WebView, but never the backing controller.
     * Persist before ending the host-owned lease. Failure keeps both alive.
     */
    suspend fun prepareSkinChange(): Boolean = gate.withLock {
        if(session!=null) {
            val opened=session!!
            val checkpoint = withContext(Dispatchers.IO) {
                eventFeatures.checkpointFormalEvent(opened.handle, UUID.randomUUID().toString())
            }
            if(checkpoint.status == EventMutationStatusV1.FAILED)return@withLock false
        }
        val newEvent = newEventSession
        if(deckOpen && newEvent != null) {
            val checkpoint = withContext(Dispatchers.IO) {
                eventFeatures.closeNewEvent(newEvent.handle)
            }
            if(checkpoint.status == EventMutationStatusV1.FAILED)return@withLock false
            newEventSession = null
            runCatching { deck.open(checkpoint.draftId) }
        } else if (deckOpen &&
            !deck.flush("skin-change-${UUID.randomUUID()}").succeeded) return@withLock false
        if(session!=null) {
            withContext(Dispatchers.IO){eventFeatures.closeFormalEvent(session!!.handle)}
            session=null;sessionId=null
        }
        pendingWriteReview?.let { review ->
            val cancelled=result(review.command.requestId,"cancelled")
            completed[review.command.requestId]=review.command to JSONObject(cancelled.toString())
            latest=cancelled
            pendingWriteReview=null
        }
        deckOpen=false
        hasActiveUiSession=false
        true
    }

    fun hasLiveEditor(): Boolean = hasActiveUiSession
}
