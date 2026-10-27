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
        )),
        SemanticOptionGroup("calendar.export", "导出", listOf(
            SemanticOption("calendar.export.ics", "导出 Activity ICS", "仅包含 Activity；其他类型和完成状态请用原生备份", "export")
        )),
        SemanticOptionGroup("data.backup", "数据备份", listOf(
            SemanticOption("data.backup.export", "导出原生数据备份", "包含日历与草稿等敏感数据的明文 JSON；请保存到安全位置", "backup"),
            SemanticOption("data.backup.restore", "恢复原生数据备份", "只允许恢复到空数据库；已有 PowerCal 数据不会被覆盖", "restore")
        )),
        SemanticOptionGroup("skin.packages", "皮肤管理", listOf(
            SemanticOption("skin.import", "导入皮肤包", "由 Android 宿主校验并安装；导入后不会自动启用", "skin"),
            SemanticOption("skin.exportClear", "导出 Clear 候选皮肤", "导出应用内置的 Clear v3 包；不更改当前皮肤", "export")
        )),
        SemanticOptionGroup("reminders.permissions", "提醒与权限", listOf(
            SemanticOption("reminders.exactAlarmSettings", "精确闹钟权限", "由 Android 系统管理精确闹钟授权", "alarm"),
            SemanticOption("reminders.notificationPermission", "通知权限", "在 Android 系统对话框或设置中管理", "notification"),
            SemanticOption("reminders.reconcile", "重新同步提醒", "根据当前日历数据重建系统提醒", "refresh")
        ))
    )
    private val allowed = groups.flatMap { it.items }.map { it.id }.toSet()
    fun supports(id: String): Boolean = id in allowed
    fun toJson(enabled: Boolean = true,
               collection: List<SemanticOptionGroup> = groups,
               disabledIds: Set<String> = emptySet(),
               includeSkinPackages: Boolean = false): JSONArray = JSONArray().apply {
        collection.filter { includeSkinPackages || it.id != "skin.packages" }.forEach { group ->
            put(JSONObject().put("id", group.id).put("label", group.label)
                .put("items", JSONArray().apply {
                    group.items.forEach { item ->
                        put(JSONObject().put("id", item.id).put("label", item.label)
                            .put("detail", item.detail).put("icon", item.icon)
                            .put("enabled", enabled && item.id !in disabledIds))
                    }
                }))
        }
    }
}

/** Exposes only non-identifying permission booleans to a skin settings page. */
internal object ReminderSettingsProjection {
    fun toJson(
        exactAlarmAllowed: Boolean,
        notificationsAllowed: Boolean,
        notificationsRuntimePermissionRequired: Boolean
    ): JSONObject =
        JSONObject()
            .put("exactAlarmAllowed", exactAlarmAllowed)
            .put("notificationsAllowed", notificationsAllowed)
            .put("notificationsRuntimePermissionRequired", notificationsRuntimePermissionRequired)
}
