package dev.powerfulcalendar.interop

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportCheckpointTest {
    @Test fun receiptSurvivesReloadAndUpdatesWithoutDuplicatingRuns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fingerprint = "a".repeat(64)
        ImportCheckpoint.record(context, ImportCheckpoint.Snapshot(fingerprint, listOf(17L), 2))
        ImportCheckpoint.record(context, ImportCheckpoint.Snapshot(fingerprint, listOf(17L, 18L), 1))
        val matching = ImportCheckpoint.recent(context).filter { it.fingerprint == fingerprint }
        assertEquals(1, matching.size)
        assertEquals(listOf(17L, 18L), matching.single().draftIds)
        assertEquals(1, matching.single().problemCount)
    }
}
