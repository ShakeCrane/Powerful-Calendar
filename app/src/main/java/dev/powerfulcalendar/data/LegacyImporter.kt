package dev.powerfulcalendar.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord

/**
 * One-way, explicitly requested migration from the v0.1 prototype database.
 *
 * The old file is opened READ-ONLY and never modified or deleted. Re-running the migration is
 * idempotent because every legacy row keeps a stable `legacy-v01:<id>` external id.
 *
 * v0.1 stored intervals only (start + end) and delegated system clocks to the OEM alarm app, so
 * imported rows become ACTIVE activities; there is no old PowerCal alarm data to hand over.
 */
object LegacyImporter {
    const val SOURCE = "legacy-v01"
    const val LEGACY_DATABASE = "powerful-calendar.db"

    data class Result(
        val available: Boolean,
        val batchId: String = "",
        val imported: Int = 0,
        val skipped: Int = 0,
        val warnings: List<String> = emptyList()
    )

    fun migrate(context: Context, store: PowerStore): Result {
        val file = context.getDatabasePath(LEGACY_DATABASE)
        if (!file.exists()) {
            return Result(available = false, warnings = listOf("没有找到旧版数据库，无需迁移"))
        }
        val legacy = runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrElse {
            return Result(available = true, warnings = listOf("无法只读打开旧版数据库：" + it.message))
        }
        val batchId = store.beginMigration(SOURCE)
        val warnings = mutableListOf<String>()
        var imported = 0
        var skipped = 0
        try {
            val hasTable = legacy.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='events'", null
            ).use { it.moveToFirst() }
            if (!hasTable) {
                val warning = "旧版数据库没有 events 表"
                store.finishMigration(batchId, listOf(warning))
                return Result(available = true, batchId = batchId, warnings = listOf(warning))
            }
            legacy.rawQuery(
                """SELECT id,title,start_ms,end_ms,notes,location,priority,reminder_minutes,
                          all_day,source,external_id FROM events ORDER BY start_ms ASC""",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val legacyId = cursor.getLong(0)
                    val title = cursor.getString(1).orEmpty().ifBlank { "未命名旧事件" }
                    val start = cursor.getLong(2)
                    val end = cursor.getLong(3).coerceAtLeast(start + 60_000L)
                    val notes = cursor.getString(4).orEmpty()
                    val location = cursor.getString(5).orEmpty()
                    val allDay = cursor.getInt(8) == 1
                    val origin = cursor.getString(9).orEmpty().ifBlank { "local" }
                    val record = TemporalRecord(
                        eventSeq = 0L,
                        revision = 0L,
                        kind = TemporalKind.ACTIVITY,
                        title = title,
                        notes = if (notes.isBlank()) "" else notes,
                        location = location,
                        startMillis = start,
                        endMillis = end,
                        reminderMinutes = cursor.getInt(7),
                        enabled = true,
                        source = SOURCE,
                        createdAtMillis = start
                    )
                    val externalId = "v01:$legacyId"
                    when (val outcome = store.importStructured(
                        record = record,
                        externalId = externalId,
                        batchId = batchId,
                        source = SOURCE
                    )) {
                        is PowerStore.ImportOutcome.Imported -> imported++
                        is PowerStore.ImportOutcome.AlreadyImported -> skipped++
                        is PowerStore.ImportOutcome.Rejected -> {
                            skipped++
                            warnings.add("旧事件 #$legacyId 校验失败：" + outcome.reason)
                        }
                    }
                    if (allDay) {
                        warnings.add("旧版全天事件「$title」按原始起止时间导入为活动，请确认展示是否符合预期")
                    }
                    if (origin == "android-calendar" || origin == "ics") {
                        warnings.add("旧事件「$title」来自 $origin，保留为历史来源标记")
                    }
                }
            }
        } catch (error: Exception) {
            warnings.add("读取旧版数据库时中断：" + (error.message ?: "未知错误"))
        } finally {
            legacy.close()
        }
        store.finishMigration(batchId, warnings.take(50))
        return Result(true, batchId, imported, skipped, warnings.take(50))
    }
}
