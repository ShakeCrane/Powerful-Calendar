package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.EditSession
import dev.powerfulcalendar.core.NewEventEditSession
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.FormBuild
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.ReminderTiming
import dev.powerfulcalendar.core.TemporalConversion
import java.time.LocalDate
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
    data class SelectScope(override val requestId: String, val sessionId: String,
        val scope: RecurrenceEditScope): WebEditCommand
    data class DiscardEventChanges(override val requestId: String, val sessionId: String): WebEditCommand
    data class PatchEvent(override val requestId: String, val sessionId: String,
        val field: String, val value: String): WebEditCommand
    data class SaveEvent(override val requestId: String, val sessionId: String): WebEditCommand
    data class DeleteEvent(override val requestId: String, val sessionId: String): WebEditCommand
    data class CloseEvent(override val requestId: String, val sessionId: String): WebEditCommand
    data class OpenDeck(override val requestId: String, val draftId: Long?): WebEditCommand
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
    val deckVersion: Long? = null
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
                .put("lastDiscardId", JSONObject.NULL)
                .put("cards", items).put("truncated", false)
                .put("currentId", active.clientDraftId)
                .put("version", active.version).put("dirty", active.meaningfulDiff)
                .put("busy", false).put("aiLocked", false)
                .put("error", active.lastError)
                .put("form", WebEditWire.form(active.form))
                .put("hasConflict", false).put("hasRestore", false)
        }
        val state = deck.state
        val items = org.json.JSONArray()
        state.cards.take(80).forEach { card ->
            items.put(JSONObject().put("draftId",card.draftId)
                .put("eventSeq",card.eventSeq).put("draftVersion",card.draftVersion)
                .put("title",card.form.title.take(240))
                .put("kind",card.form.kind.name).put("status",card.status.name))
        }
        return JSONObject().put("open",deckOpen)
            .put("lastDiscardId",state.lastDiscardId)
            .put("cards",items).put("truncated",state.cards.size>80)
            .put("currentId",state.currentId)
            .put("version",state.version).put("dirty",state.dirty)
            .put("busy",state.busy).put("aiLocked",state.aiLocked)
            .put("error",state.error)
            .put("form",state.form?.let(WebEditWire::form))
            .put("hasConflict",state.conflict!=null)
            .put("hasRestore",state.restore!=null)
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

    suspend fun execute(cmd: WebEditCommand): JSONObject = gate.withLock {
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
                            cmd.kind, zone = ZoneId.systemDefault()
                        )
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
                        else if(cmd.draftId==null) result(cmd.requestId,"opened")
                        else if(deck.select(cmd.draftId)) result(cmd.requestId,"selected")
                        else result(cmd.requestId,"failed",deck.state.error)
                    } else {
                        deck.open(cmd.draftId);deckOpen=true
                        result(cmd.requestId,"opened")
                    }
                }
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
                    else if(deck.select(cmd.draftId))result(cmd.requestId,"selected")
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
                        runCatching { deck.open(saved?.draftId) }
                        result(cmd.requestId,"closed",saved?.draftId?.toString())
                    } else if(deck.flush()) {deckOpen=false;result(cmd.requestId,"closed")}
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
          catch(e:java.time.DateTimeException) {result(cmd.requestId,"invalid","Invalid date")}
          catch(e:IllegalArgumentException) {result(cmd.requestId,"invalid","Invalid field value")}
          catch(e:Exception){ result(cmd.requestId,"failed","Host operation failed; retry or recover") }
        hasActiveUiSession=session!=null || newEventSession!=null || deckOpen
        latest=reply
        completed[cmd.requestId]=cmd to JSONObject(reply.toString())
        if(completed.size>64)completed.remove(completed.keys.first())
        reply
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

    private suspend fun commitDeckResult(requestId: String): JSONObject =
        if (deck.commit()) result(requestId, "committed")
        else result(requestId,
            if (deck.state.conflict != null) "conflict"
            else if (deck.state.restore != null) "restore_required"
            else "failed", deck.state.error)

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

    /** Called only by the host-owned confirmation UI, never by a Web command. */
    suspend fun resolveFormalWriteReview(token: String, approved: Boolean): JSONObject = gate.withLock {
        val review=pendingWriteReview
            ?: return@withLock result("host-review","stale")
        if(review.token!=token) return@withLock result("host-review","stale")
        val reply = try {
            if(!approved) result(review.command.requestId,"cancelled")
            else when (review.command) {
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
