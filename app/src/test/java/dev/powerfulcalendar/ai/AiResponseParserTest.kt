package dev.powerfulcalendar.ai

import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiResponseParserTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val known = listOf(
        TemporalRecord(
            eventSeq = 5, revision = 2, kind = TemporalKind.ACTIVITY, title = "周会",
            startMillis = LocalDateTime.of(2026, 10, 23, 15, 0).atZone(zone).toInstant().toEpochMilli(),
            endMillis = LocalDateTime.of(2026, 10, 23, 16, 0).atZone(zone).toInstant().toEpochMilli(),
            zoneId = zone.id
        )
    )

    @Test
    fun parsesCreateAndReplaceProposals() {
        val raw = """
            {"events":[
              {"operation":"CREATE","kind":"ALARM","title":"起床","start":"2026-10-21 07:30",
               "reminderMinutes":0,"enabled":true},
              {"operation":"REPLACE","eventSeq":5,"kind":"ACTIVITY","title":"周会改期",
               "start":"2026-10-23 16:00","end":"2026-10-23 17:00"}
            ]}
        """.trimIndent()
        val result = AiResponseParser.parse(raw, known, zone)
        assertEquals(2, result.proposals.size)
        assertEquals(DraftOperation.CREATE, result.proposals[0].operation)
        assertEquals("起床", result.proposals[0].form.title)
        assertEquals(DraftOperation.REPLACE, result.proposals[1].operation)
        assertEquals(5L, result.proposals[1].eventSeq)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun aReplaceWithoutARealEventFallsBackToCreateInsteadOfGuessing() {
        val raw = """
            {"events":[{"operation":"REPLACE","eventSeq":99,"kind":"ALARM","title":"幽灵",
            "start":"2026-10-21 07:30"}]}
        """.trimIndent()
        val result = AiResponseParser.parse(raw, known, zone)
        assertEquals(DraftOperation.CREATE, result.proposals.single().operation)
        assertNull(result.proposals.single().eventSeq)
        assertTrue(result.warnings.any { it.contains("不存在的事件") })
    }

    @Test
    fun unknownKindIsDroppedRatherThanCoerced() {
        val raw = """{"events":[{"operation":"CREATE","kind":"TASK","title":"不该出现"}]}"""
        val result = AiResponseParser.parse(raw, known, zone)
        assertTrue(result.proposals.isEmpty())
        assertTrue(result.warnings.any { it.contains("类型未知") })
    }

    @Test
    fun missingTimeStaysMissing() {
        val raw = """
            {"events":[{"operation":"CREATE","kind":"ACTIVITY","title":"只有标题",
            "start":"","end":""}]}
        """.trimIndent()
        val result = AiResponseParser.parse(raw, known, zone)
        val form = result.proposals.single().form
        assertEquals("", form.startText)
        assertEquals("", form.endText)
        assertTrue(form.missingFields().isNotEmpty())
        assertTrue(result.warnings.any { it.contains("还缺") })
    }

    @Test
    fun fencedJsonIsAcceptedButArbitraryProseIsNot() {
        val fenced = """
            ```json
            {"events":[{"operation":"CREATE","kind":"DEADLINE","title":"交材料",
            "deadlineDate":"2026-10-24"}]}
            ```
        """.trimIndent()
        val parsed = AiResponseParser.parse(fenced, known, zone)
        assertEquals(1, parsed.proposals.size)
        assertEquals("2026-10-24", parsed.proposals.single().form.endText)
        assertEquals(TemporalKind.DEADLINE, parsed.proposals.single().form.kind)

        val junk = AiResponseParser.parse("抱歉，我无法解析这段内容。", known, zone)
        assertTrue(junk.proposals.isEmpty())
        assertTrue(junk.warnings.isNotEmpty())
    }

    @Test
    fun dateOnlyDeadlineIsNotTurnedIntoANightTime() {
        val raw = """
            {"events":[{"operation":"CREATE","kind":"DEADLINE","title":"截止",
            "deadlineDate":"2026-10-24","deadlineTime":""}]}
        """.trimIndent()
        val form = AiResponseParser.parse(raw, known, zone).proposals.single().form
        assertEquals("2026-10-24", form.endText)
        assertNotNull(form.parseDeadline())
        assertTrue(form.endText.length == 10)
    }

    @Test
    fun malformedTimesAreRejectedNotNormalized() {
        val raw = """
            {"events":[{"operation":"CREATE","kind":"ACTIVITY","title":"坏时间",
            "start":"明天下午","end":"2026-10-21 10:00"}]}
        """.trimIndent()
        val result = AiResponseParser.parse(raw, known, zone)
        assertEquals("", result.proposals.single().form.startText)
        assertTrue(result.warnings.any { it.contains("格式不可用") })
    }

    @Test
    fun settingsEndpointNormalizationAcceptsBothShapes() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            AiSettings.normalizeEndpoint("https://api.example.com/v1")
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            AiSettings.normalizeEndpoint("https://api.example.com/v1/chat/completions")
        )
        assertEquals("", AiSettings.normalizeEndpoint("  "))
    }
}
