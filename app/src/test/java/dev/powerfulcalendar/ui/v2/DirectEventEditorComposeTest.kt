package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.*
import dev.powerfulcalendar.core.api.v1.EventEditorOpenResultV1
import dev.powerfulcalendar.core.api.v1.EventEditorSnapshotV1
import dev.powerfulcalendar.core.api.v1.EventFeatureApiV1
import dev.powerfulcalendar.core.api.v1.EventMutationStatusV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
import dev.powerfulcalendar.core.api.v1.toCoreDraftForm
import dev.powerfulcalendar.core.api.v1.toEventFormV1
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h900dp-xhdpi")
class DirectEventEditorComposeTest {
    @get:Rule val ui = createComposeRule()
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var eventApi: EventFeatureApiV1
    private lateinit var editor: EventEditorSnapshotV1

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "editor-ui-" + UUID.randomUUID() + ".db")
        val now = System.currentTimeMillis() + 3_600_000L
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.ACTIVITY, title = "Original",
            startText = DraftForm.formatInstant(now, ZoneId.systemDefault()),
            endText = DraftForm.formatInstant(now + 3_600_000L, ZoneId.systemDefault())
        ))
        assertTrue(store.commit(card.draftId, actor = "test") is PowerStore.CommitOutcome.Committed)
        eventApi = PowerCalCoreFeatureApiV1(store).events
        editor = openFormalEditor(card.eventSeq, store.get(card.eventSeq)!!.revision)
    }

    @After fun tearDown() {
        eventApi.closeFormalEvent(editor.handle)
        val name = store.databaseName
        store.close(); context.deleteDatabase(name)
    }

    private fun render(onDelete: () -> Unit = {}) {
        val editorState = mutableStateOf(editor)
        val form = mutableStateOf(editor.form.toCoreDraftForm())
        val baseline = mutableStateOf(editor.baselineForm.toCoreDraftForm())
        val error = mutableStateOf<String?>(null)
        val editorRevision = mutableIntStateOf(0)
        fun refresh() {
            eventApi.editorSnapshot(editorState.value.handle)?.let { updated ->
                editorState.value = updated
                editor = updated
            }
        }
        ui.setContent { PowerCalTheme {
            DirectEventEditorContent(
                editorState.value, form.value, error.value, editorState.value.meaningfulChanges,
                editorRevision = editorRevision.intValue,
                onChange = { next ->
                    form.value = next
                    eventApi.updateFormalEvent(editorState.value.handle, next.toEventFormV1())
                    refresh()
                },
                onScopeChange = { selected ->
                    eventApi.selectFormalRecurrenceScope(editorState.value.handle, selected)
                    refresh()
                    form.value = editorState.value.form.toCoreDraftForm()
                    baseline.value = editorState.value.baselineForm.toCoreDraftForm()
                },
                onSave = {
                    val outcome = eventApi.saveFormalEvent(
                        editorState.value.handle, form.value.toEventFormV1(), UUID.randomUUID().toString()
                    )
                    if (outcome.status in setOf(
                            EventMutationStatusV1.UPDATED, EventMutationStatusV1.NO_CHANGE
                        )) editorRevision.intValue++
                    refresh()
                    form.value = editorState.value.form.toCoreDraftForm()
                    baseline.value = editorState.value.baselineForm.toCoreDraftForm()
                    error.value = outcome.message ?: editorState.value.lastError
                },
                onExit = {},
                onDiscardChanges = {
                    eventApi.discardFormalChanges(editorState.value.handle)
                    refresh()
                    form.value = editorState.value.form.toCoreDraftForm()
                    baseline.value = editorState.value.baselineForm.toCoreDraftForm()
                    error.value = null
                },
                onDelete = onDelete
            )
        } }
    }

    private fun currentEditor(): EventEditorSnapshotV1 =
        requireNotNull(eventApi.editorSnapshot(editor.handle)).also { editor = it }

    private fun openFormalEditor(
        eventSeq: Long,
        revision: Long,
        occurrenceKey: String? = null
    ): EventEditorSnapshotV1 = when (
        val opened = eventApi.openFormalEvent(eventSeq, revision, occurrenceKey)
    ) {
        is EventEditorOpenResultV1.Opened -> opened.editor
        else -> error("Expected event editor to open, got $opened")
    }

    private fun titleField() = ui.onNode(hasSetTextAction() and hasText("Original"))

    @Test fun typingRevertDiscardAndSaveRefreshObservableBaselineWithoutClosing() {
        render()
        titleField().performTextReplacement("Changed")
        ui.onNodeWithText("Changed").assertExists()
        ui.onNodeWithText("放弃本次修改").assertIsEnabled()

        ui.onNode(hasSetTextAction() and hasText("Changed")).performTextReplacement("Original")
        ui.runOnIdle {
            assertEquals("Original", currentEditor().form.title)
            assertFalse(
                "reverting the business field must restore the baseline",
                currentEditor().meaningfulChanges
            )
        }
        ui.onNodeWithText("放弃本次修改").assertIsNotEnabled()
        titleField().performTextReplacement("Discard me")
        ui.onNodeWithText("放弃本次修改").performClick()
        titleField().assertExists()

        titleField().performTextReplacement("Saved in place")
        ui.onNodeWithText("保存修改").performClick()
        ui.onNodeWithText("Saved in place").assertExists()
        ui.onNodeWithText("放弃本次修改").assertIsNotEnabled()
        assertEquals("Saved in place", store.get(requireNotNull(editor.eventSeq))!!.title)
    }

    @Test fun failedSaveKeepsTypedTextAndDeleteRequiresExplicitConfirmation() {
        var deletes = 0
        render { deletes++ }
        titleField().performTextReplacement("")
        ui.onNodeWithText("保存修改").performClick()
        val editable = ui.onAllNodes(hasSetTextAction())[0].fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals("", editable)

        ui.onNodeWithText("删除事件").performClick()
        ui.onNodeWithText("确认删除事件？").assertIsDisplayed()
        assertEquals(0, deletes)
        ui.onNodeWithText("取消").performClick()
        assertEquals(0, deletes)
        ui.onNodeWithText("删除事件").performClick()
        ui.onNodeWithText("确认删除").performClick()
        assertEquals(1, deletes)
        assertFalse(store.get(requireNotNull(editor.eventSeq)) == null)
    }

    @Test fun directEditUsesSameKindConversionAndReminderFieldsAsNewDraftCard() {
        render()
        // The same typed form and picker controls are shared by both editor modes.
        ui.onNodeWithText("活动 ▾").performClick()
        ui.onNodeWithText("闹钟").performClick()
        assertEquals(TemporalKind.ALARM, TemporalKind.valueOf(currentEditor().form.kind.name))
        ui.onNodeWithText("闹钟 ▾").assertExists()

        ui.onNodeWithText("重复").performScrollTo()
        ui.onNodeWithText("每天").performScrollTo()
        ui.onNodeWithText("每天").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertEquals(
            RecurrenceFreq.DAILY,
            RecurrenceFreq.valueOf(currentEditor().form.recurrence.frequency.name)
        )

        ui.onNodeWithText("保存修改").performClick()
        assertEquals(TemporalKind.ALARM, store.get(requireNotNull(editor.eventSeq))!!.kind)
        assertEquals(RecurrenceFreq.DAILY, store.get(requireNotNull(editor.eventSeq))!!.recurrence.freq)
    }

    @Test fun recurringEditorRequiresScopeAndSingleScopeEditsOnlySelectedTitleAndTime() {
        eventApi.closeFormalEvent(editor.handle)
        val shanghai = ZoneId.of("Asia/Shanghai")
        val firstMillis = java.time.LocalDateTime.of(2026, 10, 20, 7, 30)
            .atZone(shanghai).toInstant().toEpochMilli()
        val draft = store.newDraft(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "每日活动",
            startText = DraftForm.formatInstant(firstMillis, shanghai),
            endText = DraftForm.formatInstant(firstMillis + 3_600_000L, shanghai),
            zoneId = shanghai.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        ))
        assertTrue(store.commit(draft.draftId, actor = "test") is PowerStore.CommitOutcome.Committed)
        val formal = store.get(draft.eventSeq)!!
        val selectedKey = "2026-10-21T07:30"
        editor = openFormalEditor(formal.eventSeq, formal.revision, selectedKey)
        render()

        ui.onNodeWithText("保存修改").assertIsNotEnabled()
        ui.onNodeWithText("仅本次").performClick()
        ui.onNodeWithText("提醒方式与时间").assertDoesNotExist()
        ui.onNode(hasSetTextAction() and hasText("每日活动"))
            .performTextReplacement("只改周三")
        ui.onNodeWithText("保存修改").performClick()

        val saved = store.get(formal.eventSeq)!!
        assertEquals("每日活动", saved.title)
        assertEquals("只改周三", saved.recurrence.overrides[selectedKey]?.title)
        assertEquals(null, saved.recurrence.overrides["2026-10-20T07:30"])
        assertEquals(null, saved.recurrence.overrides["2026-10-22T07:30"])
    }

    @Test fun monthOccurrenceOpenPassesTheOriginalSlotKeyToTheEditorHost() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val firstMillis = java.time.LocalDateTime.of(2026, 10, 20, 7, 30)
            .atZone(shanghai).toInstant().toEpochMilli()
        val owner = TemporalRecord(
            eventSeq = 90,
            revision = 1,
            kind = TemporalKind.ALARM,
            title = "每日闹钟",
            startMillis = firstMillis,
            zoneId = shanghai.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        )
        var opened: TemporalRecord? = null
        var key: String? = null
        PresentationSkinRegistry.useMonth(null)
        ui.setContent {
            PowerCalTheme {
                MonthScreen(
                    records = listOf(owner),
                    initialDate = java.time.LocalDate.of(2026, 10, 21),
                    zone = shanghai,
                    aiLocked = false,
                    onBack = {},
                    onDate = {},
                    onOpen = { record, occurrenceKey -> opened = record; key = occurrenceKey },
                    onCreate = {}
                )
            }
        }
        ui.onNodeWithText("闹钟 · 每日闹钟").performClick()
        ui.runOnIdle {
            assertEquals(owner, opened)
            assertEquals("2026-10-21T07:30", key)
        }
    }

    @Test fun memoOccurrenceOpenPassesTheOriginalSlotKeyToTheEditorHost() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val firstMillis = java.time.LocalDateTime.of(2026, 10, 20, 7, 30)
            .atZone(shanghai).toInstant().toEpochMilli()
        val owner = TemporalRecord(
            eventSeq = 91,
            revision = 1,
            kind = TemporalKind.ALARM,
            title = "待完成重复事项",
            startMillis = firstMillis,
            trackingEnabled = true,
            zoneId = shanghai.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        )
        val entry = TodoEntry(owner, "2026-10-21T07:30", firstMillis, owner.title)
        var opened: TemporalRecord? = null
        var key: String? = null
        PresentationSkinRegistry.useMemo(null)
        ui.setContent {
            PowerCalTheme {
                MemoCenterScreen(
                    items = listOf(entry),
                    zone = shanghai,
                    busy = false,
                    onBack = {},
                    onCreateMemo = {},
                    onToggle = {},
                    onOpen = { record, occurrenceKey -> opened = record; key = occurrenceKey }
                )
            }
        }
        ui.onNodeWithText("待完成重复事项").performClick()
        ui.runOnIdle {
            assertEquals(owner, opened)
            assertEquals("2026-10-21T07:30", key)
        }
    }
}
