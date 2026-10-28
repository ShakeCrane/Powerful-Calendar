package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.reminders.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebSkinReminderProjectionTest {
    private fun input(
        id: Long,
        millis: Long = id * 60_000,
        title: String = "Reminder $id",
        state: PowerStore.DesiredSchedule? = PowerStore.DesiredSchedule.PENDING,
        execution: String? = null,
        hint: String? = null,
        kind: TemporalKind = TemporalKind.ALARM
    ) = ReminderDiagnosticInput(
        record = TemporalRecord(
            eventSeq = id,
            revision = 3,
            kind = kind,
            title = title,
            notes = "PRIVATE_NOTE_credential=do-not-project",
            location = "PRIVATE_LOCATION_token=do-not-project",
            startMillis = millis,
            reminderMinutes = 0
        ),
        intent = state?.let {
            PowerStore.ScheduleIntent(
                eventSeq = id,
                triggerId = "primary",
                revision = 3,
                occurrenceKey = "SECRET_OCCURRENCE_KEY",
                instantMillis = millis,
                requirement = PowerStore.TriggerRequirement.INEXACT,
                desired = it,
                androidHint = hint
            )
        },
        lastExecution = execution
    )

    @Test fun projectionIsStableBoundedAndContainsOnlyTheDocumentedItemFields() {
        val entries = (1L..45L).map { input(it, millis = 45_000 - it * 100L) }
        val first = WebSkinReminderProjection.toJson(entries)
        val reversed = WebSkinReminderProjection.toJson(entries.reversed())

        assertEquals(first.toString(), reversed.toString())
        assertEquals(WebSkinReminderProjection.ITEM_LIMIT, first.getInt("itemLimit"))
        assertTrue(first.getBoolean("truncated"))
        val items = first.getJSONArray("items")
        assertEquals(40, items.length())
        val expectedKeys = setOf(
            "displayId", "title", "titleTruncated", "kind", "desiredState", "androidHint", "lastExecution"
        )
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            assertEquals(expectedKeys, item.keys().asSequence().toSet())
            assertEquals("reminder-${(index + 1).toString().padStart(2, '0')}", item.getString("displayId"))
            assertFalse(item.has("eventSeq"))
            assertFalse(item.has("occurrenceKey"))
            assertTrue(item.getString("title").length <= WebSkinReminderProjection.TITLE_LIMIT)
        }
        assertFalse(first.toString().contains("PRIVATE_NOTE"))
        assertFalse(first.toString().contains("PRIVATE_LOCATION"))
        assertFalse(first.toString().contains("credential"))
        assertFalse(first.toString().contains("SECRET_OCCURRENCE_KEY"))
        assertFalse(first.toString().contains("eventSeq"))
    }

    @Test fun titleHintAndExecutionAreBoundedAndUntrustedDetailIsNotProjected() {
        val longTitle = "T".repeat(150)
        val entry = input(
            id = 8,
            title = longTitle,
            state = PowerStore.DesiredSchedule.BLOCKED,
            execution = "MISSED",
            hint = "缺少精确闹钟权限"
        )
        val output = WebSkinReminderProjection.toJson(listOf(entry)).getJSONArray("items").getJSONObject(0)
        assertEquals(WebSkinReminderProjection.TITLE_LIMIT, output.getString("title").length)
        assertTrue(output.getBoolean("titleTruncated"))
        assertEquals("缺少精确闹钟权限", output.getString("androidHint"))
        assertEquals("MISSED", output.getString("lastExecution"))
        assertEquals("BLOCKED", output.getString("desiredState"))

        val unsafe = WebSkinReminderProjection.toJson(listOf(
            input(9, title = "Short", state = null, execution = "token=secret", hint = "Bearer secret")
        )).getJSONArray("items").getJSONObject(0)
        assertNull(unsafe.opt("androidHint").takeUnless { it === JSONObject.NULL })
        assertNull(unsafe.opt("lastExecution").takeUnless { it === JSONObject.NULL })
        assertEquals("NO_INTENT", unsafe.getString("desiredState"))
    }

    @Test fun executionOutcomeDoesNotMasqueradeAsDesiredScheduleState() {
        val productionResults = listOf(
            "FIRED", "MISSED", "BLOCKED", "SNOOZED", "SNOOZE_INEXACT",
            "IGNORED_STALE", "IGNORED_SNOOZE_STALE"
        )
        val rows = WebSkinReminderProjection.toJson(productionResults.mapIndexed { index, result ->
            input(index + 1L, state = null, execution = result)
        }).getJSONArray("items")
        for (index in productionResults.indices) {
            val row = rows.getJSONObject(index)
            val result = productionResults[index]
            assertEquals(result, row.getString("lastExecution"))
            assertEquals(if (result == "FIRED") "FIRED" else "NO_INTENT", row.getString("desiredState"))
        }

        val completedIntent = WebSkinReminderProjection.toJson(listOf(
            input(10, state = PowerStore.DesiredSchedule.FIRED, execution = "MISSED")
        )).getJSONArray("items").getJSONObject(0)
        assertEquals("FIRED", completedIntent.getString("desiredState"))
        assertEquals("MISSED", completedIntent.getString("lastExecution"))
    }

    @Test fun formalSchemaMatchesTheProductionProjectionAndHostOperationPayload() {
        val schema = readSchema()
        assertEquals(1, schema.getInt("x-contractVersion"))
        val definitions = schema.getJSONObject("\$defs")
        val diagnosticsSchema = definitions.getJSONObject("reminderDiagnostics")
        val diagnosticsProperties = diagnosticsSchema.getJSONObject("properties")
        assertEquals(WebSkinReminderProjection.ITEM_LIMIT, diagnosticsProperties.getJSONObject("itemLimit").getInt("const"))
        val itemSchema = definitions.getJSONObject("reminderDiagnosticItem")
        val itemProperties = itemSchema.getJSONObject("properties")
        val expectedItemKeys = setOf(
            "displayId", "title", "titleTruncated", "kind", "desiredState", "androidHint", "lastExecution"
        )
        assertEquals(expectedItemKeys, itemProperties.keys().asSequence().toSet())
        assertEquals(WebSkinReminderProjection.TITLE_LIMIT, itemProperties.getJSONObject("title").getInt("maxLength"))
        assertEquals(WebSkinReminderProjection.HINT_LIMIT, itemProperties.getJSONObject("androidHint").getInt("maxLength"))
        assertEquals(WebSkinReminderProjection.EXECUTION_LIMIT, itemProperties.getJSONObject("lastExecution").getInt("maxLength"))
        assertEquals(
            listOf("ALARM", "ACTIVITY", "DEADLINE", "MEMO"),
            itemProperties.getJSONObject("kind").getJSONArray("enum").strings()
        )
        assertEquals(
            listOf("NO_INTENT", "PENDING", "SCHEDULED", "BLOCKED", "CANCELLED", "FIRED"),
            itemProperties.getJSONObject("desiredState").getJSONArray("enum").strings()
        )
        assertEquals(
            listOf("FIRED", "MISSED", "BLOCKED", "SNOOZED", "SNOOZE_INEXACT", "IGNORED_STALE", "IGNORED_SNOOZE_STALE", null),
            itemProperties.getJSONObject("lastExecution").getJSONArray("enum").nullableStrings()
        )
        assertEquals(false, itemSchema.getBoolean("additionalProperties"))
        assertEquals(40, diagnosticsProperties.getJSONObject("items").getInt("maxItems"))

        val reportSchema = definitions.getJSONObject("reminderReconcile")
        val reportProperties = reportSchema.getJSONObject("properties")
        assertEquals(setOf("status", "checked", "scheduled", "blocked", "pending", "failed", "truncated"),
            reportProperties.keys().asSequence().toSet())
        val successWithBlocked = ReminderReconcileSnapshot.completed(
            ReminderScheduler.ReconcileReport(checked = 2, scheduled = 1, blocked = 1, pending = 0, failed = 0)
        ).toJson()
        assertEquals("SUCCEEDED", successWithBlocked.getString("status"))
        assertEquals(1, successWithBlocked.getInt("blocked"))
        assertFalse(successWithBlocked.getBoolean("truncated"))
        val incomplete = ReminderReconcileSnapshot.completed(
            ReminderScheduler.ReconcileReport(checked = 2, scheduled = 1, blocked = 0, pending = 1, failed = 0)
        ).toJson()
        assertEquals("FAILED", incomplete.getString("status"))
        assertEquals("FAILED", ReminderReconcileSnapshot.completed(
            ReminderScheduler.ReconcileReport(checked = 2, scheduled = 1, blocked = 0, pending = 0, failed = 1)
        ).status)
        val truncated = ReminderReconcileSnapshot.completed(
            ReminderScheduler.ReconcileReport(
                checked = 500, scheduled = 0, blocked = 0, pending = 0, failed = 0, truncated = true
            )
        )
        assertEquals("FAILED", truncated.status)
        assertTrue(truncated.truncated)

        val actualProjection = WebSkinReminderProjection.toJson(listOf(input(10, title = "A".repeat(140))))
        assertMatchesDiagnosticSchema(actualProjection, diagnosticsProperties, itemProperties)
        assertMatchesCountSchema(successWithBlocked, reportProperties)
    }

    private fun readSchema(): JSONObject {
        var cursor: Path? = Paths.get("").toAbsolutePath()
        while (cursor != null) {
            val file = cursor.resolve("docs/contracts/web-skin-reminder-state-v1.schema.json")
            if (Files.isRegularFile(file)) return JSONObject(file.toFile().readText())
            cursor = cursor.parent
        }
        error("Could not locate docs/contracts/web-skin-reminder-state-v1.schema.json")
    }

    private fun assertMatchesDiagnosticSchema(
        payload: JSONObject,
        schema: JSONObject,
        itemSchema: JSONObject
    ) {
        assertEquals(schema.getJSONObject("itemLimit").getInt("const"), payload.getInt("itemLimit"))
        assertTrue(payload.getJSONArray("items").length() <= schema.getJSONObject("items").getInt("maxItems"))
        val items = payload.getJSONArray("items")
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            assertEquals(itemSchema.keys().asSequence().toSet(), item.keys().asSequence().toSet())
            assertTrue(item.getString("title").length <= itemSchema.getJSONObject("title").getInt("maxLength"))
            assertTrue(item.getString("kind") in itemSchema.getJSONObject("kind").getJSONArray("enum").strings())
            assertTrue(item.getString("desiredState") in itemSchema.getJSONObject("desiredState").getJSONArray("enum").strings())
        }
    }

    private fun assertMatchesCountSchema(payload: JSONObject, properties: JSONObject) {
        for (key in listOf("checked", "scheduled", "blocked", "pending", "failed")) {
            assertTrue(payload.getInt(key) >= properties.getJSONObject(key).getInt("minimum"))
        }
        assertTrue(payload.has("truncated"))
        assertTrue(payload.getString("status") in properties.getJSONObject("status").getJSONArray("enum").strings())
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.nullableStrings(): List<String?> = (0 until length()).map {
        if (isNull(it)) null else getString(it)
    }
}
