package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.api.v1.AiBatchModeV1
import dev.powerfulcalendar.core.api.v1.AiBatchSessionV1
import dev.powerfulcalendar.core.api.v1.AiBatchStateV1
import dev.powerfulcalendar.core.api.v1.AiBatchTerminalReportV1
import dev.powerfulcalendar.core.api.v1.DraftOperationV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebAiBatchProjectionTest {
    @Test
    fun progressTrackerIsSessionFencedMonotonicAndClearedByOwner() {
        val tracker = WebAiBatchProgressTracker()
        tracker.begin("first", AiBatchModeV1.PUBLISH, total = 3)
        tracker.report("first", AiBatchModeV1.PUBLISH, done = 1, total = 3)
        tracker.report("first", AiBatchModeV1.PUBLISH, done = 0, total = 3)
        tracker.report("stale", AiBatchModeV1.PUBLISH, done = 2, total = 3)

        assertEquals(WebAiBatchProgress("first", AiBatchModeV1.PUBLISH, 1, 3),
            tracker.progress.value)
        tracker.begin("second", AiBatchModeV1.DISCARD, total = 2)
        tracker.finish("first")
        assertEquals(WebAiBatchProgress("second", AiBatchModeV1.DISCARD, 0, 2),
            tracker.progress.value)
        tracker.report("second", AiBatchModeV1.DISCARD, done = 2, total = 2)
        tracker.finish("second")
        assertEquals(null, tracker.progress.value)
    }

    @Test
    fun activeStateAndTerminalReportExposeOutcomesWithoutAuthorizationIdentity() {
        val session = AiBatchSessionV1(
            sessionId = "private-session-id",
            lockEpoch = 991,
            scope = setOf(8101, 8102),
            allowedOperations = setOf(DraftOperationV1.CREATE),
            state = AiBatchStateV1.CANCELLED,
            startedAtMillis = 10,
            updatedAtMillis = 20,
            terminalReport = AiBatchTerminalReportV1(
                mode = dev.powerfulcalendar.core.api.v1.AiBatchModeV1.PUBLISH,
                outcomeKnown = true,
                published = 1,
                discarded = 0,
                kept = 1,
                cancelled = true,
                hasFailures = true
            ),
            reviewedVersions = mapOf(8101L to 3L, 8102L to 5L)
        )

        val projected = WebAiBatchProjection.project(
            session, preparationMode = null, reviewMode = null,
            reviewCards = null, progress = null
        )

        assertEquals("CANCELLED", projected.getString("phase"))
        assertFalse(projected.getBoolean("active"))
        assertFalse(projected.getBoolean("canCancel"))
        assertEquals("PUBLISH", projected.getJSONObject("report").getString("mode"))
        assertEquals(1, projected.getJSONObject("report").getInt("published"))
        assertEquals(1, projected.getJSONObject("report").getInt("kept"))
        assertTrue(projected.getJSONObject("report").getBoolean("cancelled"))
        assertTrue(projected.getJSONObject("report").getBoolean("hasFailures"))
        val wire = projected.toString()
        assertFalse(wire.contains("private-session-id"))
        assertFalse(wire.contains("991"))
        assertFalse(wire.contains("8101"))
        assertFalse(wire.contains("8102"))
        assertFalse(wire.contains("PRIVATE_EVENT_TITLE"))
        assertFalse(wire.contains("reviewedVersions"))
    }

    @Test
    fun preparationAndHostReviewAreDistinctFromAuthorization() {
        val preparing = WebAiBatchProjection.project(
            session = null,
            preparationMode = AiBatchModeV1.DISCARD,
            reviewMode = null,
            reviewCards = null,
            progress = null
        )
        assertEquals("SELECTING", preparing.getString("phase"))
        assertEquals("DISCARD", preparing.getString("mode"))
        assertFalse(preparing.getBoolean("active"))

        val card = DraftCard(
            draftId = 8101,
            eventSeq = 72,
            queueOrder = 1,
            operation = DraftOperation.DELETE,
            form = DraftForm(kind = TemporalKind.MEMO, title = "待删事项"),
            status = DraftStatus.PENDING,
            baseRevision = 2,
            draftVersion = 3
        )
        val awaitingHost = WebAiBatchProjection.project(
            session = null,
            preparationMode = null,
            reviewMode = AiBatchModeV1.PUBLISH,
            reviewCards = listOf(card),
            progress = null
        )
        assertEquals("AWAITING_HOST_REVIEW", awaitingHost.getString("phase"))
        assertEquals(1, awaitingHost.getInt("selectedCount"))
        assertTrue(awaitingHost.getBoolean("containsDelete"))
        assertFalse(awaitingHost.toString().contains("8101"))
        assertFalse(awaitingHost.toString().contains("待删事项"))
    }

    @Test
    fun progressModeIsUsedOnlyForTheMatchingActiveSession() {
        val session = AiBatchSessionV1(
            sessionId = "active-session", lockEpoch = 7, scope = emptySet(),
            allowedOperations = emptySet(), state = AiBatchStateV1.RUNNING,
            startedAtMillis = 10, updatedAtMillis = 20
        )
        val matching = WebAiBatchProgress(
            sessionId = "active-session", mode = AiBatchModeV1.PUBLISH, done = 1, total = 2
        )
        val stale = matching.copy(sessionId = "previous-session", mode = AiBatchModeV1.DISCARD)

        val currentProjection = WebAiBatchProjection.project(
            session, null, null, null, matching
        )
        val staleProjection = WebAiBatchProjection.project(
            session, null, null, null, stale
        )
        val terminalProjection = WebAiBatchProjection.project(
            session.copy(state = AiBatchStateV1.COMPLETED), null, null, null, matching
        )

        assertEquals("PUBLISH", currentProjection.getString("mode"))
        assertTrue(staleProjection.isNull("mode"))
        assertTrue(terminalProjection.isNull("mode"))
    }

    @Test
    fun stuckRecoveryIsOnlyProjectedForTimedOutCancellationAndHidesUnknownCounts() {
        val running = AiBatchSessionV1(
            sessionId = "running-private", lockEpoch = 7, scope = emptySet(),
            allowedOperations = emptySet(), state = AiBatchStateV1.RUNNING,
            startedAtMillis = 1, updatedAtMillis = 2
        )
        val requested = running.copy(
            sessionId = "cancel-private", state = AiBatchStateV1.CANCEL_REQUESTED,
            canReleaseStuck = true
        )
        val freshCancel = requested.copy(canReleaseStuck = false)

        assertFalse(WebAiBatchProjection.project(running, null, null, null, null)
            .getBoolean("canRelease"))
        assertFalse(WebAiBatchProjection.project(freshCancel, null, null, null, null)
            .getBoolean("canRelease"))
        assertTrue(WebAiBatchProjection.project(requested, null, null, null, null)
            .getBoolean("canRelease"))

        val uncertain = requested.copy(
            state = AiBatchStateV1.FAILED,
            terminalReport = AiBatchTerminalReportV1(
                mode = dev.powerfulcalendar.core.api.v1.AiBatchModeV1.PUBLISH,
                outcomeKnown = false,
                releasedByUser = true
            )
        )
        val report = WebAiBatchProjection.project(uncertain, null, null, null, null)
            .getJSONObject("report")
        assertFalse(report.getBoolean("outcomeKnown"))
        assertFalse(report.has("published"))
        assertFalse(report.has("discarded"))
        assertFalse(report.has("kept"))
    }
}
