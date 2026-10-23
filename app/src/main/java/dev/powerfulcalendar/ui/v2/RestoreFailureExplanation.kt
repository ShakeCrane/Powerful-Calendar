package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.PowerStore

/** Stable, actionable and privacy-safe restore feedback, without leaking raw provider/file errors. */
internal object RestoreFailureExplanation {
    fun explain(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.take(8).toList()
        if (chain.any { it is PowerStore.NonEmptyRestoreTargetException }) {
            return "目标数据库不是空库，恢复已拒绝以保护现有数据。请先导出当前数据；" +
                "仅在确认备份文件可用后，于一个空的 PowerCal 数据库中恢复。不要直接覆盖已有日程。"
        }
        if (chain.any { it is SecurityException }) {
            return "无权读取所选文件。请在系统文件选择器重新授予访问权限；现有数据未覆盖。"
        }
        if (chain.any { it is org.json.JSONException ||
                (it is IllegalArgumentException &&
                    it.message?.contains("备份文件不是有效 JSON") == true) }) {
            return "所选文件不是有效的 PowerCal JSON 备份；请重新选择原始备份，现有数据未覆盖。"
        }
        if (chain.any { it.message?.contains("SHA-256") == true ||
                it.message?.contains("hash", ignoreCase = true) == true }) {
            return "备份完整性校验失败；请确认文件未损坏或被修改，现有数据未覆盖。"
        }
        return "无法安全恢复此文件。请检查备份格式、文件权限和目标数据库状态；" +
            "现有数据不会因本次失败而被主动覆盖。"
    }
}
