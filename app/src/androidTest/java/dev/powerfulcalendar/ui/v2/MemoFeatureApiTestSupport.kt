package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.api.v1.MemoEntryV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1

internal fun queryMemoEntriesForTest(
    store: PowerStore,
    nowMillis: Long,
    includeCompleted: Boolean = false
): List<MemoEntryV1> {
    val core = PowerCalCoreFeatureApiV1(store)
    return core.memos.queryEntries(
        core.events.queryEvents(), nowMillis, includeCompleted
    ).entries
}
