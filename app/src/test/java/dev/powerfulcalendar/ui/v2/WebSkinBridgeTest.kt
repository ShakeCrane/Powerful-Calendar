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
        assertEquals(WebSkinAction.LoadMoreMemo, decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"loadMoreMemo"}}"""))
        assertNull(decodeBridgeAction(
            """{"version":1,"type":"action","payload":{"name":"loadMoreMemo","count":250}}"""))
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
}
