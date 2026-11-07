package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.EditorSelection
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.core.api.v1.EventEditorOpenResultV1
import dev.powerfulcalendar.core.api.v1.EventFeatureApiV1
import dev.powerfulcalendar.core.api.v1.AiBatchModeV1
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.widget.FrameLayout
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import android.webkit.WebMessagePort
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.util.Locale

/** Preserves the explicit date/time context of the active native creation surface. */
internal fun newEventSelectionForPage(
    page: String,
    camera: TimelineCoordinate.Camera,
    zone: ZoneId,
    selectedMonthDate: LocalDate? = null
): EditorSelection? = when (page) {
    "TIMELINE" -> EditorSelection.AtInstant(
        TimelineCoordinate.snap(camera.anchorMillis, camera.hours, zone)
    )
    "MONTH" -> EditorSelection.OnDate(selectedMonthDate
        ?: Instant.ofEpochMilli(camera.anchorMillis).atZone(zone).toLocalDate())
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

internal const val WEB_SKIN_CSP = "default-src 'self'; img-src 'self' data:; " +
    "style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'none'; " +
    "frame-src 'none'; form-action 'none'; object-src 'none'; base-uri 'none'"

internal fun webSkinResponseHeaders(contentLength: Int): Map<String, String> = mapOf(
    "Content-Security-Policy" to WEB_SKIN_CSP,
    "X-Content-Type-Options" to "nosniff",
    "Cache-Control" to "no-store",
    "Content-Length" to contentLength.toString()
)

/** Adds the platform touch slop as a presentation metric without changing package contents. */
internal fun injectWebSkinTouchSlop(html: String, touchSlopDp: Float): String {
    require(touchSlopDp.isFinite() && touchSlopDp >= 0f)
    val slopTag = "<meta name=\"powercal-touch-slop-dp\" content=\"$touchSlopDp\">"
    val metricTag = Regex("(?is)<meta\\s+name\\s*=\\s*([\\\"'])powercal-touch-slop-dp\\1[^>]*>")
    val withoutExistingMetric = html.replace(metricTag, "")
    val head = Regex("(?i)<head\\b[^>]*>").find(withoutExistingMetric)
    if (head != null) {
        val insertAt = head.range.last + 1
        return withoutExistingMetric.replaceRange(insertAt, insertAt, slopTag)
    }
    val body = Regex("(?i)<body\\b[^>]*>").find(withoutExistingMetric)
    if (body != null) return withoutExistingMetric.replaceRange(body.range.first, body.range.first, slopTag)
    return "$slopTag$withoutExistingMetric"
}

/** Presentation-only signal; it carries no Host action, data, or write authority. */
internal data class WebSkinModalPresentation(val open: Boolean, val scrimAlpha: Int)

internal fun decodeWebSkinModalVisibility(raw: String): WebSkinModalPresentation? {
    if (raw.length !in 1..512) return null
    val message = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (message.keys().asSequence().toSet() != setOf("version", "type", "payload") ||
        message.optInt("version") != WebSkinBridgeRuntime.VERSION ||
        message.optString("type") != "presentation") return null
    val payload = message.optJSONObject("payload") ?: return null
    val keys = payload.keys().asSequence().toSet()
    val alpha = if (keys == setOf("modalOpen")) {
        156 // Backward-compatible default for v1 presentations without a measured scrim.
    } else {
        if (keys != setOf("modalOpen", "scrimAlpha")) return null
        (payload.opt("scrimAlpha") as? Int)?.takeIf { it in 0..255 } ?: return null
    }
    val open = payload.opt("modalOpen") as? Boolean ?: return null
    return WebSkinModalPresentation(open, alpha)
}

/** Resource MIME selection follows the v3 package path extension, case-insensitively. */
internal fun webSkinMimeType(path: String): String = when (
    path.substringAfterLast('.', "").lowercase(Locale.ROOT)
) {
    "html" -> "text/html"
    "css" -> "text/css"
    "js" -> "application/javascript"
    "json" -> "application/json"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    "woff2" -> "font/woff2"
    "woff" -> "font/woff"
    "ttf" -> "font/ttf"
    else -> "application/octet-stream"
}

/** Versioned, deliberately tiny capability bridge. It never exposes Context or PowerStore. */
internal object WebSkinBridgeRuntime {
    const val VERSION = 1
    var owner: Any? = null
    var onAction: (WebSkinAction) -> Unit = {}
    /** Optional skin-only dismissal hook; it grants no Host action or write authority. */
    var skinBackHandler: (((Boolean) -> Unit) -> Unit)? = null
    var onEdit: suspend (WebEditCommand) -> JSONObject = { cmd ->
        JSONObject().put("requestId",cmd.requestId).put("status","unavailable")
    }
    var state: () -> JSONObject = { JSONObject().put("bridgeVersion", VERSION) }

    fun requestSkinBack(onUnhandled: () -> Unit) {
        val handler = skinBackHandler
        if (handler == null) {
            onUnhandled()
        } else {
            handler { handled -> if (!handled) onUnhandled() }
        }
    }

    // Only the collector reads this Compose observable. Publishing from the application's
    // SideEffect does not itself cause the owning Composable to recompose.
    var stateRevision by androidx.compose.runtime.mutableLongStateOf(0L)
}

internal sealed interface WebSkinAction {
    data class Routed(val name: String) : WebSkinAction
    /** Opens the Host-owned protected AI settings form without leaving the active Skin page. */
    data object OpenAiSettings : WebSkinAction
    data object OpenProfileEditor : WebSkinAction
    data object CloseProfileEditor : WebSkinAction
    data class SaveProfileEditor(val displayName: String) : WebSkinAction
    data object RequestNotificationPermission : WebSkinAction
    data object OpenExactAlarmSettings : WebSkinAction
    data object ReconcileReminders : WebSkinAction
    data class OpenEvent(
        val eventSeq: Long,
        val expectedRevision: Long,
        val occurrenceKey: String? = null
    ) : WebSkinAction
    data class InvokeOption(val id: String) : WebSkinAction
    /** Relative browsing only; cannot change the vertical time scale. */
    data class PanTimeline(val deltaMinutes: Int) : WebSkinAction
    /** Change only the vertical time scale; the camera's center instant is retained. */
    data class ZoomTimeline(val direction: TimelineZoomDirection) : WebSkinAction
    /** Apply an exact presentation camera produced by a continuous viewport gesture. */
    data class SetTimelineCamera(val anchorMillis: Long, val hours: Double) : WebSkinAction
    /** Restore a 24-hour viewport while retaining the current camera center. */
    data object ResetTimelineZoom : WebSkinAction
    /** Select a calendar day while preserving the existing time span. */
    data class JumpTimelineDay(val date: String) : WebSkinAction
    data class SelectMonthDate(val date: LocalDate) : WebSkinAction
    data class SelectMonth(val month: YearMonth) : WebSkinAction
    data class ToggleMemo(
        val eventSeq: Long, val expectedRevision: Long,
        val occurrenceKey: String, val completed: Boolean
    ) : WebSkinAction
    /** Request the next host-bounded prefix of the current Memo projection. */
    data object LoadMoreMemo : WebSkinAction
    /** Request a bounded page before a client cache offset in one exact conversation revision. */
    data class LoadOlderChatHistory(val historyRevision: Long, val endExclusive: Int) : WebSkinAction
    data class SendChat(val text: String) : WebSkinAction
    data class ClearChat(val historyRevision: Long) : WebSkinAction
    data object ClearAiTemporaryCache : WebSkinAction
    data class RewindChat(
        val messageId: String,
        val historyRevision: Long,
        val editAndResend: Boolean
    ) : WebSkinAction
    data class CreateDraftFromChatMessage(
        val messageId: String,
        val historyRevision: Long,
        val source: ChatMessageDraftSource
    ) : WebSkinAction
    data class CopyChatMessage(val messageId: String, val historyRevision: Long) : WebSkinAction
    /** Starts the Host-owned range selection conversation; this does not authorize writes. */
    data class PrepareAiBatch(val mode: AiBatchModeV1) : WebSkinAction
    /** Cancels a pending range-selection conversation or requests stop for the active Host batch. */
    data object CancelAiBatch : WebSkinAction
    /** Requests the Host's explicit stuck-batch recovery confirmation. */
    data object ReleaseStuckAiBatch : WebSkinAction
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

internal enum class TimelineZoomDirection { IN, OUT }

internal enum class ChatMessageDraftSource { MESSAGE_CONTENT, PROPOSAL_SOURCE }

internal enum class NotificationPermissionLaunch {
    UNAVAILABLE,
    REQUEST_RUNTIME_PERMISSION,
    OPEN_APP_NOTIFICATION_SETTINGS
}

internal data class NotificationPermissionStatus(
    val required: Boolean,
    val allowed: Boolean
)

internal enum class ExactAlarmSettingsLaunch { UNAVAILABLE, OPEN_SETTINGS }

internal data class ExactAlarmPermissionStatus(
    val required: Boolean,
    val allowed: Boolean
)

internal fun notificationPermissionLaunch(page: String, sdkInt: Int): NotificationPermissionLaunch {
    if (page != "SETTINGS") return NotificationPermissionLaunch.UNAVAILABLE
    return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        NotificationPermissionLaunch.REQUEST_RUNTIME_PERMISSION
    } else {
        NotificationPermissionLaunch.OPEN_APP_NOTIFICATION_SETTINGS
    }
}

internal fun notificationPermissionStatus(
    sdkInt: Int,
    permissionAllowed: Boolean
): NotificationPermissionStatus {
    val required = sdkInt >= Build.VERSION_CODES.TIRAMISU
    return NotificationPermissionStatus(required, allowed = !required || permissionAllowed)
}

internal fun exactAlarmSettingsLaunch(page: String, sdkInt: Int): ExactAlarmSettingsLaunch =
    if (page == "SETTINGS" && sdkInt >= Build.VERSION_CODES.S) {
        ExactAlarmSettingsLaunch.OPEN_SETTINGS
    } else {
        ExactAlarmSettingsLaunch.UNAVAILABLE
    }

internal fun manualReminderReconcileAllowed(page: String): Boolean = page == "SETTINGS"

internal fun exactAlarmPermissionStatus(
    sdkInt: Int,
    permissionAllowed: Boolean
): ExactAlarmPermissionStatus {
    val required = sdkInt >= Build.VERSION_CODES.S
    return ExactAlarmPermissionStatus(required, allowed = !required || permissionAllowed)
}

internal enum class AiProposalDecision { PUBLISH, STAGE, REJECT, SUPPLEMENT }

internal fun openFormalEventFromSkin(
    action: WebSkinAction.OpenEvent,
    eventFeatures: EventFeatureApiV1,
    writerAvailable: Boolean
): EventEditorOpenResultV1? {
    if (!writerAvailable) return null
    return eventFeatures.openFormalEvent(
        action.eventSeq, action.expectedRevision, action.occurrenceKey
    )
}

internal fun webOpenEventFailureMessage(result: EventEditorOpenResultV1?): String? = when (result) {
    null -> "Calendar editor is currently unavailable"
    is EventEditorOpenResultV1.Opened -> null
    is EventEditorOpenResultV1.Stale,
    EventEditorOpenResultV1.Missing -> "Event changed or is no longer available"
    EventEditorOpenResultV1.Locked -> "当前业务操作暂时锁定了事件编辑"
    is EventEditorOpenResultV1.Invalid -> if (result.code == "EDITOR_OPEN_FAILED") {
        result.message ?: "Calendar editor is currently unavailable"
    } else {
        "Calendar editor is currently unavailable"
    }
}

@Composable
internal fun WebSkinRoot(
    bundle: WebSkinBundle,
    modifier: Modifier = Modifier,
    native: @Composable BoxScope.() -> Unit,
    onSkinFailure: () -> Unit = {}
) {
    val rootView = LocalView.current
    val density = LocalDensity.current
    val topSystemInset = remember(rootView, density) {
        val platformInsets = rootView.rootWindowInsets
        val statusBarId = rootView.resources.getIdentifier(
            "status_bar_height_default", "dimen", "android"
        ).takeIf { it != 0 } ?: rootView.resources.getIdentifier(
            "status_bar_height", "dimen", "android"
        )
        val resourceHeight = statusBarId.takeIf { it != 0 }
            ?.let { rootView.resources.getDimensionPixelSize(it) } ?: 0
        val windowInsetHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            platformInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
        } else {
            @Suppress("DEPRECATION")
            platformInsets?.systemWindowInsetTop ?: 0
        }
        // The runtime window inset can differ from the static status-bar resource under
        // edge-to-edge. Match Compose's statusBarsPadding() value whenever the window reports it.
        val topInsetPx = windowInsetHeight.takeIf { it > 0 } ?: resourceHeight
        with(density) { topInsetPx.toDp() }
    }
    var failed by remember(bundle) { mutableStateOf(false) }
    val failureReported = remember(bundle) { AtomicBoolean(false) }
    val currentOnSkinFailure by rememberUpdatedState(onSkinFailure)
    val reportFailure = {
        if (failureReported.compareAndSet(false, true)) {
            failed = true
            currentOnSkinFailure()
        }
    }
    Box(modifier) {
        if (!failed) SecureWebSkin(
            bundle,
            Modifier.fillMaxSize().padding(top = topSystemInset).navigationBarsPadding(),
            onFailure = reportFailure
        )
        if (!bundle.fullCanvas || failed) native()
    }
}

