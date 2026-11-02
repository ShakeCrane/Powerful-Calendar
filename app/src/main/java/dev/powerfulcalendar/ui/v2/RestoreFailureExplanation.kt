package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.api.v1.BackupRestoreExceptionV1
import dev.powerfulcalendar.core.api.v1.BackupRestoreFailureKindV1

/** Stable, actionable and privacy-safe restore feedback, without leaking raw provider/file errors. */
internal object RestoreFailureExplanation {
    fun explain(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.take(8).toList()
        val failure = chain.filterIsInstance<BackupRestoreExceptionV1>().firstOrNull()?.kind
        when (failure) {
            BackupRestoreFailureKindV1.TARGET_NOT_EMPTY -> return "目标数据库不是空库，恢复已拒绝以保护现有数据。请先导出当前数据；" +
                "仅在确认备份文件可用后，于一个空的 PowerCal 数据库中恢复。不要直接覆盖已有日程。"
            BackupRestoreFailureKindV1.INPUT_TOO_LARGE ->
                return "备份文件超过 64 MiB 安全上限；请确认所选文件是有效的 PowerCal 备份。现有数据未覆盖。"
            BackupRestoreFailureKindV1.INVALID_JSON ->
                return "所选文件不是有效的 PowerCal JSON 备份；请重新选择原始备份，现有数据未覆盖。"
            BackupRestoreFailureKindV1.INTEGRITY_FAILURE ->
                return "备份完整性校验失败；请确认文件未损坏或被修改，现有数据未覆盖。"
            BackupRestoreFailureKindV1.INVALID_BACKUP ->
                return "备份格式或内容无效，恢复已拒绝；请检查文件来源和格式，现有数据未覆盖。"
            BackupRestoreFailureKindV1.LEGACY_CONSENT_REQUIRED,
            BackupRestoreFailureKindV1.RESTORE_UNAVAILABLE,
            null -> Unit
        }
        if (chain.any { it is SecurityException }) {
            return "无权读取所选文件。请在系统文件选择器重新授予访问权限；现有数据未覆盖。"
        }
        return "无法安全恢复此文件。请检查备份格式、文件权限和目标数据库状态；" +
            "现有数据不会因本次失败而被主动覆盖。"
    }
}
