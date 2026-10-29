package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/** Bounded, read-only mirror of the Native Tools database/system diagnostics section. */
internal object WebSkinSystemDiagnosticsProjection {
    const val MAX_RECURRENCES = 3

    fun build(
        records: List<TemporalRecord>,
        nextSequence: Long,
        identityCount: Long,
        effects: GlassEffects,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): JSONObject {
        val recurrences = JSONArray().apply {
            records.asSequence()
                .filter { it.recurrence.isRepeating }
                .take(MAX_RECURRENCES)
                .forEach { record ->
                    val next = RecurrenceEngine.nextOccurrence(record, nowMillis, zone)
                    put(
                        JSONObject()
                            .put("label", "重复 #${record.eventSeq}")
                            .put(
                                "value",
                                next?.let { "下一次 ${DraftForm.formatInstant(it.startMillis, zone)}" }
                                    ?: "没有后续实例"
                            )
                    )
                }
        }

        return JSONObject()
            .put("formalCount", records.size)
            .put("nextSequence", TemporalRecord.formatSeq(nextSequence))
            .put("identityCount", identityCount.toString())
            .put("databaseVersion", PowerStore.VERSION)
            .put("databaseName", PowerStore.DATABASE_NAME)
            .put("blurEnabled", effects.blur)
            .put("reduceMotion", effects.reduceMotion)
            .put("recurrences", recurrences)
    }
}
