package dev.powerfulcalendar.interop

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal durable checkpoint for a user-selected static import. Stores no original file,
 * provider credentials, raw evidence or private notes. Uses synchronous commit so that
 * completion is reported only after the latest receipt has been persisted.
 */
object ImportCheckpoint {
    private const val PREFS = "powercal-import-checkpoints"
    private const val KEY = "recent-runs"
    private const val MAX_RUNS = 12

    data class Snapshot(val fingerprint: String, val draftIds: List<Long>, val problemCount: Int)

    @Synchronized
    fun recent(context: Context): List<Snapshot> {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        if (text.length > 100_000) return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val entry = array.optJSONObject(i) ?: return@mapNotNull null
                val hash = entry.optString("fingerprint")
                if (!hash.matches(Regex("[a-fA-F0-9]{64}"))) return@mapNotNull null
                val ids = entry.optJSONArray("draftIds") ?: JSONArray()
                Snapshot(hash, (0 until ids.length()).mapNotNull { j ->
                    ids.optLong(j).takeIf { it > 0L }
                }, entry.optInt("problemCount"))
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun record(context: Context, snapshot: Snapshot) {
        require(snapshot.fingerprint.matches(Regex("[a-fA-F0-9]{64}")))
        val history = (recent(context).filterNot { it.fingerprint == snapshot.fingerprint } +
            snapshot).takeLast(MAX_RUNS)
        val array = JSONArray()
        history.forEach { item ->
            array.put(JSONObject()
                .put("fingerprint", item.fingerprint)
                .put("draftIds", JSONArray(item.draftIds))
                .put("problemCount", item.problemCount))
        }
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, array.toString()).commit()) { "导入恢复收据未能持久化" }
    }
}
