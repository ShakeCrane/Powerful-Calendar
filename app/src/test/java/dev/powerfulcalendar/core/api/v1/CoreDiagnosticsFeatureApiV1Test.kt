package dev.powerfulcalendar.core.api.v1

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoreDiagnosticsFeatureApiV1Test {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var core: PowerCalCoreFeatureApiV1

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "core-diagnostics-api-${UUID.randomUUID()}.db")
        core = PowerCalCoreFeatureApiV1(store)
    }

    @After fun tearDown() {
        val databaseName = store.databaseName
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun queryReturnsCurrentStoreDiagnosticsWithoutWritingOrPublishing() {
        assertSame(core.diagnostics, core.diagnostics)
        assertEquals(1, core.diagnostics.apiVersion)
        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = core.events.subscribe(changes::add)
        try {
            val initial = core.diagnostics.queryDiagnostics()
            assertEquals(1L, initial.nextSequencePreview)
            assertEquals(0L, initial.identityCount)
            assertEquals(PowerStore.VERSION, initial.databaseVersion)
            assertEquals(store.databaseName, initial.databaseName)

            val draft = store.newDraft(
                DraftForm(
                    kind = TemporalKind.MEMO,
                    title = "diagnostics source",
                    zoneId = "UTC"
                )
            )
            assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
            val beforeQuery = StoreCounts(
                nextSequence = store.nextSequencePreview(),
                identities = store.identityCount(),
                drafts = store.draftCount(),
                commits = store.commitCount(),
                eventRevision = core.events.featureRevision
            )

            val result = core.diagnostics.queryDiagnostics()

            assertEquals(2L, result.nextSequencePreview)
            assertEquals(1L, result.identityCount)
            assertEquals(PowerStore.VERSION, result.databaseVersion)
            assertEquals(store.databaseName, result.databaseName)
            assertEquals(beforeQuery.nextSequence, result.nextSequencePreview)
            assertEquals(beforeQuery.identities, result.identityCount)
            assertEquals(beforeQuery.drafts, store.draftCount())
            assertEquals(beforeQuery.commits, store.commitCount())
            assertEquals(beforeQuery.eventRevision, core.events.featureRevision)
            assertTrue(changes.isEmpty())
        } finally {
            subscription.close()
        }
    }

    @Test fun publicContractContainsOnlyPlatformNeutralScalarValues() {
        val signatures = CoreDiagnosticsFeatureApiV1::class.java.declaredMethods
            .joinToString(" ") {
                it.genericReturnType.typeName + it.genericParameterTypes.joinToString()
            }
        assertFalse(signatures.contains("android.content.Context"))
        assertFalse(signatures.contains("PowerStore"))
        assertFalse(signatures.contains("SQLite"))
        assertEquals(Long::class.javaPrimitiveType, CoreDiagnosticsV1::class.java.getDeclaredField("identityCount").type)
        assertEquals(Long::class.javaPrimitiveType, CoreDiagnosticsV1::class.java.getDeclaredField("nextSequencePreview").type)
        assertEquals(Int::class.javaPrimitiveType, CoreDiagnosticsV1::class.java.getDeclaredField("databaseVersion").type)
        assertEquals(String::class.java, CoreDiagnosticsV1::class.java.getDeclaredField("databaseName").type)
    }

    @Test fun recurrenceFeatureIsRegisteredOnTheUnifiedCoreProvider() {
        assertSame(core.recurrence, core.recurrence)
        assertEquals(1, core.recurrence.apiVersion)
        val signatures = RecurrenceFeatureApiV1::class.java.declaredMethods
            .joinToString(" ") { method ->
                method.genericReturnType.typeName + method.genericParameterTypes.joinToString()
            }
        assertFalse(signatures.contains("android.content.Context"))
        assertFalse(signatures.contains("PowerStore"))
        assertFalse(signatures.contains("TemporalRecord"))
        assertFalse(signatures.contains("RecurrenceEngine"))
    }

    private data class StoreCounts(
        val nextSequence: Long,
        val identities: Long,
        val drafts: Long,
        val commits: Long,
        val eventRevision: Long
    )
}
