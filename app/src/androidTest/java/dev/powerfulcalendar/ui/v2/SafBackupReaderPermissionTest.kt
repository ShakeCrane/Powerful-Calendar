package dev.powerfulcalendar.ui.v2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.interop.RevocableSafTestProvider
import dev.powerfulcalendar.interop.SafBackupReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SafBackupReaderPermissionTest {
    @Test
    fun revokedUriGrantSurfacesSafeFailureWithoutChangingStore() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val uri = RevocableSafTestProvider.SYNTHETIC_URI
        val databaseName = "saf-revoked-" + UUID.randomUUID() + ".db"
        val store = PowerStore(targetContext, databaseName)

        try {
            targetContext.contentResolver.call(
                uri, "grantRead", targetContext.packageName, null
            )
            assertEquals(
                RevocableSafTestProvider.SYNTHETIC_BACKUP,
                SafBackupReader.readText(targetContext.contentResolver, uri)
            )

            val zone = ZoneId.of("Asia/Shanghai")
            val form = DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = "合成权限撤销保护",
                startText = "2026-11-03 10:00",
                endText = "2026-11-03 10:30",
                zoneId = zone.id
            )
            val draft = store.newDraft(form, actor = "test")
            assertTrue(store.commit(draft.draftId, actor = "test") is
                PowerStore.CommitOutcome.Committed)
            val beforeEvents = store.all()
            val beforeHistory = store.history(draft.eventSeq)
            val beforeIdentityCount = store.identityCount()
            val beforeNextSequence = store.nextSequencePreview()

            targetContext.contentResolver.call(
                uri, "revokeRead", targetContext.packageName, null
            )
            val failure = runCatching {
                SafBackupReader.readText(targetContext.contentResolver, uri)
            }.exceptionOrNull()

            assertTrue("Expected the revoked SAF grant to be enforced by Android", failure is SecurityException)
            val message = RestoreFailureExplanation.explain(requireNotNull(failure))
            assertTrue(message.contains("无权读取所选文件"))
            assertTrue(message.contains("现有数据未覆盖"))
            assertEquals(beforeEvents, store.all())
            assertEquals(beforeHistory, store.history(draft.eventSeq))
            assertTrue(store.pendingDrafts().isEmpty())
            assertEquals(beforeIdentityCount, store.identityCount())
            assertEquals(beforeNextSequence, store.nextSequencePreview())
        } finally {
            runCatching {
                targetContext.contentResolver.call(
                    uri, "revokeRead", targetContext.packageName, null
                )
            }
            store.close()
            targetContext.deleteDatabase(databaseName)
        }
    }
}
