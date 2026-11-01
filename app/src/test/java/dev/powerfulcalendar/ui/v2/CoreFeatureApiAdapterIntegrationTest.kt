package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.api.v1.EventEditorOpenResultV1
import dev.powerfulcalendar.core.api.v1.EventFeatureChangeTypeV1
import dev.powerfulcalendar.core.api.v1.EventFeatureApiV1
import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.core.api.v1.EventMutationStatusV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoreApiAdapterTest {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var core: PowerCalCoreFeatureApiV1
    private lateinit var api: EventFeatureApiV1
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-10-25T15:07:42Z"), ZoneOffset.UTC)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "ca-${UUID.randomUUID()}.db")
        core = PowerCalCoreFeatureApiV1(store, clock)
        api = core.events
    }

    @After fun tearDown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    @Test fun nativeAndWebShareSameCoreApi() = runBlocking {
        val changes = mutableListOf<EventFeatureChangeTypeV1>()
        val subscription = api.subscribe { changes += it.type }
        try {
            assertTrue(api.queryEvents().events.isEmpty())
            val nativeEditor = when (
                val opened = api.openNewEvent(EventKindV1.ACTIVITY, zone.id)
            ) {
                is EventEditorOpenResultV1.Opened -> opened.editor
                else -> error("Expected Native editor to open, got $opened")
            }
            val nativeForm = nativeEditor.form.copy(
                title = "Created through Native adapter",
                startText = "2026-10-26 09:00",
                endText = "2026-10-26 10:00"
            )
            val nativeReceipt = api.saveNewEvent(
                nativeEditor.handle, nativeForm, "native-create-01"
            )
            assertEquals(EventMutationStatusV1.CREATED, nativeReceipt.status)
            assertEquals(1L, api.featureRevision)

            val service = WebSkinEditService(
                deck = core.draftReview,
                coreFeatureApi = core
            )
            val opened = service.execute(
                WebEditCommand.CreateDeck("web-create-01", TemporalKind.ACTIVITY),
                newEventZone = zone
            )
            assertEquals("created", opened.getString("status"))
            val deck = service.snapshot().getJSONObject("deck")
            val clientDraftId = deck.getLong("currentId")
            val draftVersion = deck.getLong("version")
            val patched = service.execute(
                WebEditCommand.PatchDeck(
                    "web-patch-01", clientDraftId, draftVersion, "title", "Staged through Web adapter"
                )
            )
            assertEquals("edited", patched.getString("status"))

            val flushed = service.execute(WebEditCommand.FlushDeck("web-flush-01"))
            assertEquals("flushed", flushed.getString("status"))
            assertEquals(2L, api.featureRevision)
            assertEquals(
                listOf(
                    EventFeatureChangeTypeV1.FORMAL_CREATED,
                    EventFeatureChangeTypeV1.DRAFTS_CHANGED
                ),
                changes
            )
            assertEquals("Created through Native adapter", api.queryEvents().events.single().title)
            assertEquals(
                "Staged through Web adapter",
                store.pendingDrafts().single().form.title
            )
            assertTrue(api.queryEvents().events.size == 1)
        } finally {
            subscription.close()
        }
    }
}
