package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.EditSession
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.TemporalKind
import java.time.ZoneId
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Host-owned, capability-neutral editing contract.
 * The bundled Clear skin and imported skins use the exact same endpoint.
 *
 * Deliberately exposes a small *patch* whitelist, never the raw DraftForm:
 * provenance, recurrence, completion keys, reminder authorization, timezone,
 * or lease tokens cannot be supplied or rewritten by an untrusted skin.
 */
internal sealed interface WebEditCommand {
    val requestId: String
    data class OpenEvent(override val requestId: String, val eventSeq: Long,
        val revision: Long): WebEditCommand
    data class PatchEvent(override val requestId: String, val sessionId: String,
        val field: String, val value: String): WebEditCommand
    data class SaveEvent(override val requestId: String, val sessionId: String): WebEditCommand
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
    private val fields = mapOf("title" to 240, "startText" to 40,
        "endText" to 40, "notes" to 2000, "location" to 240)
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
                if (!exact("eventSeq","expectedRevision")) return null
                WebEditCommand.OpenEvent(id,number("eventSeq")?:return null,
                    number("expectedRevision")?:return null)
            }
            "editor.patch" -> {
                if (!exact("sessionId","field","value")) return null
                val f=field()?:return null
                WebEditCommand.PatchEvent(id,sessionId()?:return null,f.first,f.second)
            }
            "editor.save" -> if (exact("sessionId"))
                WebEditCommand.SaveEvent(id,sessionId()?:return null) else null
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

    fun patch(form: DraftForm, field: String, value: String): DraftForm = when(field) {
        "title" -> form.copy(title=value)
        "startText" -> form.copy(startText=value)
        "endText" -> form.copy(endText=value)
        "notes" -> form.copy(notes=value)
        "location" -> form.copy(location=value)
        else -> error("Field is not writable")
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
}

