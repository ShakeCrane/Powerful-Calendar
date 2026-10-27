package dev.powerfulcalendar.ui.v2

import java.net.URI
import java.util.UUID

/**
 * Gives each WebView session a fresh origin. WebView service workers and their settings are
 * process-global/persistent, so one skin must not control a later skin's origin.
 */
internal class WebSkinOrigin private constructor(val host: String) {
    val baseUrl: String get() = "https://$host/"

    fun entrypoint(path: String): String = baseUrl + path

    fun acceptsResource(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host == host && uri.port == -1 &&
            uri.rawUserInfo == null && uri.rawQuery == null
    }

    fun isEntrypoint(url: String, path: String): Boolean = url == entrypoint(path)

    companion object {
        fun newSession(): WebSkinOrigin = WebSkinOrigin(
            UUID.randomUUID().toString().replace("-", "") + ".skin.powercal.local"
        )
    }
}
