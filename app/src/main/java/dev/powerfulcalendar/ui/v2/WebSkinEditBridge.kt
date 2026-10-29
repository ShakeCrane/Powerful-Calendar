package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.EditSession
import dev.powerfulcalendar.core.EditorSelection
import dev.powerfulcalendar.core.NewEventEditSession
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
import dev.powerfulcalendar.core.FormalEventSearch
import dev.powerfulcalendar.core.FormBuild
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.ReminderTiming
import dev.powerfulcalendar.core.TemporalConversion
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
        val draftVersion: Long, val field: String, val value: String): WebEditCommand
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
                if (!exact("draftId","draftVersion","field","value")) return null
                val f=field()?:return null
                WebEditCommand.PatchDeck(id,number("draftId")?:return null,
                    number("draftVersion",true)?:return null,f.first,f.second)
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
}

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
    val editorSession: EditSession? = null,
    val editorFormSnapshot: DraftForm? = null,
    val newEventSession: NewEventEditSession? = null,
    val deckCard: DraftCard? = null,
    val deckForm: DraftForm? = null,
    val deckVersion: Long? = null,
    val conflictPrompt: DeckController.ConflictPrompt? = null,
    val restorePrompt: DeckController.RestorePrompt? = null
)

/** Host-owned timeline camera and zone; skins never choose the drag snap grid. */
internal data class TimelineMoveContext(
    val camera: TimelineCoordinate.Camera,
    val zone: ZoneId,
    val visibleEventRevisions: Map<Long, Long>
)

