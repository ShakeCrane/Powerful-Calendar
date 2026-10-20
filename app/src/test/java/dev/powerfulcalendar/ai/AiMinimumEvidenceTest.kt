package dev.powerfulcalendar.ai

import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiMinimumEvidenceTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test fun classNeedsOnlyTitleStartAndEndNotPlaceOrPeople() {
        val raw = """{"events":[{"title":"化学原理","start":"2026-10-20 08:00","end":"2026-10-20 09:35"}]}"""
        val proposal = AiResponseParser.parse(raw, emptyList(), zone).proposals.single()
        assertEquals(TemporalKind.ACTIVITY, proposal.form.kind)
        assertTrue(proposal.form.isPublishable())
        assertEquals("", proposal.form.location)
    }

    @Test fun deadlineNeedsOnlyTitleAndDate() {
        val raw = """{"events":[{"title":"交论文","deadlineDate":"2026-10-26"}]}"""
        val proposal = AiResponseParser.parse(raw, emptyList(), zone).proposals.single()
        assertEquals(TemporalKind.DEADLINE, proposal.form.kind)
        assertEquals("2026-10-26", proposal.form.endText)
        assertTrue(proposal.form.isPublishable())
    }

    @Test fun timedAlarmDoesNotNeedEnd() {
        val raw = """{"events":[{"title":"早起闹钟","start":"2026-10-26 07:30"}]}"""
        val proposal = AiResponseParser.parse(raw, emptyList(), zone).proposals.single()
        assertEquals(TemporalKind.ALARM, proposal.form.kind)
        assertTrue(proposal.form.isPublishable())
    }

    @Test fun exactTimestampsStrandedInNotesAreRecoveredRatherThanIgnored() {
        val raw = """{"events":[{"title":"计算机课","notes":"上课时间：2026-10-26 08:00-09:35；地点另行通知"}]}"""
        val result = AiResponseParser.parse(raw, emptyList(), zone)
        val proposal = result.proposals.single()
        assertEquals(TemporalKind.ACTIVITY, proposal.form.kind)
        assertEquals("2026-10-26 08:00", proposal.form.startText)
        assertEquals("2026-10-26 09:35", proposal.form.endText)
        assertTrue(proposal.form.isPublishable())
        assertTrue(result.warnings.any { it.contains("备注") })
    }

    @Test fun ambiguousWeekdayAndCrossMidnightNeverGetGuessed() {
        assertNull(AiTimeEvidence.recover("周一 08:00 到 09:35"))
        assertNull(AiTimeEvidence.recover("2026-10-26 23:00–01:00"))
        val raw = """{"events":[{"kind":"ACTIVITY","title":"周一实验",
          "notes":"每周一 08:00-09:35"}]}"""
        val proposal = AiResponseParser.parse(raw, emptyList(), zone).proposals.single()
        assertFalse(proposal.form.isPublishable())
    }
}
