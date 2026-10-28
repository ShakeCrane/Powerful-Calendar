package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebSkinBridgeTest {
    @Test fun documentedActionPayloadExamplesMatchTheHostDecoder() {
        val payloadSchemas = WebSkinBridgeContractFixture.load()
            .getJSONObject("\$defs").getJSONObject("actionPayloads")
        val names = payloadSchemas.keys()
        while (names.hasNext()) {
            val name = names.next()
            val schema = payloadSchemas.getJSONObject(name)
            val requiredArray = schema.getJSONArray("required")
            val required = (0 until requiredArray.length()).map { requiredArray.getString(it) }
            val properties = schema.getJSONObject("properties")
            val examples = schema.getJSONArray("examples")
            for (index in 0 until examples.length()) {
                val payload = examples.getJSONObject(index)
                val payloadKeys = payload.keys().asSequence().toSet()
                assertTrue("$name example has all declared required fields",
                    required.all(payloadKeys::contains))
                assertTrue("$name example contains only declared properties",
                    payloadKeys.all(properties::has))
                val wire = JSONObject().put("version", 1).put("type", "action")
                    .put("payload", payload)
                assertTrue("JSON Schema example for $name must reach its Host action",
                    decodeBridgeAction(wire.toString()) != null)

                for (field in required) {
                    val missing = JSONObject(wire.toString())
                    missing.getJSONObject("payload").remove(field)
                    assertNull("$name must reject missing required field $field",
                        decodeBridgeAction(missing.toString()))
                }
                val extra = JSONObject(wire.toString())
                extra.getJSONObject("payload").put("untrustedExtra", true)
                assertNull("$name must reject fields outside its schema",
                    decodeBridgeAction(extra.toString()))
            }
        }
    }

    @Test fun acceptsOnlyClosedTypedActions() {
        assertEquals(WebSkinAction.Routed("today"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"today"}}"""))
        assertEquals(WebSkinAction.Routed("settings"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"settings"}}"""))
        assertEquals(WebSkinAction.Routed("skinSettings"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"skinSettings"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"skinSettings","page":"TOOLS"}}"""))
        assertEquals(WebSkinAction.OpenEvent(42, 7), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"openEvent","eventSeq":42,"expectedRevision":7}}"""))
        assertEquals(WebSkinAction.OpenEvent(42, 7, "2026-10-22T09:00"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"openEvent","eventSeq":42,"expectedRevision":7,"originalOccurrenceKey":"2026-10-22T09:00"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"openEvent","eventSeq":42,"expectedRevision":7,"originalOccurrenceKey":"2026-10-22T09:00:30"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"openEvent","eventSeq":42,"expectedRevision":7,"url":"https://evil.test"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"openEvent","eventSeq":"42","expectedRevision":7}}"""))
        assertNull(decodeBridgeAction(
            """{"version":2,"type":"action","payload":{"name":"today"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"deleteEvent"}}"""))
        assertEquals(WebSkinAction.InvokeOption("calendar.import.file"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"calendar.import.file"}}"""))
        assertEquals(WebSkinAction.InvokeOption("calendar.export.ics"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"calendar.export.ics"}}"""))
        assertEquals(WebSkinAction.InvokeOption("data.backup.export"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"data.backup.export"}}"""))
        assertEquals(WebSkinAction.InvokeOption("data.backup.restore"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"data.backup.restore"}}"""))
        assertEquals(WebSkinAction.InvokeOption("data.legacy.migrate"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"data.legacy.migrate"}}"""))
        assertEquals(WebSkinAction.InvokeOption("reminders.exactAlarmSettings"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"reminders.exactAlarmSettings"}}"""))
        assertEquals(WebSkinAction.InvokeOption("reminders.notificationPermission"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"reminders.notificationPermission"}}"""))
        assertEquals(WebSkinAction.InvokeOption("reminders.reconcile"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"reminders.reconcile"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"untrusted.delete"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"calendar.import.file","force":true}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"data.backup.restore","content":"{}"}}"""))
        assertEquals(WebSkinAction.SelectSkin("my-skin_2"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"selectSkin","skinId":"my-skin_2"}}"""))
        assertEquals(WebSkinAction.DeleteSkin("my-skin_2"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"deleteSkin","skinId":"my-skin_2"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"selectSkin","skinId":"../private"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"deleteSkin","skinId":"powercal-default","confirm":true}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"selectSkin","skinId":42}}"""))
        assertNull(decodeBridgeAction("x".repeat(8_193)))
        assertTrue(decodeBridgeAction("") == null)
    }

    @Test fun eventOpenRejectsMissingStaleMismatchedAndClosedWriter() {
        val record = TemporalRecord(42, 7, TemporalKind.MEMO, "safe")
        val action = WebSkinAction.OpenEvent(42, 7)
        assertEquals(record, validateOpenEvent(action, record, true))
        assertNull(validateOpenEvent(action, null, true))
        assertNull(validateOpenEvent(action, record.copy(revision = 8), true))
        assertNull(validateOpenEvent(action, record.copy(eventSeq = 43), true))
        assertNull(validateOpenEvent(action, record, false))
    }

    @Test fun eventOpenAcceptsOnlyARealProjectedRecurrenceSlot() {
        val zone = java.time.ZoneId.of("UTC")
        val start = java.time.LocalDateTime.of(2026, 10, 22, 9, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val repeating = TemporalRecord(
            eventSeq = 42, revision = 7, kind = TemporalKind.ACTIVITY, title = "Daily",
            startMillis = start, endMillis = start + 3_600_000,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3), zoneId = zone.id
        )
        val validSlot = WebSkinAction.OpenEvent(42, 7, "2026-10-23T09:00")
        assertEquals(repeating, validateOpenEvent(validSlot, repeating, true))
        assertNull(validateOpenEvent(
            WebSkinAction.OpenEvent(42, 7, "2026-10-25T09:00"), repeating, true
        ))
        assertNull(validateOpenEvent(validSlot, repeating.copy(recurrence = Recurrence.NONE), true))
    }

    @Test fun settingsStayInSkinUntilExplicitHostToolsRequest() {
        val timeline = WebSkinNavigationState("TIMELINE")
        val settings = reduceWebSkinNavigation(timeline, "skinSettings")
        assertEquals(WebSkinNavigationState("TIMELINE", skinSettingsOpen = true), settings)
        assertEquals(WebSkinNavigationState("TOOLS"),
            reduceWebSkinNavigation(timeline, "settings"))

        val nativeTools = reduceWebSkinNavigation(settings, "settings")
        assertEquals(WebSkinNavigationState("TOOLS"), nativeTools)
        assertEquals(nativeTools, reduceWebSkinNavigation(nativeTools, "skinSettings"))
        assertEquals(WebSkinNavigationState("TIMELINE"),
            reduceWebSkinNavigation(settings, "timeline"))
        assertEquals(settings, reduceWebSkinNavigation(settings, "unknown"))
    }
}