internal class WebSkinEditService(
    private val store: PowerStore,
    private val deck: DeckController,
    private val externalEditorActive: () -> Boolean = { false }
) {
    private val gate = Mutex()
    private var session: EditSession? = null
    private var sessionId: String? = null
    private var newEventSession: NewEventEditSession? = null
    private var deckOpen = false
    private var deckSearchQuery = ""
    private var formalSearchResults: List<TemporalRecord> = emptyList()
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
                .put("eventSeq", active.persistedCard?.eventSeq ?: JSONObject.NULL)
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
        val matches = if (deckSearchQuery.isBlank()) state.cards
            else DraftQueueSearch.filter(state.cards, deckSearchQuery)
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
                .put("current", prompt.current?.let { WebEditWire.form(DraftForm.from(it)) }
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
            .put("busy",state.busy).put("aiLocked",state.aiLocked)
            .put("error",state.error)
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
        val card = withContext(Dispatchers.IO) {
            store.newDraft(
                form,
                record.eventSeq,
                DraftOperation.REPLACE,
                actor = "user",
                expectedRevision = cmd.expectedRevision
            )
        }
        // The formal event is untouched; the existing queue owns the replacement candidate.
        deck.open(card.draftId)
        deckOpen = true
        return result(cmd.requestId, "staged", card.draftId.toString())
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
                    else {
                        val record=withContext(Dispatchers.IO){ store.get(cmd.eventSeq) }
                        if(record==null || record.revision!=cmd.revision)
                            result(cmd.requestId,"stale")
                        else if(cmd.occurrenceKey != null &&
                            (!record.recurrence.isRepeating ||
                                withContext(Dispatchers.IO) {
                                    RecurrenceEngine.occurrenceForKey(record,cmd.occurrenceKey)
                                } == null)) result(cmd.requestId,"invalid","所选重复实例不存在")
                        else {
                            val opened=withContext(Dispatchers.IO){ EditSession.open(store,record,cmd.occurrenceKey) }
                            session=opened
                            sessionId=UUID.randomUUID().toString()
                            result(cmd.requestId,"opened")
                        }
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
                        withContext(Dispatchers.IO){current.selectRecurrenceScope(cmd.scope)}
                        result(cmd.requestId,"selected")
                    }
                }
                is WebEditCommand.DiscardEventChanges -> {
                    if(!editorMatches(cmd.sessionId)) result(cmd.requestId,"stale")
                    else {
                        withContext(Dispatchers.IO){session!!.discardEditingChanges()}
                        result(cmd.requestId,"discarded_changes")
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
                        withContext(Dispatchers.IO) {
                            session!!.edit(WebEditWire.patch(session!!.form,cmd.field,cmd.value))
                        }
                        result(cmd.requestId,"edited")
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
                    else when(val saved=withContext(Dispatchers.IO){session!!.save()}) {
                        is PowerStore.DirectEditOutcome.Saved ->
                            result(cmd.requestId,"saved",saved.revision.toString())
                        is PowerStore.DirectEditOutcome.Conflict -> result(cmd.requestId,"conflict")
                        is PowerStore.DirectEditOutcome.Invalid ->
                            result(cmd.requestId,"invalid",saved.reason)
                        PowerStore.DirectEditOutcome.Locked -> result(cmd.requestId,"locked")
                    }
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
                        val checkpoint=withContext(Dispatchers.IO){old.checkpoint()}
                        if(old.lastError!=null) result(cmd.requestId,"failed",old.lastError)
                        else {
                            withContext(Dispatchers.IO){old.close()}
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
                        val opened = NewEventEditSession.open(
                            cmd.kind, zone = newEventZone, selection = newEventSelection
                        )
                        clearDeckSearch()
                        newEventSession = opened
                        deckOpen = true
                        result(cmd.requestId,"created",opened.clientDraftId.toString())
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
                        else if(deck.select(cmd.draftId)) {
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
                                    FormalEventSearch.filter(store.all(), cmd.query).take(4)
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
                        deck.jump(cmd.steps) -> {
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
                        !deck.flush() -> result(cmd.requestId,"failed",deck.state.error)
                        else -> {
                            val proposal = withContext(Dispatchers.IO) {
                                // Keep the revision check, compatible-draft lookup and existing
                                // PowerStore proposal operation under the Store monitor. All host
                                // paths share this Store instance and its synchronized API.
                                synchronized(store) {
                                    val current = store.get(cmd.eventSeq)
                                    when {
                                        current == null -> null to "stale"
                                        current.revision != cmd.expectedRevision -> null to "stale"
                                        else -> {
                                            val existing = store.pendingDraftsFor(cmd.eventSeq)
                                                .firstOrNull {
                                                    it.operation == DraftOperation.REPLACE ||
                                                        it.operation == DraftOperation.CONVERT
                                                }
                                            val card = existing ?: store.proposeReplace(cmd.eventSeq)
                                            if (card.operation == DraftOperation.REPLACE ||
                                                card.operation == DraftOperation.CONVERT) card to null
                                            else null to "conflict"
                                        }
                                    }
                                }
                            }
                            val card = proposal.first
                            if (card == null) result(cmd.requestId,proposal.second ?: "failed")
                            else {
                                deck.refresh(card.draftId)
                                clearDeckSearch()
                                result(cmd.requestId,"opened",card.draftId.toString())
                            }
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
                        !deck.flush() -> result(cmd.requestId,"failed",deck.state.error)
                        else -> {
                            val opened = deck.beginRestore(cmd.eventSeq)
                            val card = deck.state.current
                            if (!opened || card == null) result(cmd.requestId,"failed",deck.state.error)
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
                            activeNew.edit(WebEditWire.patch(activeNew.form,cmd.field,cmd.value))
                            result(cmd.requestId,"edited")
                        }
                    } else {
                        val state=deck.state
                        if(!deckOpen || state.currentId!=cmd.draftId ||
                            state.version!=cmd.draftVersion)result(cmd.requestId,"stale")
                        else if(state.aiLocked || state.busy)result(cmd.requestId,"locked")
                        else {
                            val form=state.form
                            if(form==null) result(cmd.requestId,"missing")
                            else {deck.edit(WebEditWire.patch(form,cmd.field,cmd.value))
                                result(cmd.requestId,"edited")}
                        }
                    }
                }
                is WebEditCommand.SelectDeck -> {
                    val activeNew = newEventSession
                    if(!deckOpen) result(cmd.requestId,"missing")
                    else if(activeNew != null && activeNew.clientDraftId == cmd.draftId)
                        result(cmd.requestId,"selected")
                    else if(activeNew != null) result(cmd.requestId,"missing")
                    else if(deck.select(cmd.draftId)) {
                        clearDeckSearch()
                        result(cmd.requestId,"selected")
                    }
                    else result(cmd.requestId,"failed",deck.state.error)
                }
                is WebEditCommand.FlushDeck -> {
                    val activeNew = newEventSession
                    if(!deckOpen)result(cmd.requestId,"missing")
                    else if(activeNew != null) {
                        withContext(Dispatchers.IO) { activeNew.checkpoint(store) }
                        result(cmd.requestId,"flushed")
                    } else if(deck.flush())result(cmd.requestId,"flushed")
                    else result(cmd.requestId,"failed",deck.state.error)
                }
                is WebEditCommand.CloseDeck -> {
                    val activeNew = newEventSession
                    if(!deckOpen)result(cmd.requestId,"closed")
                    else if(activeNew != null) {
                        val saved = withContext(Dispatchers.IO) { activeNew.close(store) }
                        newEventSession = null
                        deckOpen = false
                        clearDeckSearch()
                        runCatching { deck.open(saved?.draftId) }
                        result(cmd.requestId,"closed",saved?.draftId?.toString())
                    } else if(deck.flush()) {
                        deckOpen=false
                        clearDeckSearch()
                        result(cmd.requestId,"closed")
                    }
                    else result(cmd.requestId,"failed",deck.state.error)
                }
                is WebEditCommand.CommitDeck -> requestCommitReview(cmd)
                is WebEditCommand.DiscardDeck -> {
                    val activeNew = newEventSession
                    if(activeNew != null) {
                        if(!deckOpen || activeNew.clientDraftId!=cmd.draftId ||
                            activeNew.version!=cmd.draftVersion) result(cmd.requestId,"stale")
                        else if(withContext(Dispatchers.IO) { activeNew.discard(store) }) {
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
                        else if(deck.discard())result(cmd.requestId,"discarded")
                        else result(cmd.requestId,"failed",deck.state.error)
                    }
                }
                is WebEditCommand.UndoDeck ->
                    if(!deckOpen)result(cmd.requestId,"missing")
                    else if(newEventSession != null) result(cmd.requestId,"missing")
                    else if(deck.undoDiscard())result(cmd.requestId,"restored")
                    else result(cmd.requestId,"failed",deck.state.error)
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
    ): DeckController.ConflictPrompt? = deck.state.conflict?.takeIf { prompt ->
        prompt.draftId == draftId && prompt.card.draftVersion == draftVersion &&
            prompt.eventSeq == eventSeq && prompt.currentRevision == expectedRevision &&
            deck.state.currentId == draftId && deck.state.version == draftVersion
    }

    private fun matchingRestore(
        draftId: Long, draftVersion: Long, eventSeq: Long, expectedRevision: Long
    ): DeckController.RestorePrompt? = deck.state.restore?.takeIf { prompt ->
        prompt.card.draftId == draftId && deck.state.currentId == draftId &&
            deck.state.version == draftVersion && prompt.eventSeq == eventSeq &&
            restoreTombstone?.eventSeq == eventSeq &&
            restoreTombstone?.revision == expectedRevision
    }

    private fun matchesPersistedConflictDraft(
        persisted: DraftCard?, prompt: DeckController.ConflictPrompt
    ): Boolean = persisted != null && persisted.status == DraftStatus.CONFLICT &&
        persisted.draftId == prompt.card.draftId && persisted.eventSeq == prompt.card.eventSeq &&
        persisted.queueOrder == prompt.card.queueOrder &&
        persisted.operation == prompt.card.operation && persisted.form == prompt.approvedForm &&
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
            operation = prompt.card.operation, current = live, before = live,
            proposed = prompt.approvedForm, deckCard = persisted,
            deckForm = prompt.approvedForm, deckVersion = state.version,
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
        if (!unchanged || withContext(Dispatchers.IO) { store.draft(prompt.card.draftId) } != prompt.card)
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
        val form = state.form ?: return result(cmd.requestId, "missing")
        val persisted = withContext(Dispatchers.IO) { store.draft(prompt.card.draftId) }
        if (persisted != prompt.card || form != prompt.card.form || tombstone != restoreTombstone)
            return result(cmd.requestId, "stale", "恢复草稿或墓碑已变化，请重新检查")
        pendingWriteReview = WebFormalWriteReview(
            token = UUID.randomUUID().toString(), command = cmd,
            operation = prompt.card.operation, current = tombstone, before = tombstone,
            proposed = form, deckCard = prompt.card, deckForm = form,
            deckVersion = state.version, restorePrompt = prompt
        )
        return result(cmd.requestId, "confirmation_required")
    }

    private suspend fun requestCommitReview(cmd: WebEditCommand.CommitDeck): JSONObject {
        val activeNew = newEventSession
        if (activeNew != null) {
            if (!deckOpen || activeNew.clientDraftId != cmd.draftId ||
                activeNew.version != cmd.draftVersion) return result(cmd.requestId, "stale")
            if (!activeNew.meaningfulDiff) return result(cmd.requestId, "no_changes")
            val form = activeNew.form
            return when (form.build(1L, 1L, activeNew.defaults.nowMillis)) {
                is FormBuild.Ready -> {
                    pendingWriteReview = WebFormalWriteReview(
                        token = UUID.randomUUID().toString(), command = cmd,
                        operation = DraftOperation.CREATE, current = null, before = null,
                        proposed = form, newEventSession = activeNew,
                        deckCard = activeNew.persistedCard, deckVersion = activeNew.version
                    )
                    result(cmd.requestId, "confirmation_required")
                }
                is FormBuild.Incomplete, is FormBuild.Invalid ->
                    when (val saved = withContext(Dispatchers.IO) { activeNew.save(store) }) {
                        is NewEventEditSession.SaveOutcome.Staged ->
                            result(cmd.requestId, "staged", activeNew.clientDraftId.toString())
                        is NewEventEditSession.SaveOutcome.Failed ->
                            result(cmd.requestId, "failed", saved.message)
                        NewEventEditSession.SaveOutcome.NoChanges -> result(cmd.requestId, "no_changes")
                        is NewEventEditSession.SaveOutcome.Published ->
                            result(cmd.requestId, "failed", "发布状态变化，请重新检查")
                    }
            }
        }

        val initial = deck.state
        if (!deckOpen || initial.currentId != cmd.draftId || initial.version != cmd.draftVersion)
            return result(cmd.requestId, "stale")
        if (initial.aiLocked || initial.busy) return result(cmd.requestId, "locked")
        if (!deck.flush()) return result(cmd.requestId, "failed", deck.state.error)

        val state = deck.state
        val card = state.current ?: return result(cmd.requestId, "missing")
        val form = state.form ?: return result(cmd.requestId, "missing")
        if (state.currentId != cmd.draftId || state.busy || state.aiLocked)
            return result(cmd.requestId, "locked")
        val current = if (card.operation.requiresExisting) {
            withContext(Dispatchers.IO) { store.get(card.eventSeq) }
        } else null
        val ready = form.build(card.eventSeq.coerceAtLeast(1L), (card.baseRevision ?: 0L) + 1L)
        val changedFormalValue = when {
            card.operation == DraftOperation.DELETE -> true
            ready !is FormBuild.Ready -> false
            card.operation == DraftOperation.CREATE -> true
            current == null -> false
            else -> form.hasMeaningfulDiffFrom(DraftForm.from(current))
        }
        if (!changedFormalValue) {
            return when {
                card.operation.requiresExisting && current == null -> commitDeckResult(cmd.requestId)
                ready !is FormBuild.Ready -> commitDeckResult(cmd.requestId)
                else -> result(cmd.requestId, "no_changes")
            }
        }
        if (card.operation.requiresExisting && current == null)
            return commitDeckResult(cmd.requestId)

        val persisted = withContext(Dispatchers.IO) { store.draft(card.draftId) }
            ?: return result(cmd.requestId, "stale")
        if (persisted.operation != card.operation || persisted.form != form ||
            persisted.draftVersion != state.version || state.dirty)
            return result(cmd.requestId, "conflict", "草稿在审查期间发生变化")
        pendingWriteReview = WebFormalWriteReview(
            token = UUID.randomUUID().toString(), command = cmd,
            operation = card.operation, current = current, before = current,
            proposed = if (card.operation == DraftOperation.DELETE) null else form,
            deckCard = persisted, deckForm = form, deckVersion = state.version
        )
        return result(cmd.requestId, "confirmation_required")
    }

    private suspend fun commitDeckResult(requestId: String): JSONObject {
        if (deck.commit()) {
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
        return result(requestId,
            if (deck.state.conflict != null) "conflict"
            else if (restore != null) "restore_required"
            else "failed", deck.state.error)
    }

    private suspend fun resolveDeckCommit(review: WebFormalWriteReview): JSONObject {
        val command = review.command as? WebEditCommand.CommitDeck
            ?: return result(review.command.requestId, "stale")
        val transient = review.newEventSession
        if (transient != null) {
            if (newEventSession !== transient || !deckOpen ||
                transient.clientDraftId != command.draftId ||
                transient.version != review.deckVersion || transient.form != review.proposed ||
                transient.persistedCard != review.deckCard)
                return result(command.requestId, "conflict", "新建内容已变化，请重新审查")
            return when (val saved = withContext(Dispatchers.IO) { transient.save(store) }) {
                is NewEventEditSession.SaveOutcome.Published -> when (val outcome = saved.outcome) {
                    is PowerStore.CommitOutcome.Committed -> {
                        newEventSession = null
                        deckOpen = false
                        runCatching { deck.open() }
                        result(command.requestId, "committed", outcome.eventSeq.toString())
                    }
                    is PowerStore.CommitOutcome.Invalid -> result(command.requestId, "invalid", outcome.reason)
                    is PowerStore.CommitOutcome.Conflict -> result(command.requestId, "conflict")
                    is PowerStore.CommitOutcome.NeedsRestore -> result(command.requestId, "restore_required")
                    PowerStore.CommitOutcome.Locked, PowerStore.CommitOutcome.NotAuthorized ->
                        result(command.requestId, "locked")
                }
                is NewEventEditSession.SaveOutcome.Staged -> result(command.requestId, "staged")
                is NewEventEditSession.SaveOutcome.Failed -> result(command.requestId, "failed", saved.message)
                NewEventEditSession.SaveOutcome.NoChanges -> result(command.requestId, "no_changes")
            }
        }

        val card = review.deckCard ?: return result(command.requestId, "stale")
        val state = deck.state
        val persisted = withContext(Dispatchers.IO) { store.draft(card.draftId) }
        if (!deckOpen || state.currentId != command.draftId || state.version != review.deckVersion ||
            state.current?.operation != card.operation || state.form != review.deckForm || state.dirty ||
            state.busy || state.aiLocked || persisted != card ||
            (card.operation.requiresExisting &&
                withContext(Dispatchers.IO) { store.get(card.eventSeq) } != review.current))
            return result(command.requestId, "conflict", "草稿或正式事件已变化，请重新审查")
        return commitDeckResult(command.requestId)
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
            review.deckVersion != command.draftVersion || review.deckForm != prompt.approvedForm)
            return result(command.requestId, "conflict", "冲突卡片或审阅内容已变化")
        val live = withContext(Dispatchers.IO) {
            synchronized(store) { store.get(command.eventSeq) }
        }
        val persisted = withContext(Dispatchers.IO) { store.draft(command.draftId) }
        if (live != review.current || live?.revision != command.expectedRevision ||
            persisted != review.deckCard || !matchesPersistedConflictDraft(persisted, prompt) ||
            deck.state.form != review.deckForm || deck.state.dirty)
            return result(command.requestId, "conflict", "正式事件或草稿已变化，请重新审查")
        if (!approved) {
            return result(command.requestId, "cancelled")
        }
        return if (deck.confirmConflictReplacement(command.expectedRevision)) {
            if (review.operation == DraftOperation.DELETE)
                result(command.requestId, "saved", command.eventSeq.toString())
            else result(command.requestId, "replaced", command.eventSeq.toString())
        } else result(command.requestId, "conflict", deck.state.error ?: "请重新审查冲突")
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
        if (currentPrompt != prompt || review.deckCard != prompt.card ||
            review.deckVersion != command.draftVersion || review.deckForm != deck.state.form ||
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
            deck.state.form != review.deckForm || deck.state.dirty)
            return result(command.requestId, "conflict", "墓碑或恢复草稿已变化，请重新审查")
        if (!approved) {
            return result(command.requestId, "cancelled")
        }
        return if (deck.requestRestore()) {
            restoreTombstone = null
            result(command.requestId, "restored", command.eventSeq.toString())
        } else result(command.requestId, "conflict", deck.state.error ?: "恢复未完成，草稿仍保留")
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
                    else if(review.scope==RecurrenceEditScope.THIS_AND_FUTURE) {
                        when(val split=withContext(Dispatchers.IO){active.saveThisAndFuture()}) {
                            is PowerStore.RecurrenceSplitOutcome.Applied -> {
                                withContext(Dispatchers.IO){active.close()}
                                session=null;sessionId=null
                                result(review.command.requestId,"saved",split.newEventSeq.toString())
                            }
                            is PowerStore.RecurrenceSplitOutcome.Conflict -> result(review.command.requestId,"conflict")
                            is PowerStore.RecurrenceSplitOutcome.Invalid -> result(review.command.requestId,"invalid",split.reason)
                            PowerStore.RecurrenceSplitOutcome.Locked -> result(review.command.requestId,"locked")
                        }
                    } else when(val saved=withContext(Dispatchers.IO){active.save()}) {
                        is PowerStore.DirectEditOutcome.Saved -> result(review.command.requestId,"saved",saved.revision.toString())
                        is PowerStore.DirectEditOutcome.Conflict -> result(review.command.requestId,"conflict")
                        is PowerStore.DirectEditOutcome.Invalid -> result(review.command.requestId,"invalid",saved.reason)
                        PowerStore.DirectEditOutcome.Locked -> result(review.command.requestId,"locked")
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
                    } else when (val deleted = withContext(Dispatchers.IO) { active.delete() }) {
                        is PowerStore.DirectEditOutcome.Saved -> {
                            withContext(Dispatchers.IO) { active.close() }
                            session = null
                            sessionId = null
                            result(review.command.requestId, "saved", current.eventSeq.toString())
                        }
                        is PowerStore.DirectEditOutcome.Conflict ->
                            result(review.command.requestId, "conflict")
                        is PowerStore.DirectEditOutcome.Invalid ->
                            result(review.command.requestId, "invalid", deleted.reason)
                        PowerStore.DirectEditOutcome.Locked -> result(review.command.requestId, "locked")
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
            withContext(Dispatchers.IO){active.checkpoint()}
            if(active.lastError!=null)return@withLock false
        }
        val newEvent = newEventSession
        if(deckOpen && newEvent != null) {
            runCatching { withContext(Dispatchers.IO) { newEvent.checkpoint(store) } }
                .onFailure { return@withLock false }
        } else if(deckOpen && !deck.flush()) return@withLock false
        true
    }

    /**
     * A skin change may replace the WebView, but never the backing controller.
     * Persist before ending the host-owned lease. Failure keeps both alive.
     */
    suspend fun prepareSkinChange(): Boolean = gate.withLock {
        if(session!=null) {
            val opened=session!!
            withContext(Dispatchers.IO){opened.checkpoint()}
            if(opened.lastError!=null)return@withLock false
        }
        val newEvent = newEventSession
        if(deckOpen && newEvent != null) {
            runCatching { withContext(Dispatchers.IO) { newEvent.close(store) } }
                .onFailure { return@withLock false }
            newEventSession = null
            runCatching { deck.open(newEvent.persistedCard?.draftId) }
        } else if(deckOpen && !deck.flush()) return@withLock false
        if(session!=null) {
            withContext(Dispatchers.IO){session!!.close()}
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
