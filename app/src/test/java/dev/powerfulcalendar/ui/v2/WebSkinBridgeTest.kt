package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
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
