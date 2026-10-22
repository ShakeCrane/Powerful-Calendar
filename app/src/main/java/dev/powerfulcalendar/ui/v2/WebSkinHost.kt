package dev.powerfulcalendar.ui.v2

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Versioned, deliberately tiny capability bridge. It never exposes Context or PowerStore. */
internal object WebSkinBridgeRuntime {
    var actions: Map<String, () -> Unit> = emptyMap()
    var state: () -> JSONObject = { JSONObject().put("bridgeVersion", 1) }
}

@Composable
internal fun WebSkinRoot(
    bundle: WebSkinBundle,
    modifier: Modifier = Modifier,
    native: @Composable BoxScope.() -> Unit
) {
    var failed by remember(bundle) { mutableStateOf(false) }
    Box(modifier) {
        if (!failed) SecureWebSkin(bundle, Modifier.fillMaxSize(), onFailure = { failed = true })
        // Host-owned confirmation/editor surfaces are composed outside this root and cannot be
        // obscured by a bridge command. Native content remains the accessibility fallback.
        if (!bundle.fullCanvas || failed) native()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SecureWebSkin(bundle: WebSkinBundle, modifier: Modifier, onFailure: () -> Unit) {
    val origin = "https://skin.powercal.local/"
    val context = androidx.compose.ui.platform.LocalContext.current
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
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.host != "skin.powercal.local"

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url
                    if (uri.scheme != "https" || uri.host != "skin.powercal.local") {
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
                        else -> "application/octet-stream"
                    }
                    return WebResourceResponse(mime, if (mime.startsWith("text") || mime.contains("json") || mime.contains("javascript")) "utf-8" else null,
                        ByteArrayInputStream(data)).apply {
                        responseHeaders = mapOf(
                            "Content-Security-Policy" to "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'",
                            "X-Content-Type-Options" to "nosniff"
                        )
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    val channels = view.createWebMessageChannel()
                    channels[0].setWebMessageCallback(object : android.webkit.WebMessagePort.WebMessageCallback() {
                        override fun onMessage(port: android.webkit.WebMessagePort, message: android.webkit.WebMessage) {
                            handleBridge(message.data.orEmpty())
                            port.postMessage(android.webkit.WebMessage(stateMessage().toString()))
                        }
                    })
                    view.postWebMessage(android.webkit.WebMessage("powercal-port", arrayOf(channels[1])), Uri.parse(origin))
                    channels[0].postMessage(android.webkit.WebMessage(stateMessage().toString()))
                }
            }
            loadUrl(origin + bundle.entrypoint)
        }
    }
    AndroidView({ webView }, modifier)
    LaunchedEffect(webView) {
        kotlinx.coroutines.delay(5_000)
        if (webView.progress < 100) onFailure()
    }
    DisposableEffect(webView) { onDispose { webView.stopLoading(); webView.destroy() } }
}

private fun stateMessage(): JSONObject = JSONObject()
    .put("version", 1).put("type", "state").put("payload", WebSkinBridgeRuntime.state())

private fun handleBridge(raw: String) {
    if (raw.length > 8_192) return
    val message = runCatching { JSONObject(raw) }.getOrNull() ?: return
    if (message.optInt("version") != 1 || message.optString("type") != "action") return
    val payload = message.optJSONObject("payload") ?: return
    val name = payload.optString("name")
    if (name.length > 40) return
    WebSkinBridgeRuntime.actions[name]?.invoke()
}
