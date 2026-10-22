package dev.powerfulcalendar.ui.v2

import org.json.JSONObject

/**
 * One-way host -> skin state transport. A disconnected skin receives the latest complete
 * frame upon reconnection; unchanged state is never retransmitted. No write authority.
 */
internal class WebSkinStateFeed {
    private var lastFrame: String? = null
    private var receiver: ((String) -> Unit)? = null

    fun publish(state: JSONObject) {
        val next = JSONObject()
            .put("version", WebSkinBridgeRuntime.VERSION)
            .put("type", "state")
            .put("payload", state)
            .toString()
        if (next == lastFrame) return
        require(next.length <= 512_000) { "Web skin state projection exceeds transport limit" }
        lastFrame = next
        receiver?.invoke(next)
    }

    fun connect(send: (String) -> Unit) {
        receiver = send
        lastFrame?.let(send)
    }

    fun disconnect() {
        receiver = null
    }
}
