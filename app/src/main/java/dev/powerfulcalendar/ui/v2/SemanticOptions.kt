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
            SemanticOption("calendar.import.system", "从系统日历导入", "读取当前静态快照，不同步", "calendar"),
            SemanticOption("data.legacy.migrate", "迁移旧版 PowerCal 数据",
                "经 Android 宿主确认后只读导入；旧数据库文件保留", "migration")
        )),
        SemanticOptionGroup("calendar.export", "导出", listOf(
            SemanticOption("calendar.export.ics", "导出 ICS",
                "仅导出活动且不含活动启用/停用状态与活动提醒；闹钟、截止、备忘与完成状态不会由 ICS 保留。" +
                    "需要完整恢复请使用原生备份。", "file")
        )),
        SemanticOptionGroup("data.backup", "备份", listOf(
            SemanticOption("data.backup.export", "导出原生备份",
                "单一 SQLite 一致性快照与 SHA-256；含事件、身份与墓碑、草稿、提交、来源链接、迁移记录、AI 任务收据和序号水位；" +
                    "不含 API 密钥。文件为明文敏感数据。写后有界回读；无法回读时会明确提示未验证。", "file"),
            SemanticOption("data.backup.restore", "恢复原生备份",
                "仅允许恢复到空数据库；校验版本、SHA-256 完整性与数据引用（哈希不证明来源可信），" +
                    "恢复后重新核对提醒调度并显示权限状态。" +
                    "早期 flat v2 需二次确认；旧授权与旧执行记录不会恢复。", "file")
        ))
    )
    private val allowed = groups.flatMap { it.items }.map { it.id }.toSet()
    fun supports(id: String): Boolean = id in allowed
    fun toJson(enabled: Boolean = true,
               collection: List<SemanticOptionGroup> = groups): JSONArray = JSONArray().apply {
        collection.forEach { group ->
            put(JSONObject().put("id", group.id).put("label", group.label)
                .put("items", JSONArray().apply {
                    group.items.forEach { item ->
                        put(JSONObject().put("id", item.id).put("label", item.label)
                            .put("detail", item.detail).put("icon", item.icon)
                            .put("enabled", enabled))
                    }
                }))
        }
    }
}
