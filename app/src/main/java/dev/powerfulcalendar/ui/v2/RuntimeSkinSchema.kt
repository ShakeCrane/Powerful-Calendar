package dev.powerfulcalendar.ui.v2

import org.json.JSONArray
import org.json.JSONObject

/**
 * Declarative, non-executable .pcskin v2 UI bytecode. Only these built-in node and intent names
 * can reach Compose. No reflection, expressions, resource URLs, scripts or user classes.
 */
data class RuntimeSkinDocument(
    val screens: Map<String, RuntimeSkinNode>,
    val transition: String,
    val durationMs: Int
)

data class RuntimeSkinNode(
    val type: String,
    val text: String = "",
    val action: String = "",
    val children: List<RuntimeSkinNode> = emptyList(),
    val padding: Int = 0,
    val gap: Int = 8,
    val grow: Boolean = false,
    val color: String = "",
    val motion: String = "none",
    val durationMs: Int = 220
)

internal object RuntimeSkinSchema {
    val screens = setOf("timeline", "month", "memo", "ai", "settings",
        "deck", "navigation", "overlay", "chrome", "picker")
    val types = setOf("column", "row", "box", "card", "scroll", "text", "button",
        "spacer", "native", "events", "todos", "chats", "chat-input", "deck-actions",
        "skins", "nav-items", "overlay-title", "overlay-body", "overlay-confirm",
        "overlay-dismiss", "window-content", "profile", "credentials",
        "picker-title", "picker-date", "picker-time", "picker-confirm", "picker-dismiss")
    val actions = mapOf(
        "timeline" to setOf("today", "memo", "deck", "month", "settings",
            "new-alarm", "new-activity", "new-deadline", "native"),
        "month" to setOf("back", "new", "native"),
        "memo" to setOf("back", "new", "native"),
        "ai" to setOf("settings", "deck", "pick-image", "pick-file",
            "clear-cache", "native"),
        "settings" to setOf("back", "deck", "import-skin", "export-skin",
            "import-ics", "export-ics", "export-backup", "restore-backup",
            "notification-permission", "exact-alarm", "native"),
        "deck" to setOf("back", "commit", "discard", "undo", "save", "native"),
        "navigation" to setOf("timeline", "month", "ai", "settings", "native"),
        "overlay" to setOf("native"),
        "chrome" to setOf("native"),
        "picker" to setOf("native")
    )
    private val colors = setOf("primary", "primaryContainer", "surface",
        "surfaceContainer", "surfaceContainerHigh", "surfaceContainerLow", "background")
    private val motions = setOf("none", "fade", "slide-up", "slide-left", "scale")
    private val struct = setOf("column", "row", "box", "card", "scroll")
    private val leaf = types - struct
    private val fields = setOf("type", "text", "action", "children", "padding",
        "gap", "grow", "color", "motion", "durationMs")
    private const val MAX_NODES = 384

