package dev.powerfulcalendar.ui.v2

import android.webkit.CookieManager
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import java.io.ByteArrayInputStream

/**
 * Service worker settings belong to the WebView process, not to one WebView.
 * Packaged skins are offline documents and have no service-worker network capability.
 */
internal object WebSkinServiceWorkerPolicy {
    @Volatile
    private var installed = false

    @Synchronized
    fun install() {
        if (installed) return

        CookieManager.getInstance().setAcceptCookie(false)
        val controller = ServiceWorkerController.getInstance()
        configure(controller.serviceWorkerWebSettings.let(::AndroidSettingsPort))
        controller.setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse =
                blockedResponse()
        })
        installed = true
    }

    internal fun configure(settings: SettingsPort) {
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
    }

    private fun blockedResponse() = WebResourceResponse(
        "text/plain",
        "utf-8",
        403,
        "Blocked",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0))
    )

    internal interface SettingsPort {
        var allowFileAccess: Boolean
        var allowContentAccess: Boolean
        var blockNetworkLoads: Boolean
        var cacheMode: Int
    }

    private class AndroidSettingsPort(
        private val delegate: android.webkit.ServiceWorkerWebSettings
    ) : SettingsPort {
        override var allowFileAccess: Boolean
            get() = delegate.allowFileAccess
            set(value) { delegate.allowFileAccess = value }
        override var allowContentAccess: Boolean
            get() = delegate.allowContentAccess
            set(value) { delegate.allowContentAccess = value }
        override var blockNetworkLoads: Boolean
            get() = delegate.blockNetworkLoads
            set(value) { delegate.blockNetworkLoads = value }
        override var cacheMode: Int
            get() = delegate.cacheMode
            set(value) { delegate.cacheMode = value }
    }
}
