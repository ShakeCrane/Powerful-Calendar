package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import org.json.JSONObject
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
    fun activeStateAndTerminalReportExposeOutcomesWithoutAuthorizationIdentity() {
        val session = PowerStore.AiSession(
            sessionId = "private-session-id",
            lockEpoch = 991,
            scope = setOf(8101, 8102),
            allowedOperations = setOf(DraftOperation.CREATE),
            state = PowerStore.AiState.CANCELLED,
            startedAtMillis = 10,
            updatedAtMillis = 20,
            report = JSONObject()
                .put("mode", "PUBLISH")
                .put("published", 1)
                .put("discarded", 0)
                .put("kept", 1)
                .put("cancelled", true)
                .put("failures", "#8102：版本冲突：PRIVATE_EVENT_TITLE")
                .toString(),
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
            preparationMode = AiBatchRunner.Mode.DISCARD,
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
            reviewMode = AiBatchRunner.Mode.PUBLISH,
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
        val session = PowerStore.AiSession(
            sessionId = "active-session", lockEpoch = 7, scope = emptySet(),
            allowedOperations = emptySet(), state = PowerStore.AiState.RUNNING,
            startedAtMillis = 10, updatedAtMillis = 20
        )
        val matching = WebAiBatchProgress(
            sessionId = "active-session", mode = AiBatchRunner.Mode.PUBLISH, done = 1, total = 2
        )
        val stale = matching.copy(sessionId = "previous-session", mode = AiBatchRunner.Mode.DISCARD)

        val currentProjection = WebAiBatchProjection.project(
            session, null, null, null, matching
        )
        val staleProjection = WebAiBatchProjection.project(
            session, null, null, null, stale
        )
        val terminalProjection = WebAiBatchProjection.project(
            session.copy(state = PowerStore.AiState.COMPLETED), null, null, null, matching
        )

        assertEquals("PUBLISH", currentProjection.getString("mode"))
        assertTrue(staleProjection.isNull("mode"))
        assertTrue(terminalProjection.isNull("mode"))
    }

    @Test
    fun stuckRecoveryIsOnlyProjectedForTimedOutCancellationAndHidesUnknownCounts() {
        val now = 100_000_000L
        val running = PowerStore.AiSession(
            sessionId = "running-private", lockEpoch = 7, scope = emptySet(),
            allowedOperations = emptySet(), state = PowerStore.AiState.RUNNING,
            startedAtMillis = now - PowerStore.DEFAULT_STALE_MS * 2,
            updatedAtMillis = now - PowerStore.DEFAULT_STALE_MS * 2
        )
        val requested = running.copy(
            sessionId = "cancel-private", state = PowerStore.AiState.CANCEL_REQUESTED,
            updatedAtMillis = now - PowerStore.DEFAULT_STALE_MS
        )
        val freshCancel = requested.copy(updatedAtMillis = now)

        assertFalse(WebAiBatchProjection.project(running, null, null, null, null, now)
            .getBoolean("canRelease"))
        assertFalse(WebAiBatchProjection.project(freshCancel, null, null, null, null, now)
            .getBoolean("canRelease"))
        assertTrue(WebAiBatchProjection.project(requested, null, null, null, null, now)
            .getBoolean("canRelease"))

        val uncertain = requested.copy(
            state = PowerStore.AiState.FAILED,
            report = JSONObject().put("mode", "PUBLISH").put("released", "user")
                .put("outcomeKnown", false).put("published", 99).put("discarded", 1)
                .put("kept", 0).toString()
        )
        val report = WebAiBatchProjection.project(uncertain, null, null, null, null, now)
            .getJSONObject("report")
        assertFalse(report.getBoolean("outcomeKnown"))
        assertFalse(report.has("published"))
        assertFalse(report.has("discarded"))
        assertFalse(report.has("kept"))
    }
}
