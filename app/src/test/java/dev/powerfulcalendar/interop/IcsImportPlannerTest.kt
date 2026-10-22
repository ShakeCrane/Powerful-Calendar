package dev.powerfulcalendar.interop

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class IcsImportPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun calendar(vararg blocks: String) =
        ("BEGIN:VCALENDAR\nVERSION:2.0\n" + blocks.joinToString("\n") +
            "\nEND:VCALENDAR\n")

    private fun event(uid: String, end: String = "DTEND:20261023T093000Z") = """
        BEGIN:VEVENT
        UID:$uid
        DTSTART:20261023T080000Z
        $end
        SUMMARY:Independent $uid
        END:VEVENT
    """.trimIndent()

    @Test fun partialSuccessRetainsIndependentValidEvent() {
        val plan = IcsImportPlanner.prepare(calendar(event("good"), event("bad", "")), zone)
        assertEquals(2, plan.units)
        assertEquals(1, plan.candidates.size)
        assertEquals(1, plan.problems.size)
        assertEquals("uid:good", plan.candidates.single().sourceId)
        assertEquals("uid:bad", plan.problems.single().sourceId)
    }

    @Test fun recurrenceOverrideBlocksOnlyItsOwnSeries() {
        val master = event("series").replace(
            "SUMMARY:Independent series", "RRULE:FREQ=WEEKLY;COUNT=8\nSUMMARY:Class")
        val override = event("series").replace(
            "SUMMARY:Independent series", "RECURRENCE-ID:20261030T080000Z\nSUMMARY:Moved")
        val plan = IcsImportPlanner.prepare(calendar(master, override, event("separate")), zone)
        assertEquals(2, plan.units)
        assertEquals(listOf("uid:separate"), plan.candidates.map { it.sourceId })
        assertEquals("uid:series", plan.problems.single().sourceId)
    }

    @Test fun nestedAlarmDoesNotOverwriteOuterDescription() {
        val item = event("withalarm").replace("END:VEVENT", """
            DESCRIPTION:Main event
            BEGIN:VALARM
            DESCRIPTION:Reminder text
            ACTION:DISPLAY
            TRIGGER:-PT10M
            END:VALARM
            END:VEVENT
        """.trimIndent())
        val plan = IcsImportPlanner.prepare(calendar(item), zone)
        assertTrue(plan.problems.toString(), plan.problems.isEmpty())
        assertEquals("Main event", plan.candidates.single().form.notes)
    }

    @Test fun todoCannotMasqueradeAsTimedActivity() {
        val todo = """
            BEGIN:VTODO
            UID:todo
            SUMMARY:Finish task
            DTSTART:20261023T080000Z
            DUE:20261023T093000Z
            END:VTODO
        """.trimIndent()
        val plan = IcsImportPlanner.prepare(calendar(todo, event("good")), zone)
        assertEquals(1, plan.candidates.size)
        assertEquals(1, plan.problems.size)
        assertTrue(plan.problems.single().reason.contains("VTODO"))
    }

    @Test fun structuralCorruptionRejectsWholeFile() {
        assertThrows(IllegalArgumentException::class.java) {
            IcsImportPlanner.prepare("BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:x\n", zone)
        }
    }

    @Test fun allDayWithoutEndPreservesDateIntent() {
        val item = """
            BEGIN:VEVENT
            UID:allday
            DTSTART;VALUE=DATE:20261023
            SUMMARY:All day seminar
            END:VEVENT
        """.trimIndent()
        val plan = IcsImportPlanner.prepare(calendar(item), ZoneId.systemDefault())
        assertTrue(plan.problems.toString(), plan.problems.isEmpty())
        assertEquals(1, plan.candidates.size)
        assertTrue(plan.candidates.single().form.notes.contains("日期精度"))
    }

    @Test fun timedEventWithDurationIsNotGuessed() {
        val item = event("duration", "DURATION:PT90M")
        val plan = IcsImportPlanner.prepare(calendar(item), zone)
        assertTrue(plan.problems.toString(), plan.problems.isEmpty())
        assertEquals(1, plan.candidates.size)
    }

    @Test fun sameStaticFileHasSameFingerprint() {
        val input = calendar(event("stable"))
        assertEquals(IcsImportPlanner.prepare(input, zone).fingerprint,
            IcsImportPlanner.prepare(input, zone).fingerprint)
    }
}