    fun parse(bytes: ByteArray): RuntimeSkinDocument {
        require(bytes.size <= 120_000) { "v2 UI 结构文件超过 120KB" }
        val doc = JSONObject(bytes.toString(Charsets.UTF_8))
        require(keys(doc) == setOf("format", "version", "screens", "transition", "durationMs") ||
            keys(doc) == setOf("format", "version", "screens")) {
            "UI 文档字段不合法"
        }
        require(doc.getString("format") == "powercal-runtime-ui" && doc.getInt("version") == 1) {
            "UI 运行时协议不兼容"
        }
        val transition = doc.optString("transition", "fade")
        val duration = doc.optInt("durationMs", 220)
        require(transition in motions && duration in 0..600) { "页面转场参数不合法" }
        val rawScreens = doc.getJSONObject("screens")
        val names = keys(rawScreens)
        require(names.isNotEmpty() && names.all { it in screens }) { "UI 页面名不合法" }
        var count = 0
        fun readNode(json: JSONObject, screen: String, depth: Int,
                     scrollDepth: Int = 0): RuntimeSkinNode {
            require(depth <= 10 && ++count <= MAX_NODES) { "UI 树深度或节点数超限" }
            require(keys(json).all { it in fields }) { "UI 节点含未知字段" }
            val type = json.getString("type")
            require(scrollDepth == 0 || type != "scroll") { "???????????" }
            require(screen != "deck" || type != "native") {
                "?????? native ???????????"
            }
            require(type in types) { "不支持组件类型：$type" }
            val text = json.optString("text", "")
            val action = json.optString("action", "")
            val color = json.optString("color", "")
            val motion = json.optString("motion", "none")
            val padding = json.optInt("padding", 0)
            val gap = json.optInt("gap", 8)
            val durationMs = json.optInt("durationMs", 220)
            val grow = json.optBoolean("grow", false)
            require(text.length <= 160 && action.length <= 40 &&
                padding in 0..40 && gap in 0..32 && durationMs in 0..600 &&
                color in colors + "" && motion in motions) { "组件样式参数超限" }
            require(action.isEmpty() || (type == "button" && action in actions.getValue(screen))) {
                "页面 $screen 禁止绑定动作 $action"
            }
            if (type == "button") require(text.isNotBlank() && action.isNotEmpty()) {
                "按钮必须包含标签和宿主动作"
            }
            if (type == "native") require(action.isEmpty()) { "native 不能覆盖回调" }
            if (type in setOf("overlay-title", "overlay-body", "overlay-confirm",
                    "overlay-dismiss", "window-content"))
                require(screen == "overlay") { "仅弹窗能访问弹窗 slot" }
            if (type in setOf("profile", "credentials", "skins"))
                require(screen == "settings") { "设置控件只能出现在设置页" }
            if (type == "nav-items") require(screen == "navigation") { "导航组件只能出现在导航" }
            if (type == "events") require(screen in setOf("timeline", "month")) {
                "事件列表仅可在时间轴、月历显示"
            }
            if (type == "todos") require(screen == "memo") { "Memo 数据不得跨页" }
            if (type in setOf("chats", "chat-input")) require(screen == "ai") {
                "AI 数据不得跨页"
            }
            if (type == "deck-actions") require(screen == "deck") { "草稿操作不得跨页" }
            val raw = json.optJSONArray("children") ?: JSONArray()
            require(raw.length() <= 30 && (type !in leaf || raw.length() == 0)) {
                "组件的子节点不合法"
            }
            val children = (0 until raw.length()).map {
                readNode(raw.getJSONObject(it), screen, depth + 1,
                    scrollDepth + if (type == "scroll") 1 else 0)
            }
            return RuntimeSkinNode(type, text, action, children, padding, gap,
                grow, color, motion, durationMs)
        }
        val parsed = names.associateWith { name ->
            readNode(rawScreens.getJSONObject(name), name, 1)
        }
        fun hasType(node: RuntimeSkinNode, type: String): Boolean =
            node.type == type || node.children.any { hasType(it, type) }
        // For fail-safe user recovery and modal authorization, some surfaces must retain
        // a real host-owned outlet. A wrong ZIP cannot hide all navigation or confirmation.
        parsed["chrome"]?.let { require(hasType(it, "native")) {
            "根容器必须保留宿主内容 native"
        } }
        parsed["navigation"]?.let {
            require(hasType(it, "nav-items") || hasType(it, "native")) {
                "导航必须包含原生导航或完整 nav-items 控件"
            }
        }
        parsed["overlay"]?.let {
            require(hasType(it, "native") ||
                (hasType(it, "overlay-body") && hasType(it, "overlay-confirm") &&
                    hasType(it, "overlay-dismiss") && hasType(it, "window-content"))) {
                "弹窗必须保留正文、确认、取消和窗口内容，不得隐藏授权操作"
            }
        }
        parsed["picker"]?.let {
            require(hasType(it, "native") ||
                (hasType(it, "picker-title") && hasType(it, "picker-date") &&
                    hasType(it, "picker-time") && hasType(it, "picker-confirm") &&
                    hasType(it, "picker-dismiss"))) {
                "??????????????????????"
            }
        }
        return RuntimeSkinDocument(parsed, transition, duration)
    }

    private fun keys(json: JSONObject): Set<String> {
        val iterator = json.keys()
        return buildSet { while (iterator.hasNext()) add(iterator.next()) }
    }
}
