package dev.powerfulcalendar.ui.v2

import org.json.JSONArray
import org.json.JSONObject

/**
 * Host-owned semantic choices: v3 skins may render a whole group with one reusable visual
 * component. Adding a sibling option does not add a new WebView permission or modify skins.
 * A hand-written, static HTML menu still stays static; native Settings is always reachable.
 */
internal data class SemanticOption(
    val id: String,
    val label: String,
    val detail: String,
    val icon: String
)

internal data class SemanticOptionGroup(
    val id: String,
    val label: String,
    val items: List<SemanticOption>
)

internal object SemanticOptions {
    val groups = listOf(
        SemanticOptionGroup("calendar.import", "导入", listOf(
            SemanticOption("calendar.import.file", "导入日历文件", "选择文件，由本地程序优先处理", "file"),
            SemanticOption("calendar.import.system", "从系统日历导入", "读取当前静态快照，不同步", "calendar")
        ))
    )
    private val allowed = groups.flatMap { it.items }.map { it.id }.toSet()
    fun supports(id: String): Boolean = id in allowed
    fun toJson(): JSONArray = JSONArray().apply {
        groups.forEach { group ->
            put(JSONObject().put("id", group.id).put("label", group.label)
                .put("items", JSONArray().apply {
                    group.items.forEach { item ->
                        put(JSONObject().put("id", item.id).put("label", item.label)
                            .put("detail", item.detail).put("icon", item.icon)
                            .put("enabled", true))
                    }
                }))
        }
    }
}
