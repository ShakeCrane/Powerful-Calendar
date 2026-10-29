package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(WebSkinAction.Routed("nativeSettings"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"nativeSettings"}}"""))
        assertEquals(WebSkinAction.RequestNotificationPermission, decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"requestNotificationPermission"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"requestNotificationPermission","granted":true}}"""))
        assertEquals(WebSkinAction.OpenExactAlarmSettings, decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"openExactAlarmSettings"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"openExactAlarmSettings","package":"other.app"}}"""))
        assertEquals(WebSkinAction.ReconcileReminders, decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"reconcileReminders"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"reconcileReminders","force":true}}"""))
        assertEquals(WebSkinAction.LoadMoreMemo, decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"loadMoreMemo"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"loadMoreMemo","count":250}}"""))
        assertEquals(WebSkinAction.OpenEvent(42, 7), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"openEvent","eventSeq":42,"expectedRevision":7}}"""))
        assertEquals(WebSkinAction.OpenEvent(42, 7, "2026-10-23T00:00"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"openEvent","eventSeq":42,"expectedRevision":7,"occurrenceKey":"2026-10-23T00:00"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"openEvent","eventSeq":42,"expectedRevision":7,"occurrenceKey":"../invalid"}}"""))
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
        assertEquals(WebSkinAction.InvokeOption("calendar.import.system"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"calendar.import.system"}}"""))
        assertEquals(WebSkinAction.InvokeOption("calendar.export.ics"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"calendar.export.ics"}}"""))
        assertEquals(WebSkinAction.InvokeOption("data.backup.export"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"data.backup.export"}}"""))
        assertEquals(WebSkinAction.InvokeOption("data.backup.restore"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"data.backup.restore"}}"""))
        assertEquals(WebSkinAction.InvokeOption("data.legacy.migrate"), decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"data.legacy.migrate"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"untrusted.delete"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"invokeOption","id":"calendar.import.file","force":true}}"""))
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

    @Test fun eventOpenPreservesOnlyAnOccurrenceOwnedByTheCurrentSeries() {
        val recurring = TemporalRecord(
            eventSeq = 42,
            revision = 7,
            kind = TemporalKind.MEMO,
            title = "safe",
            memoStartDate = "2026-10-22",
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 2),
            zoneId = "Asia/Shanghai"
        )
        val selectedKey = "2026-10-23T00:00"
        val action = WebSkinAction.OpenEvent(42, 7, selectedKey)
        assertEquals(recurring, validateOpenEvent(action, recurring, true))
        assertNull(validateOpenEvent(
            action.copy(occurrenceKey = "2026-10-24T00:00"), recurring, true
        ))
        assertNull(validateOpenEvent(
            action, recurring.copy(recurrence = Recurrence.NONE), true
        ))
        assertNull(validateOpenEvent(action.copy(expectedRevision = 6), recurring, true))
    }

    @Test fun notificationPermissionUsesHostOwnedPlatformFlowAndStatus() {
        assertEquals(NotificationPermissionLaunch.UNAVAILABLE,
            notificationPermissionLaunch("TIMELINE", 35))
        assertEquals(NotificationPermissionLaunch.UNAVAILABLE,
            notificationPermissionLaunch("TOOLS", 35))
        assertEquals(NotificationPermissionLaunch.REQUEST_RUNTIME_PERMISSION,
            notificationPermissionLaunch("SETTINGS", 35))
        assertEquals(NotificationPermissionLaunch.OPEN_APP_NOTIFICATION_SETTINGS,
            notificationPermissionLaunch("SETTINGS", 32))

        assertEquals(NotificationPermissionStatus(required = false, allowed = true),
            notificationPermissionStatus(32, permissionAllowed = false))
        assertEquals(NotificationPermissionStatus(required = true, allowed = false),
            notificationPermissionStatus(35, permissionAllowed = false))
        assertEquals(NotificationPermissionStatus(required = true, allowed = true),
            notificationPermissionStatus(35, permissionAllowed = true))
    }

    @Test fun exactAlarmSettingsAreAvailableOnlyOnSettingsAndAndroid12Plus() {
        assertEquals(ExactAlarmSettingsLaunch.UNAVAILABLE,
            exactAlarmSettingsLaunch("TIMELINE", 35))
        assertEquals(ExactAlarmSettingsLaunch.UNAVAILABLE,
            exactAlarmSettingsLaunch("SETTINGS", 30))
        assertEquals(ExactAlarmSettingsLaunch.OPEN_SETTINGS,
            exactAlarmSettingsLaunch("SETTINGS", 31))

        assertEquals(ExactAlarmPermissionStatus(required = false, allowed = true),
            exactAlarmPermissionStatus(30, permissionAllowed = false))
        assertEquals(ExactAlarmPermissionStatus(required = true, allowed = false),
            exactAlarmPermissionStatus(35, permissionAllowed = false))
        assertEquals(ExactAlarmPermissionStatus(required = true, allowed = true),
            exactAlarmPermissionStatus(35, permissionAllowed = true))
    }

    @Test fun manualReminderReconciliationIsSettingsOnly() {
        assertTrue(manualReminderReconcileAllowed("SETTINGS"))
        assertFalse(manualReminderReconcileAllowed("TIMELINE"))
        assertFalse(manualReminderReconcileAllowed("AI"))
    }
}
