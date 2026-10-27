package dev.powerfulcalendar.ai

import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assert.*
import org.junit.Test

class AiPartialProposalFallbackTest {
    private fun task(prompt: String): AiConversationTaskSnapshot {
        val now = 1793059200000L
        val history = listOf(AiChatMessage(
            role = "user",
            content = prompt,
            createdAtMillis = now,
            zoneId = "Asia/Shanghai"
        ))
        return checkNotNull(AiConversationTaskSnapshot.from(
            history, now, "Asia/Shanghai"
        ))
    }

    @Test fun missingAlarmTimeStillYieldsEditableReviewNotFormalEvent() {
        val result = AiPartialProposalFallback.recover(
            task("帮我创建一个闹钟"),
            AiInterpretation(warnings = listOf("模型 events 为空"))
        )
        assertEquals(1, result.proposals.size)
        val item = result.proposals.single()
        assertEquals(TemporalKind.ALARM, item.form.kind)
        assertTrue(item.form.startText.isEmpty())
        assertFalse(item.form.isPublishable())
        assertEquals(0, item.form.reminderMinutes)
        assertTrue(item.form.notes.contains("帮我创建一个闹钟"))
    }

    @Test fun explicitMultipleEventsYieldIndependentIncompleteCards() {
        val result = AiPartialProposalFallback.recover(
            task("帮我创建 5 个重叠的活动"),
            AiInterpretation()
        )
        assertEquals(5, result.proposals.size)
        assertEquals((0..4).toList(), result.proposals.map { it.sourceIndex })
        assertTrue(result.proposals.all { it.eventSeq == null && !it.form.isPublishable() })
    }

    @Test fun timedDeadlineDefaultsToSixtyMinutesOnFallback() {
        val result = AiPartialProposalFallback.recover(
            task("帮我创建一个截止事件"),
            AiInterpretation()
        )
        val form = result.proposals.single().form
        assertEquals(TemporalKind.DEADLINE, form.kind)
        assertEquals(60, form.reminderMinutes)
        assertFalse(form.isPublishable())
    }

    @Test fun unknownAndModificationRequestsNeverFabricateCreateCards() {
        val empty = AiInterpretation(warnings = listOf("no type"))
        assertEquals(empty, AiPartialProposalFallback.recover(task("帮我创建一个事项"), empty))
        val modified = AiPartialProposalFallback.recover(
            task("帮我创建一个闹钟").let { prior ->
                prior.copy(userEvidence = prior.userEvidence.map {
                    it.copy(text = "帮我修改一个闹钟")
                })
            }, empty
        )
        assertTrue(modified.proposals.isEmpty())
    }

    @Test fun validStructuredProposalIsPreservedUnchanged() {
        val original = AiInterpretation(proposals = listOf(AiProposal(
            operation = dev.powerfulcalendar.core.DraftOperation.CREATE,
            eventSeq = null,
            form = dev.powerfulcalendar.core.DraftForm(
                kind = TemporalKind.ACTIVITY, title = "Test"
            )
        )))
        assertEquals(original, AiPartialProposalFallback.recover(task("帮我创建一个闹钟"), original))
    }
}
