package dev.powerfulcalendar.interop

import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.TemporalKind
import java.security.MessageDigest
import java.time.ZoneId
import java.time.ZoneOffset

/** Each candidate is a complete, independently importable semantic unit. */
data class IcsCandidate(val sourceId: String, val form: DraftForm)

/** A failed semantic unit is retained for explanation or a narrowly scoped AI clarification. */
data class IcsProblem(val sourceId: String, val reason: String, val evidence: String)

data class IcsImportPlan(
    val fingerprint: String,
    val candidates: List<IcsCandidate>,
    val problems: List<IcsProblem>,
    val units: Int
)

/**
 * Strict structural preflight before item-level partial import. An overridden occurrence and
 * its master must never be imported independently. Unsupported RFC constructs are deferred,
 * not replaced with guessed appointments. No network or PowerStore access occurs here.
 */
object IcsImportPlanner {
    private data class Component(val kind: String, val lines: List<String>) {
        val raw: String get() = lines.joinToString("\r\n", postfix = "\r\n")
        val uid: String? get() = lines.firstOrNull { it.substringBefore(':').substringBefore(';')
            .equals("UID", ignoreCase = true) }?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
        fun has(name: String) = lines.any {
            it.substringBefore(':').substringBefore(';').equals(name, ignoreCase = true)
        }
        fun value(name: String): String? = lines.firstOrNull {
            it.substringBefore(':').substringBefore(';').equals(name, ignoreCase = true)
        }?.substringAfter(':')
        fun dateKey(): String? = lines.firstOrNull {
            it.substringBefore(':').substringBefore(';').equals("DTSTART", ignoreCase = true)
        }?.substringBefore(':')
    }

    fun prepare(text: String, deviceZone: ZoneId = ZoneId.systemDefault()): IcsImportPlan {
        require(text.length <= 2_000_000) { "ICS 文件超过本地安全上限" }
        // Unfold lines before reading UID or component boundaries.
        val lines = mutableListOf<String>()
        text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
            .split('\n').forEach { line ->
                if ((line.startsWith(' ') || line.startsWith('\t')) && lines.isNotEmpty()) {
                    lines[lines.lastIndex] += line.drop(1)
                } else lines += line
            }
        val stack = mutableListOf<String>()
        val components = mutableListOf<Component>()
        var active: MutableList<String>? = null
        var sawCalendar = false
        var closedCalendar = false
        for (line in lines) {
            val normalized = line.trim()
            if (normalized.startsWith("BEGIN:", ignoreCase = true)) {
                val name = normalized.substringAfter(':').uppercase()
                if (stack.isEmpty()) {
                    require(name == "VCALENDAR" && !sawCalendar) { "缺少唯一 VCALENDAR 根组件" }
                    sawCalendar = true
                } else require(!closedCalendar) { "VCALENDAR 结束后仍有组件" }
                if (stack.size == 1 && name in setOf("VEVENT", "VTODO")) {
                    require(active == null) { "ICS 组件发生重叠" }
                    active = mutableListOf()
                }
                active?.add(line)
                stack += name
            } else if (normalized.startsWith("END:", ignoreCase = true)) {
                val name = normalized.substringAfter(':').uppercase()
                require(stack.isNotEmpty() && stack.last() == name) { "ICS 组件开始和结束不匹配：$name" }
                active?.add(line)
                if (stack.size == 2 && name in setOf("VEVENT", "VTODO")) {
                    val block = requireNotNull(active)
                    components += Component(name, block.toList())
                    active = null
                    require(components.size <= 300) { "一次最多处理 300 个日历语义组件" }
                }
                stack.removeAt(stack.lastIndex)
                if (name == "VCALENDAR") closedCalendar = true
            } else {
                if (closedCalendar) require(normalized.isEmpty()) { "日历结束后有额外内容" }
                active?.add(line)
            }
        }
        require(sawCalendar && closedCalendar && stack.isEmpty() && active == null) {
            "ICS 文件结构不完整，未执行部分导入"
        }
        val fingerprint = sha(text)
        val candidates = mutableListOf<IcsCandidate>()
        val problems = mutableListOf<IcsProblem>()
        val groups = components.withIndex().groupBy { (_, component) ->
            component.uid?.let { "uid:$it" } ?: "anonymous:${sha(component.raw)}"
        }
        for ((sourceId, entries) in groups) {
            val blocks = entries.map { it.value }
            val first = blocks.first()
            val evidence = blocks.joinToString("\n") { it.raw }.take(12_000)
            fun defer(reason: String) { problems += IcsProblem(sourceId, reason, evidence) }
            if (blocks.size != 1) {
                defer("同一 UID 有多个组件，可能含修改实例或版本，暂不拆分导入")
                continue
            }
            if (first.kind != "VEVENT") {
                defer("VTODO 待办与活动语义不同，需要专用本地适配器")
                continue
            }
            if (first.has("RECURRENCE-ID") || first.has("RDATE")) {
                defer("包含修改实例或额外重复日期，现有重复模型不能无损表示")
                continue
            }
            val key = first.dateKey()
            if (key == null) {
                defer("缺少 DTSTART")
                continue
            }
            val date = first.value("DTSTART").orEmpty()
            val allDay = key.contains("VALUE=DATE", ignoreCase = true) || date.length == 8
            // The current ACTIVITY record has interval precision. Preserve the original
            // date-only intent explicitly in notes instead of silently losing it.
            fun preservePrecision(notes: String): String = if (allDay) {
                "原 ICS 为全天事件（日期精度，按全天区间展示）。" +
                    if (notes.isEmpty()) "" else "\n" + notes
            } else notes
            val parsed = IcsCodec.parse("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" +
                first.raw + "END:VCALENDAR\r\n")
            if (parsed.warnings.isNotEmpty() || parsed.events.size + parsed.series.size != 1) {
                defer(parsed.warnings.firstOrNull() ?: "组件无法安全转换")
                continue
            }
            val series = parsed.series.singleOrNull()
            if (series != null) {
                candidates += IcsCandidate(sourceId, series.form.copy(
                    notes = preservePrecision(series.form.notes)))
                continue
            }
            val event = parsed.events.single()
            val tzid = key.split(';').firstOrNull { it.startsWith("TZID=", true) }
                ?.substringAfter('=')?.trim('"')
            val sourceZone = runCatching {
                when {
                    tzid != null -> ZoneId.of(tzid)
                    date.endsWith("Z") -> ZoneOffset.UTC
                    else -> deviceZone
                }
            }.getOrNull()
            if (sourceZone == null) {
                defer("未知时区，无法安全转换")
                continue
            }
            candidates += IcsCandidate(sourceId, DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = event.title,
                startText = DraftForm.formatInstant(event.startMillis, sourceZone),
                endText = DraftForm.formatInstant(event.endMillis, sourceZone),
                notes = preservePrecision(event.notes),
                location = event.location,
                reminderMinutes = event.reminderMinutes,
                source = "ics",
                zoneId = sourceZone.id
            ))
        }
        return IcsImportPlan(fingerprint, candidates, problems, groups.size)
    }

    private fun sha(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
