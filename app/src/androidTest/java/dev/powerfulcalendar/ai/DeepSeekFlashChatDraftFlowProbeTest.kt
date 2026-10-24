package dev.powerfulcalendar.ai

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.util.UUID

/**
 * Opt-in live two-stage probes. They use fixed, synthetic prompts and private temporary SQLite
 * databases; they neither open UI nor touch the app's default calendar store. A model is contacted
 * only when the matching powercalLiveProbe instrumentation argument is explicitly supplied.
 */
@RunWith(AndroidJUnit4::class)
class DeepSeekFlashChatDraftFlowProbeTest {
    @Test
    fun authorizedFlashChatFlowsIntoPendingDraftWithoutPublishing() {
        assumeTrue("Pass powercalLiveProbe=deepseek to opt in to the authorized paid probe",
            InstrumentationRegistry.getArguments().getString("powercalLiveProbe") == "deepseek")
        verifyChatToPendingDraft(FLASH_MODEL_ID, "DeepSeek两阶段草稿验证")
    }

    @Test
    fun freeProviderChatFlowsIntoPendingDraftWithoutPublishing() {
        assumeTrue("Pass powercalLiveProbe=free to opt in to the free-provider probe",
            InstrumentationRegistry.getArguments().getString("powercalLiveProbe") == "free")
        verifyChatToPendingDraft(FREE_MODEL_ID, "免费模型两阶段草稿验证")
    }

    private fun verifyChatToPendingDraft(modelId: String, title: String) {
        assumeTrue("This probe is scoped to Android API 35", Build.VERSION.SDK_INT == 35)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stored = AiSettingsStore.load(context)
        assumeTrue("No existing AI endpoint/credential is configured",
            stored.configured && stored.apiKey.isNotBlank() &&
                stored.endpoint.startsWith("https://"))

        val settings = stored.copy(
            model = modelId,
            enabled = true,
            shareContext = false
        )
        val zone = ZoneId.of("Asia/Shanghai")
        val rawUserText =
            "请为合成验收创建一条活动，标题必须是“$title”，" +
                "日期为2026-11-03，开始时间14:00，结束时间14:30。只生成待确认草稿，不要发布。"
        val databaseName = "ai-chat-draft-${UUID.randomUUID()}.db"
        var store = PowerStore(context, databaseName)
        try {
            val client = AiClient(settings, zone)
            val chat = runBlocking {
                client.chat(listOf(AiChatMessage("user", rawUserText)))
            }

            assertTrue("The chat stage did not succeed", chat.succeeded)
            assertEquals("Only the typed synthetic request may enter the automatic task snapshot",
                listOf(rawUserText), chat.automaticProposalTask?.userEvidence?.map { it.text })
            assertTrue("The internal marker must not leak into the visible reply",
                !chat.reply.contains(AiChatActionParser.OPEN))

            val interpretation = runBlocking {
                client.interpret(requireNotNull(chat.automaticProposalTask), emptyList())
            }
            assertTrue(interpretation.warnings.toString(), interpretation.warnings.isEmpty())
            assertEquals(1, interpretation.proposals.size)
            val proposal = interpretation.proposals.single()
            assertEquals(TemporalKind.ACTIVITY, proposal.form.kind)
            assertEquals(title, proposal.form.title)
            assertTrue(proposal.form.isPublishable())

            val created = AiDraftService(store).applyReviewedProposals(
                interpretation, selectedPositions = setOf(0), publish = false)
            assertTrue(created.warnings.toString(), created.warnings.isEmpty())
            assertEquals(setOf(0), created.handledIndices)
            assertEquals(1, created.stagedCount)
            assertEquals(0, created.publishedCount)
            val draft = store.pendingDrafts().single()
            assertEquals(DraftStatus.PENDING, draft.status)
            assertEquals(DraftOperation.CREATE, draft.operation)
            assertEquals(proposal.form.title, draft.form.title)
            assertTrue(store.all().isEmpty())
            assertTrue(store.history(draft.eventSeq).isEmpty())
            assertNull(store.intentFor(draft.eventSeq))

            val draftId = draft.draftId
            val queueOrder = draft.queueOrder
            store.close()
            store = PowerStore(context, databaseName)
            val reopened = store.pendingDrafts().single()
            assertEquals(draftId, reopened.draftId)
            assertEquals(queueOrder, reopened.queueOrder)
            assertEquals(DraftStatus.PENDING, reopened.status)
            assertTrue(store.all().isEmpty())
            assertTrue(store.history(reopened.eventSeq).isEmpty())
            assertNull(store.intentFor(reopened.eventSeq))
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    private companion object {
        const val FLASH_MODEL_ID = "deepseek/deepseek-v4.1-flash"
        const val FREE_MODEL_ID = "stealth/glyph-cluster:free"
    }
}
