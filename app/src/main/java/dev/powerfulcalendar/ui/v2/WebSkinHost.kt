package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.EditorSelection
import dev.powerfulcalendar.core.TimelineCoordinate
import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import android.webkit.WebMessagePort
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId

/** Preserves the explicit date/time context of the active native creation surface. */
internal fun newEventSelectionForPage(
    page: String,
    camera: TimelineCoordinate.Camera,
    zone: ZoneId
): EditorSelection? = when (page) {
    "TIMELINE" -> EditorSelection.AtInstant(
        TimelineCoordinate.snap(camera.anchorMillis, camera.hours, zone)
    )
    "MONTH" -> EditorSelection.OnDate(
        Instant.ofEpochMilli(camera.anchorMillis).atZone(zone).toLocalDate()
    )
    else -> null
}

/**
 * v1 enforces only host.read (state projection) and host.action (all inbound
 * actions and editing commands). host.edit remains accepted as inert v3
 * metadata for package round-trip compatibility; it grants no access alone.
 * Changing this requires a versioned capability policy and compatibility tests.
 */
internal object WebSkinCapabilityPolicy {
    fun mayRead(bundle: WebSkinBundle): Boolean = "host.read" in bundle.capabilities
    fun mayAct(bundle: WebSkinBundle): Boolean = "host.action" in bundle.capabilities
}

/** Versioned, deliberately tiny capability bridge. It never exposes Context or PowerStore. */
internal object WebSkinBridgeRuntime {
    const val VERSION = 1
    var owner: Any? = null
    var onAction: (WebSkinAction) -> Unit = {}
    var onEdit: suspend (WebEditCommand) -> JSONObject = { cmd ->
        JSONObject().put("requestId",cmd.requestId).put("status","unavailable")
    }
    var state: () -> JSONObject = { JSONObject().put("bridgeVersion", VERSION) }
    // Only the collector reads this Compose observable. Publishing from the application's
    // SideEffect does not itself cause the owning Composable to recompose.
    var stateRevision by androidx.compose.runtime.mutableLongStateOf(0L)
}

internal sealed interface WebSkinAction {
    data class Routed(val name: String) : WebSkinAction
    data class OpenEvent(val eventSeq: Long, val expectedRevision: Long) : WebSkinAction
    data class InvokeOption(val id: String) : WebSkinAction
    /** Relative browsing only; cannot change the vertical time scale. */
    data class PanTimeline(val deltaMinutes: Int) : WebSkinAction
    /** Select a calendar day while preserving the existing time span. */
    data class JumpTimelineDay(val date: String) : WebSkinAction
    data class ToggleMemo(
        val eventSeq: Long, val expectedRevision: Long,
        val occurrenceKey: String, val completed: Boolean
    ) : WebSkinAction
    data class SendChat(val text: String) : WebSkinAction
    data object PickAiFile : WebSkinAction
    data object PickAiImage : WebSkinAction
    data object CreateMemo : WebSkinAction
    data class CreateDraft(val kind: String) : WebSkinAction
    data class BrowseMonth(val deltaMonths: Int) : WebSkinAction
    data class ResolveAiProposal(
        val reviewId: String,
        val decision: AiProposalDecision,
        val indices: List<Int>
    ) : WebSkinAction
}

internal data class WebSkinNavigationState(
    val hostPage: String,
    val skinSettingsOpen: Boolean = false
)

/**
 * Keep the customizable settings landing inside the skin. Sensitive tools remain an explicit
 * Host route and are never represented by a JavaScript-owned settings form.
 */
internal fun reduceWebSkinNavigation(
    current: WebSkinNavigationState,
    actionName: String
): WebSkinNavigationState = when (actionName) {
    "timeline" -> WebSkinNavigationState("TIMELINE")
    "month" -> WebSkinNavigationState("MONTH")
    "memo" -> WebSkinNavigationState("MEMO")
    "ai" -> WebSkinNavigationState("AI")
    "skinSettings" -> if (current.hostPage == "TOOLS") current
        else current.copy(skinSettingsOpen = true)
    "settings" -> WebSkinNavigationState("TOOLS")
    else -> current
}

internal enum class AiProposalDecision { PUBLISH, STAGE, REJECT, SUPPLEMENT }

