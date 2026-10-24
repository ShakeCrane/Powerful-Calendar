package dev.powerfulcalendar.core

import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/**
 * One invocation is run per target-process start. The host harness force-stops the package between
 * invocations while keeping this uniquely named synthetic database intact.
 */
@RunWith(AndroidJUnit4::class)
class PowerStoreProcessRestartTest {
    @Test
    fun aiOriginPendingDraftSurvivesHostDrivenProcessRestarts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val databaseName = requireNotNull(arguments.getString(ARG_DATABASE))
        val cycle = requireNotNull(arguments.getString(ARG_CYCLE)).toInt()
        require(cycle in 0..RESTART_COUNT)
        require(DATABASE_NAME.matches(databaseName))

        val context = instrumentation.targetContext
        val store = PowerStore(context, databaseName)
        var verified = false
        try {
            if (cycle == 0) {
                assertTrue("The generated database must start empty", store.pendingDrafts().isEmpty())
                store.newDraft(
                    DraftForm(
                        kind = TemporalKind.ACTIVITY,
                        title = TITLE,
                        startText = "2026-11-03 10:00",
                        endText = "2026-11-03 10:30",
                        source = "ai",
                        zoneId = ZONE.id
                    ),
                    actor = "test"
                )
            }

            val draft = store.pendingDrafts().single()
            assertEquals(DraftStatus.PENDING, draft.status)
            assertEquals(DraftOperation.CREATE, draft.operation)
            assertEquals(TITLE, draft.form.title)
            assertEquals(TemporalKind.ACTIVITY, draft.form.kind)
            assertEquals("ai", draft.form.source)
            assertEquals(ZONE.id, draft.form.zoneId)
            assertTrue("The synthetic proposal must remain complete", draft.form.isPublishable())
            assertEquals(1, store.identityCount())
            assertEquals(2L, store.nextSequencePreview())
            assertTrue("A pending draft must not create a formal event", store.all().isEmpty())
            assertTrue("A pending draft must not create commit history", store.history(draft.eventSeq).isEmpty())
            assertNull("A pending draft must not create reminder intent", store.intentFor(draft.eventSeq))

            instrumentation.sendStatus(
                0,
                Bundle().apply {
                    putString("result", "PASS")
                    putString("database_name", databaseName)
                    putInt("cycle", cycle)
                    putInt("process_pid", Process.myPid())
                    putLong("draft_id", draft.draftId)
                    putLong("event_seq", draft.eventSeq)
                    putLong("queue_order", draft.queueOrder)
                    putString("draft_status", draft.status.name)
                }
            )
            verified = true
        } finally {
            store.close()
            // On a failed final cycle, preserve the synthetic database for diagnosis.
            if (cycle == RESTART_COUNT && verified) {
                context.deleteDatabase(databaseName)
            }
        }
    }

    private companion object {
        const val ARG_DATABASE = "processRestartDbName"
        const val ARG_CYCLE = "processRestartCycle"
        const val RESTART_COUNT = 12
        const val TITLE = "PC-ISSUE-135 synthetic process-persistence draft"
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val DATABASE_NAME = Regex("freeze-process-restart-[a-f0-9-]{36}\\.db")
    }
}