internal class WebSkinEditService(
    private val store: PowerStore,
    private val deck: DeckController,
    private val externalEditorActive: () -> Boolean = { false }
) {
    private val gate = Mutex()
    private var session: EditSession? = null
    private var sessionId: String? = null
    private var deckOpen = false
    // In-process replay protection; capacity bounded. One instance per host, NOT per WebView.
    private val completed = LinkedHashMap<String, Pair<WebEditCommand, JSONObject>>()
    @Volatile private var latest: JSONObject? = null

    private fun result(id: String, status: String, detail: String? = null): JSONObject =
        JSONObject().put("requestId",id).put("status",status)
            .apply { if(detail!=null)put("detail",detail.take(240)) }

    private fun editorMatches(id: String) = session != null && sessionId == id
    private fun deckState(): JSONObject {
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
        val reply = try {
            when(cmd) {
                is WebEditCommand.OpenEvent -> {
                    if(session!=null || externalEditorActive() || deckOpen)
                        result(cmd.requestId,"locked")
                    else {
                        val record=withContext(Dispatchers.IO){ store.get(cmd.eventSeq) }
                        if(record==null || record.revision!=cmd.revision)
                            result(cmd.requestId,"stale")
                        else {
                            val opened=withContext(Dispatchers.IO){ EditSession.open(store,record) }
                            session=opened
                            sessionId=UUID.randomUUID().toString()
                            result(cmd.requestId,"opened")
                        }
                    }
                }
                is WebEditCommand.PatchEvent -> {
                    if(!editorMatches(cmd.sessionId)) result(cmd.requestId,"stale")
                    else {
                        withContext(Dispatchers.IO) {
                            session!!.edit(WebEditWire.patch(session!!.form,cmd.field,cmd.value))
                        }
                        result(cmd.requestId,"edited")
                    }
                }
                is WebEditCommand.SaveEvent -> {
                    if(!editorMatches(cmd.sessionId)) result(cmd.requestId,"stale")
                    else when(val saved=withContext(Dispatchers.IO){session!!.save()}) {
                        is PowerStore.DirectEditOutcome.Saved ->
                            result(cmd.requestId,"saved",saved.revision.toString())
                        is PowerStore.DirectEditOutcome.Conflict -> result(cmd.requestId,"conflict")
                        is PowerStore.DirectEditOutcome.Invalid ->
                            result(cmd.requestId,"invalid",saved.reason)
                        PowerStore.DirectEditOutcome.Locked -> result(cmd.requestId,"locked")
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
                    else {
                        val card=withContext(Dispatchers.IO) {
                            store.newDraft(DraftForm.fresh(cmd.kind,zone=ZoneId.systemDefault()))
                        }
                        deck.open(card.draftId);deckOpen=true
                        result(cmd.requestId,"created",card.draftId.toString())
                    }
                }
                is WebEditCommand.OpenDeck -> {
                    if(session!=null || externalEditorActive()) result(cmd.requestId,"locked")
                    else if(deckOpen) {
                        // A repeated open must never reset in-memory dirty form data.
                        if(cmd.draftId==null) result(cmd.requestId,"opened")
                        else if(deck.select(cmd.draftId)) result(cmd.requestId,"selected")
                        else result(cmd.requestId,"failed",deck.state.error)
                    } else {
                        deck.open(cmd.draftId);deckOpen=true
                        result(cmd.requestId,"opened")
                    }
                }
                is WebEditCommand.PatchDeck -> {
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
                is WebEditCommand.SelectDeck -> {
                    if(!deckOpen) result(cmd.requestId,"missing")
                    else if(deck.select(cmd.draftId))result(cmd.requestId,"selected")
                    else result(cmd.requestId,"failed",deck.state.error)
                }
                is WebEditCommand.FlushDeck -> {
                    if(!deckOpen)result(cmd.requestId,"missing")
                    else if(deck.flush())result(cmd.requestId,"flushed")
                    else result(cmd.requestId,"failed",deck.state.error)
                }
                is WebEditCommand.CloseDeck -> {
                    if(!deckOpen)result(cmd.requestId,"closed")
                    else if(deck.flush()) {deckOpen=false;result(cmd.requestId,"closed")}
                    else result(cmd.requestId,"failed",deck.state.error)
                }
                is WebEditCommand.CommitDeck -> {
                    val s=deck.state
                    if(!deckOpen || s.currentId!=cmd.draftId ||
                        s.version!=cmd.draftVersion) result(cmd.requestId,"stale")
                    else if(deck.commit())result(cmd.requestId,"committed")
                    else result(cmd.requestId,
                        if(deck.state.conflict!=null)"conflict"
                        else if(deck.state.restore!=null)"restore_required"
                        else "failed",deck.state.error)
                }
                is WebEditCommand.DiscardDeck -> {
                    val s=deck.state
                    if(!deckOpen || s.currentId!=cmd.draftId ||
                        s.version!=cmd.draftVersion) result(cmd.requestId,"stale")
                    else if(deck.discard())result(cmd.requestId,"discarded")
                    else result(cmd.requestId,"failed",deck.state.error)
                }
                is WebEditCommand.UndoDeck ->
                    if(!deckOpen)result(cmd.requestId,"missing")
                    else if(deck.undoDiscard())result(cmd.requestId,"restored")
                    else result(cmd.requestId,"failed",deck.state.error)
            }
        } catch(e:kotlinx.coroutines.CancellationException) {throw e}
          catch(e:Exception){ result(cmd.requestId,"failed","Host operation failed; retry or recover") }
        latest=reply
        completed[cmd.requestId]=cmd to JSONObject(reply.toString())
        if(completed.size>64)completed.remove(completed.keys.first())
        reply
    }

    /** Host lifecycle checkpoint; never drop dirty state on a failed flush. */
    suspend fun checkpoint(): Boolean = gate.withLock {
        val active=session
        if(active!=null) {
            withContext(Dispatchers.IO){active.checkpoint()}
            if(active.lastError!=null)return@withLock false
        }
        if(deckOpen && !deck.flush())return@withLock false
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
        if(deckOpen && !deck.flush())return@withLock false
        if(session!=null) {
            withContext(Dispatchers.IO){session!!.close()}
            session=null;sessionId=null
        }
        deckOpen=false
        true
    }

    fun hasLiveEditor(): Boolean = session!=null || deckOpen
}