internal fun validateOpenEvent(
    action: WebSkinAction.OpenEvent,
    current: dev.powerfulcalendar.core.TemporalRecord?,
    writerAvailable: Boolean
): dev.powerfulcalendar.core.TemporalRecord? = current?.takeIf {
    writerAvailable && it.eventSeq == action.eventSeq && it.revision == action.expectedRevision
}

@Composable
internal fun WebSkinRoot(
    bundle: WebSkinBundle,
    modifier: Modifier = Modifier,
    native: @Composable BoxScope.() -> Unit,
    onSkinFailure: () -> Unit = {}
) {
    var failed by remember(bundle) { mutableStateOf(false) }
    Box(modifier) {
        if (!failed) SecureWebSkin(
            bundle, Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            onFailure = {
                failed = true
                onSkinFailure()
            }
        )
        if (!bundle.fullCanvas || failed) native()

    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SecureWebSkin(bundle: WebSkinBundle, modifier: Modifier, onFailure: () -> Unit) {
    val origin = "https://skin.powercal.local/"
    val context = androidx.compose.ui.platform.LocalContext.current
    val feed = remember(bundle) { WebSkinStateFeed() }
    val live = remember(bundle) { AtomicBoolean(true) }
    val session = remember(bundle) { WebSkinWebSession() }
    val webView = remember(bundle) {
        WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.databaseEnabled = false
            settings.setGeolocationEnabled(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.blockNetworkLoads = true
            if (Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: WebResourceRequest,
                    error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) onFailure()
                }
                override fun onRenderProcessGone(view: WebView,
                    detail: android.webkit.RenderProcessGoneDetail): Boolean {
                    onFailure()
                    return true
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.toString() != origin + bundle.entrypoint

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url
                    if (uri.scheme != "https" || uri.host != "skin.powercal.local" ||
                        uri.port != -1 || uri.userInfo != null || uri.query != null) {
                        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(),
                            ByteArrayInputStream(ByteArray(0)))
                    }
                    val path = uri.path.orEmpty().removePrefix("/").ifEmpty { bundle.entrypoint }
                    val data = bundle.files[path] ?: return WebResourceResponse(
                        "text/plain", "utf-8", 404, "Missing", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    val mime = when {
                        path.endsWith(".html") -> "text/html"
                        path.endsWith(".css") -> "text/css"
                        path.endsWith(".js") -> "application/javascript"
                        path.endsWith(".json") -> "application/json"
                        path.endsWith(".png") -> "image/png"
                        path.endsWith(".webp") -> "image/webp"
                        path.endsWith(".woff2") -> "font/woff2"
                        path.endsWith(".woff") -> "font/woff"
                        path.endsWith(".ttf") -> "font/ttf"
                        else -> "application/octet-stream"
                    }
                    return WebResourceResponse(mime, if (mime.startsWith("text") || mime.contains("json") || mime.contains("javascript")) "utf-8" else null,
                        ByteArrayInputStream(data)).apply {
                        responseHeaders = mapOf(
                            "Content-Security-Policy" to "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'none'; frame-src 'none'; form-action 'none'; object-src 'none'; base-uri 'none'",
                            "X-Content-Type-Options" to "nosniff"
                        )
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (!live.get()) return
                    val finished = Uri.parse(url)
                    if (url != origin + bundle.entrypoint ||
                        finished.userInfo != null || finished.port != -1) {
                        onFailure()
                        return
                    }
                    val channels = view.createWebMessageChannel()
                    feed.disconnect()
                    session.replace(channels[0])
                    channels[0].setWebMessageCallback(object : android.webkit.WebMessagePort.WebMessageCallback() {
                        override fun onMessage(port: android.webkit.WebMessagePort, message: android.webkit.WebMessage) {
                            view.post {
                                if (!live.get() || session.port !== port ||
                                    PowerCalSkinRegistry.active.web !== bundle ||
                                    !WebSkinCapabilityPolicy.mayAct(bundle)) return@post
                                val raw = message.data.orEmpty()
                                val command = WebEditWire.decode(raw)
                                if(command!=null) {
                                    // The write survives WebView/composition cancellation. A
                                    // late acknowledgement must never reach a replaced port.
                                    val invokingOwner = WebSkinBridgeRuntime.owner
                                    PowerCalStores.appScope.launch(Dispatchers.Main.immediate) {
                                        // Delayed tasks cannot execute after skin/owner replacement.
                                        if(!live.get() || session.port!==port ||
                                            PowerCalSkinRegistry.active.web!==bundle ||
                                            WebSkinBridgeRuntime.owner!==invokingOwner)
                                            return@launch
                                        val reply = runCatching {
                                            WebSkinBridgeRuntime.onEdit(command)
                                        }.getOrElse {
                                            JSONObject().put("requestId",command.requestId)
                                                .put("status","failed")
                                        }
                                        if(live.get() && session.port===port &&
                                            PowerCalSkinRegistry.active.web===bundle) {
                                            runCatching {
                                                port.postMessage(android.webkit.WebMessage(
                                                    JSONObject().put("version",1).put("type","result")
                                                        .put("payload",reply).toString()))
                                            }.onFailure { onFailure() }
                                            runCatching { feed.publish(exposedState(bundle)) }
                                                .onFailure { onFailure() }
                                        }
                                    }
                                } else decodeBridgeAction(raw)
                                    ?.let(WebSkinBridgeRuntime.onAction)
                                runCatching { feed.publish(exposedState(bundle)) }
                                    .onFailure { onFailure() }
                            }
                        }
                    })
                    view.postWebMessage(android.webkit.WebMessage("powercal-port", arrayOf(channels[1])), Uri.parse(origin))
                    feed.connect { frame ->
                        runCatching { channels[0].postMessage(android.webkit.WebMessage(frame)) }
                            .onFailure { onFailure() }
                    }
                    runCatching { feed.publish(exposedState(bundle)) }
                        .onFailure { onFailure() }
                }
            }
            loadUrl(origin + bundle.entrypoint)
        }
    }
    AndroidView({ webView }, modifier)
    LaunchedEffect(webView, feed) {
        snapshotFlow {
            // SideEffect installs a new immutable snapshot closure on every host change.
            // Observing its explicit revision avoids stale closures without polling.
            WebSkinBridgeRuntime.stateRevision
            exposedState(bundle).toString()
        }.distinctUntilChanged().collect { json ->
            if (PowerCalSkinRegistry.active.web === bundle) {
                runCatching { feed.publish(JSONObject(json)) }.onFailure { onFailure() }
            }
        }
    }
    LaunchedEffect(webView) {
        kotlinx.coroutines.delay(5_000)
        if (live.get() && webView.progress < 100) onFailure()
    }
    DisposableEffect(webView) { onDispose {
        live.set(false)
        feed.disconnect()
        session.close()
        webView.stopLoading()
        webView.destroy()
    } }
}

private fun exposedState(bundle: WebSkinBundle): JSONObject =
    if (WebSkinCapabilityPolicy.mayRead(bundle)) WebSkinBridgeRuntime.state()
    else JSONObject().put("bridgeVersion", WebSkinBridgeRuntime.VERSION)

/** Disconnected WebViews/old page channels cannot invoke the current host. */
private class WebSkinWebSession {
    var port: WebMessagePort? = null
        private set
    fun replace(next: WebMessagePort) {
        close()
        port = next
    }
    fun close() {
        runCatching { port?.close() }
        port = null
    }
}

internal fun decodeBridgeAction(raw: String): WebSkinAction? {
    if (raw.length !in 1..8_192) return null
    val message = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (message.keys().asSequence().toSet() != setOf("version", "type", "payload")) return null
    if (message.optInt("version") != WebSkinBridgeRuntime.VERSION ||
        message.optString("type") != "action") return null
    val payload = message.optJSONObject("payload") ?: return null
    val keys = payload.keys().asSequence().toSet()
    val name = payload.optString("name")
    if (name.length !in 1..40) return null
    return when (name) {
        "timeline", "month", "memo", "ai", "settings", "skinSettings", "openDeck", "today" ->
            if (keys == setOf("name")) WebSkinAction.Routed(name) else null
        "invokeOption" -> {
            if (keys != setOf("name", "id")) return null
            val id = payload.opt("id")
            if (id is String && SemanticOptions.supports(id)) WebSkinAction.InvokeOption(id)
            else null
        }
        "panTimeline" -> {
            if (keys != setOf("name", "deltaMinutes")) return null
            val rawMinutes = payload.opt("deltaMinutes")
            val minutes = (rawMinutes as? Int)
            if (minutes != null && minutes != 0 && minutes in -2880..2880)
                WebSkinAction.PanTimeline(minutes)
            else null
        }
        "jumpTimelineDay" -> {
            if (keys != setOf("name", "date")) return null
            val date = payload.opt("date")
            val parsed = if (date is String && date.length == 10)
                runCatching { java.time.LocalDate.parse(date) }.getOrNull() else null
            if (parsed != null && parsed.year in 1900..2200)
                WebSkinAction.JumpTimelineDay(parsed.toString())
            else null
        }
        "createMemo" -> if (keys == setOf("name")) WebSkinAction.CreateMemo else null
        "browseMonth" -> {
            if (keys != setOf("name", "deltaMonths")) return null
            val raw = payload.opt("deltaMonths")
            if (raw is Int && raw in -12..12 && raw != 0)
                WebSkinAction.BrowseMonth(raw)
            else null
        }
        "createDraft" -> {
            if (keys != setOf("name", "kind")) return null
            val kind = payload.opt("kind")
            if (kind is String && kind in setOf("ACTIVITY", "ALARM", "DEADLINE", "MEMO"))
                WebSkinAction.CreateDraft(kind)
            else null
        }
        "sendChat" -> {
            if (keys != setOf("name", "text")) return null
            val text = payload.opt("text")
            if (text is String && text.isNotBlank() && text.length <= 4000)
                WebSkinAction.SendChat(text)
            else null
        }
        "pickAiFile" -> if (keys == setOf("name")) WebSkinAction.PickAiFile else null
        "pickAiImage" -> if (keys == setOf("name")) WebSkinAction.PickAiImage else null
        "resolveAiProposal" -> {
            if (keys != setOf("name", "reviewId", "decision", "indices")) return null
            val reviewId = payload.opt("reviewId") as? String ?: return null
            val canonicalId = runCatching { java.util.UUID.fromString(reviewId).toString() }
                .getOrNull()?.takeIf { it == reviewId } ?: return null
            val decision = when (payload.opt("decision")) {
                "publish" -> AiProposalDecision.PUBLISH
                "stage" -> AiProposalDecision.STAGE
                "reject" -> AiProposalDecision.REJECT
                "supplement" -> AiProposalDecision.SUPPLEMENT
                else -> return null
            }
            val array = payload.optJSONArray("indices") ?: return null
            if (array.length() > 100) return null
            val indices = (0 until array.length()).map { index ->
                when (val value = array.opt(index)) {
                    is Int -> value
                    is Long -> value.takeIf { it in 0L..99L }?.toInt() ?: return null
                    else -> return null
                }.takeIf { it in 0..99 } ?: return null
            }
            if (indices.distinct().size != indices.size) return null
            if (decision == AiProposalDecision.SUPPLEMENT) {
                if (indices.isNotEmpty()) return null
            } else if (indices.isEmpty()) return null
            WebSkinAction.ResolveAiProposal(canonicalId, decision, indices)
        }
        "toggleMemo" -> {
            if (keys != setOf("name", "eventSeq", "expectedRevision",
                    "occurrenceKey", "completed")) return null
            val seqRaw = payload.opt("eventSeq")
            val revRaw = payload.opt("expectedRevision")
            val key = payload.opt("occurrenceKey")
            val done = payload.opt("completed")
            val seq = (seqRaw as? Long) ?: (seqRaw as? Int)?.toLong()
            val rev = (revRaw as? Long) ?: (revRaw as? Int)?.toLong()
            if (seq != null && seq > 0 && rev != null && rev > 0 &&
                key is String && key.length in 1..100 &&
                key.all { it.isLetterOrDigit() || it in ":_-." } &&
                done is Boolean) {
                WebSkinAction.ToggleMemo(seq, rev, key, done)
            } else null
        }
        "openEvent" -> {
            if (keys != setOf("name", "eventSeq", "expectedRevision")) return null
            val seq = payload.optLong("eventSeq", -1L)
            val revision = payload.optLong("expectedRevision", -1L)
            val rawSeq = payload.opt("eventSeq")
            val rawRevision = payload.opt("expectedRevision")
            if (seq <= 0L || revision <= 0L || rawSeq !is Number || rawRevision !is Number ||
                rawSeq is Float || rawSeq is Double || rawRevision is Float || rawRevision is Double) null
            else WebSkinAction.OpenEvent(seq, revision)
        }
        else -> null
    }
}