/**
 * Paint only the Activity's transparent system-bar regions. Keeping this view inside the existing
 * full-screen decor avoids dimming the Web dialog panel or changing its touch behavior.
 */
private class WebSkinModalSystemBarScrim(context: Context) {
    private val activity = requireNotNull(findActivity(context)) {
        "WebSkin needs an Activity context to match native system-bar modal dimming"
    }
    private val overlay = WebSkinSystemBarOverlayView(context)

    fun setVisible(visible: Boolean, alpha: Int = 156) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        if (visible) {
            overlay.setScrimAlpha(alpha)
            if (overlay.parent !== decor) {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                decor.addView(overlay, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }
            overlay.bringToFront()
            overlay.requestApplyInsets()
        } else {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
    }
}

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private class WebSkinSystemBarOverlayView(context: Context) : View(context) {
    private val scrimPaint = Paint().apply { color = Color.argb(156, 0, 0, 0) }
    private var statusInset = 0
    private var navigationInset = 0

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setScrimAlpha(alpha: Int) {
        scrimPaint.color = Color.argb(alpha.coerceIn(0, 255), 0, 0, 0)
        invalidate()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        updateInsets(insets)
        return super.onApplyWindowInsets(insets)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rootWindowInsets?.let(::updateInsets)
        if (statusInset > 0) {
            canvas.drawRect(0f, 0f, width.toFloat(), statusInset.toFloat(), scrimPaint)
        }
        if (navigationInset > 0) {
            val top = (height - navigationInset).coerceAtLeast(0).toFloat()
            canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), scrimPaint)
        }
    }

    private fun updateInsets(insets: WindowInsets) {
        val (top, bottom) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
            bars.top to bars.bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetTop to insets.systemWindowInsetBottom
        }
        if (statusInset != top || navigationInset != bottom) {
            statusInset = top
            navigationInset = bottom
            invalidate()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SecureWebSkin(
    bundle: WebSkinBundle,
    modifier: Modifier,
    onFailure: () -> Unit
) {
    val origin = "https://skin.powercal.local/"
    val context = androidx.compose.ui.platform.LocalContext.current
    val modalScrim = remember(bundle) { WebSkinModalSystemBarScrim(context) }
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
                        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked",
                            webSkinResponseHeaders(0),
                            ByteArrayInputStream(ByteArray(0)))
                    }
                    val path = uri.path.orEmpty().removePrefix("/").ifEmpty { bundle.entrypoint }
                    val source = bundle.files[path] ?: return WebResourceResponse(
                        "text/plain", "utf-8", 404, "Missing", webSkinResponseHeaders(0),
                        ByteArrayInputStream(ByteArray(0)))
                    val mime = webSkinMimeType(path)
                    val data = if (path == bundle.entrypoint && mime == "text/html") {
                        val density = context.resources.displayMetrics.density
                        val touchSlopDp = ViewConfiguration.get(context).scaledTouchSlop / density
                        injectWebSkinTouchSlop(source.toString(Charsets.UTF_8), touchSlopDp)
                            .toByteArray(Charsets.UTF_8)
                    } else source
                    return WebResourceResponse(mime, if (mime.startsWith("text") || mime.contains("json") || mime.contains("javascript")) "utf-8" else null,
                        ByteArrayInputStream(data)).apply {
                        responseHeaders = webSkinResponseHeaders(data.size)
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
                                    PowerCalSkinRegistry.active.web !== bundle) return@post
                                val raw = message.data.orEmpty()
                                val modalPresentation = decodeWebSkinModalVisibility(raw)
                                if (modalPresentation != null) {
                                    if (WebSkinCapabilityPolicy.mayRead(bundle)) {
                                        modalScrim.setVisible(modalPresentation.open,
                                            modalPresentation.scrimAlpha)
                                    }
                                    return@post
                                }
                                if (!WebSkinCapabilityPolicy.mayAct(bundle)) return@post
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
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    val source = runCatching { Uri.parse(message.sourceId()) }.getOrNull()
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR &&
                        message.message().startsWith("Uncaught") &&
                        source?.scheme == "https" && source.host == "skin.powercal.local") {
                        onFailure()
                    }
                    return false
                }
            }
            loadUrl(origin + bundle.entrypoint)
        }
    }
    AndroidView({ webView }, modifier)
    DisposableEffect(webView, bundle, live) {
        val handler: ((Boolean) -> Unit) -> Unit = { onHandled ->
            val invokingOwner = WebSkinBridgeRuntime.owner
            if (!live.get() || invokingOwner == null ||
                PowerCalSkinRegistry.active.web !== bundle
            ) {
                onHandled(false)
            } else {
                runCatching {
                    webView.evaluateJavascript(
                        "window.PowerCalSkin?.handleHostBack?.() === true"
                    ) { result ->
                        onHandled(live.get() &&
                            PowerCalSkinRegistry.active.web === bundle &&
                            WebSkinBridgeRuntime.owner === invokingOwner &&
                            result?.trim() == "true")
                    }
                }.onFailure { onHandled(false) }
            }
        }
        WebSkinBridgeRuntime.skinBackHandler = handler
        onDispose {
            if (WebSkinBridgeRuntime.skinBackHandler === handler) {
                WebSkinBridgeRuntime.skinBackHandler = null
            }
        }
    }
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
        modalScrim.setVisible(false)
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

private const val MAX_WEB_SKIN_ACTION_JSON_CHARS = 40_960

internal fun decodeBridgeAction(raw: String): WebSkinAction? {
    // A 6,000-code-unit chat message can expand to six JSON characters per
    // control character. Keep the envelope bounded without rejecting valid input.
    if (raw.length !in 1..MAX_WEB_SKIN_ACTION_JSON_CHARS) return null
    val message = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (message.keys().asSequence().toSet() != setOf("version", "type", "payload")) return null
    if (message.optInt("version") != WebSkinBridgeRuntime.VERSION ||
        message.optString("type") != "action") return null
    val payload = message.optJSONObject("payload") ?: return null
    val keys = payload.keys().asSequence().toSet()
    val name = payload.optString("name")
    if (name.length !in 1..40) return null
    return when (name) {
        "openAiSettings" -> if (keys == setOf("name")) WebSkinAction.OpenAiSettings else null
        "timeline", "month", "memo", "ai", "settings", "openDeck", "today" ->
            if (keys == setOf("name")) WebSkinAction.Routed(name) else null
        "openProfileEditor" -> if (keys == setOf("name"))
            WebSkinAction.OpenProfileEditor else null
        "closeProfileEditor" -> if (keys == setOf("name"))
            WebSkinAction.CloseProfileEditor else null
        "saveProfileEditor" -> {
            if (keys != setOf("name", "displayName")) return null
            val displayName = payload.opt("displayName") as? String ?: return null
            if (displayName.length <= 80 && displayName.none(Char::isISOControl))
                WebSkinAction.SaveProfileEditor(displayName) else null
        }
        "requestNotificationPermission" -> if (keys == setOf("name"))
            WebSkinAction.RequestNotificationPermission else null
        "openExactAlarmSettings" -> if (keys == setOf("name"))
            WebSkinAction.OpenExactAlarmSettings else null
        "reconcileReminders" -> if (keys == setOf("name"))
            WebSkinAction.ReconcileReminders else null
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
        "zoomTimeline" -> {
            if (keys != setOf("name", "direction")) return null
            when (payload.opt("direction")) {
                "in" -> WebSkinAction.ZoomTimeline(TimelineZoomDirection.IN)
                "out" -> WebSkinAction.ZoomTimeline(TimelineZoomDirection.OUT)
                else -> null
            }
        }
        "setTimelineCamera" -> {
            if (keys != setOf("name", "anchorMillis", "hours")) return null
            val rawAnchor = payload.opt("anchorMillis") as? Number ?: return null
            val rawHours = payload.opt("hours") as? Number ?: return null
            val anchorNumber = rawAnchor.toDouble()
            val nextHours = rawHours.toDouble()
            if (!anchorNumber.isFinite() || abs(anchorNumber) > 9_007_199_254_740_991.0 ||
                anchorNumber % 1.0 != 0.0 || !nextHours.isFinite() || nextHours !in 0.25..48.0) {
                return null
            }
            WebSkinAction.SetTimelineCamera(anchorNumber.toLong(), nextHours)
        }
        "resetTimelineZoom" ->
            if (keys == setOf("name")) WebSkinAction.ResetTimelineZoom else null
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
        "loadMoreMemo" -> if (keys == setOf("name")) WebSkinAction.LoadMoreMemo else null
        "loadOlderChatHistory" -> {
            if (keys != setOf("name", "historyRevision", "endExclusive")) return null
            val rawRevision = payload.opt("historyRevision")
            val revision = when (rawRevision) {
                is Int -> rawRevision.toLong()
                is Long -> rawRevision
                else -> null
            }
            val endExclusive = payload.opt("endExclusive") as? Int ?: return null
            revision?.takeIf { it >= 0 }
                ?.takeIf { endExclusive in 1..200 }
                ?.let { WebSkinAction.LoadOlderChatHistory(it, endExclusive) }
        }
        "browseMonth" -> {
            if (keys != setOf("name", "deltaMonths")) return null
            val raw = payload.opt("deltaMonths")
            if (raw is Int && raw in -12..12 && raw != 0)
                WebSkinAction.BrowseMonth(raw)
            else null
        }
        "selectMonthDate" -> {
            if (keys != setOf("name", "date")) return null
            val raw = payload.opt("date") as? String ?: return null
            val date = if (raw.length == 10)
                runCatching { LocalDate.parse(raw) }.getOrNull() else null
            date?.takeIf { it.year in WebSkinMonthProjection.MIN_YEAR..WebSkinMonthProjection.MAX_YEAR }
                ?.let { WebSkinAction.SelectMonthDate(it) }
        }
        "selectMonth" -> {
            if (keys != setOf("name", "month")) return null
            val raw = payload.opt("month") as? String ?: return null
            val month = if (raw.length == 7)
                runCatching { YearMonth.parse(raw) }.getOrNull() else null
            month?.takeIf { it.year in WebSkinMonthProjection.MIN_YEAR..WebSkinMonthProjection.MAX_YEAR }
                ?.let { WebSkinAction.SelectMonth(it) }
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
            if (text is String && text.isNotBlank() && text.length <= 6_000)
                WebSkinAction.SendChat(text)
            else null
        }
        "clearChat" -> {
            if (keys != setOf("name", "historyRevision")) return null
            val rawRevision = payload.opt("historyRevision")
            val revision = when (rawRevision) {
                is Int -> rawRevision.toLong()
                is Long -> rawRevision
                else -> null
            }
            revision?.takeIf { it >= 0 }?.let { WebSkinAction.ClearChat(it) }
        }
        "clearAiTemporaryCache" -> if (keys == setOf("name"))
            WebSkinAction.ClearAiTemporaryCache else null
        "rewindChat" -> {
            if (keys != setOf("name", "messageId", "historyRevision", "editAndResend")) return null
            val messageId = (payload.opt("messageId") as? String)?.takeIf { it.length in 1..100 }
                ?: return null
            val rawRevision = payload.opt("historyRevision")
            val revision = when (rawRevision) {
                is Int -> rawRevision.toLong()
                is Long -> rawRevision
                else -> null
            }?.takeIf { it >= 0 } ?: return null
            val editAndResend = payload.opt("editAndResend") as? Boolean ?: return null
            WebSkinAction.RewindChat(messageId, revision, editAndResend)
        }
        "createDraftFromChatMessage" -> {
            if (keys != setOf("name", "messageId", "historyRevision", "source")) return null
            val messageId = (payload.opt("messageId") as? String)?.takeIf { it.length in 1..100 }
                ?: return null
            val rawRevision = payload.opt("historyRevision")
            val revision = when (rawRevision) {
                is Int -> rawRevision.toLong()
                is Long -> rawRevision
                else -> null
            }?.takeIf { it >= 0 } ?: return null
            val source = when (payload.opt("source")) {
                "MESSAGE_CONTENT" -> ChatMessageDraftSource.MESSAGE_CONTENT
                "PROPOSAL_SOURCE" -> ChatMessageDraftSource.PROPOSAL_SOURCE
                else -> return null
            }
            WebSkinAction.CreateDraftFromChatMessage(messageId, revision, source)
        }
        "copyChatMessage" -> {
            if (keys != setOf("name", "messageId", "historyRevision")) return null
            val messageId = (payload.opt("messageId") as? String)?.takeIf { it.length in 1..100 }
                ?: return null
            val rawRevision = payload.opt("historyRevision")
            val revision = when (rawRevision) {
                is Int -> rawRevision.toLong()
                is Long -> rawRevision
                else -> null
            }?.takeIf { it >= 0 } ?: return null
            WebSkinAction.CopyChatMessage(messageId, revision)
        }
        "prepareAiBatch" -> {
            if (keys != setOf("name", "mode")) return null
            when (payload.opt("mode")) {
                "publish" -> WebSkinAction.PrepareAiBatch(AiBatchModeV1.PUBLISH)
                "discard" -> WebSkinAction.PrepareAiBatch(AiBatchModeV1.DISCARD)
                else -> null
            }
        }
        "cancelAiBatch" -> if (keys == setOf("name")) WebSkinAction.CancelAiBatch else null
        "releaseStuckAiBatch" -> if (keys == setOf("name"))
            WebSkinAction.ReleaseStuckAiBatch else null
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
            val baseKeys = setOf("name", "eventSeq", "expectedRevision")
            val hasOccurrenceKey = "occurrenceKey" in keys
            if (keys != baseKeys && keys != baseKeys + "occurrenceKey") return null
            val seq = payload.optLong("eventSeq", -1L)
            val revision = payload.optLong("expectedRevision", -1L)
            val rawSeq = payload.opt("eventSeq")
            val rawRevision = payload.opt("expectedRevision")
            val occurrenceKey = if (hasOccurrenceKey) {
                (payload.opt("occurrenceKey") as? String)?.takeIf { key ->
                    key.length in 1..100 && key.all {
                        it.isLetterOrDigit() || it == ':' || it == '_' || it == '-' || it == '.'
                    }
                } ?: return null
            } else null
            if (seq <= 0L || revision <= 0L || rawSeq !is Number || rawRevision !is Number ||
                rawSeq is Float || rawSeq is Double || rawRevision is Float || rawRevision is Double) null
            else WebSkinAction.OpenEvent(seq, revision, occurrenceKey)
        }
        else -> null
    }
}
