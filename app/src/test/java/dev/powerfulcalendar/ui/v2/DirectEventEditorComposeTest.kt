package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.*
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
    private lateinit var session: EditSession

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
        session = EditSession.open(store, store.get(card.eventSeq)!!)
    }

    @After fun tearDown() {
        session.close()
        val name = store.databaseName
        store.close(); context.deleteDatabase(name)
    }

    private fun render(onDelete: () -> Unit = {}) {
        val form = mutableStateOf(session.form)
        val baseline = mutableStateOf(session.baselineForm)
        val error = mutableStateOf<String?>(null)
        val editorRevision = mutableIntStateOf(0)
        ui.setContent { PowerCalTheme {
            DirectEventEditorContent(
                session, form.value, error.value, session.meaningfulDiff,
                editorRevision = editorRevision.intValue,
                onChange = { form.value = it; session.edit(it) },
                onScopeChange = { selected ->
                    session.selectRecurrenceScope(selected)
                    form.value = session.form
                    baseline.value = session.baselineForm
                },
                onSave = {
                    val outcome = session.save()
                    if (outcome is PowerStore.DirectEditOutcome.Saved) editorRevision.intValue++
                    form.value = session.form
                    baseline.value = session.baselineForm; error.value = session.lastError
                },
                onExit = {},
                onDiscardChanges = {
                    session.discardEditingChanges(); form.value = session.form; error.value = null
                },
                onDelete = onDelete
            )
        } }
    }

    private fun titleField() = ui.onNode(hasSetTextAction() and hasText("Original"))

    @Test fun typingRevertDiscardAndSaveRefreshObservableBaselineWithoutClosing() {
        render()
        titleField().performTextReplacement("Changed")
        ui.onNodeWithText("Changed").assertExists()
        ui.onNodeWithText("放弃本次修改").assertIsEnabled()

        ui.onNode(hasSetTextAction() and hasText("Changed")).performTextReplacement("Original")
        ui.runOnIdle {
            assertEquals("Original", session.form.title)
            assertFalse("reverting the business field must restore the baseline", session.meaningfulDiff)
        }
        ui.onNodeWithText("放弃本次修改").assertIsNotEnabled()
        titleField().performTextReplacement("Discard me")
        ui.onNodeWithText("放弃本次修改").performClick()
        titleField().assertExists()

        titleField().performTextReplacement("Saved in place")
        ui.onNodeWithText("保存修改").performClick()
        ui.onNodeWithText("Saved in place").assertExists()
        ui.onNodeWithText("放弃本次修改").assertIsNotEnabled()
        assertEquals("Saved in place", store.get(session.eventSeq)!!.title)
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
        assertFalse(store.get(session.eventSeq) == null)
    }

    @Test fun directEditUsesSameKindConversionAndReminderFieldsAsNewDraftCard() {
        render()
        // The same typed form and picker controls are shared by both editor modes.
        ui.onNodeWithText("活动 ▾").performClick()
        ui.onNodeWithText("闹钟").performClick()
        assertEquals(TemporalKind.ALARM, session.form.kind)
        ui.onNodeWithText("闹钟 ▾").assertExists()

        ui.onNodeWithText("重复").performScrollTo()
        ui.onNodeWithText("每天").performScrollTo()
        ui.onNodeWithText("每天").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertEquals(RecurrenceFreq.DAILY, session.form.recurrence.freq)

        ui.onNodeWithText("保存修改").performClick()
        assertEquals(TemporalKind.ALARM, store.get(session.eventSeq)!!.kind)
        assertEquals(RecurrenceFreq.DAILY, store.get(session.eventSeq)!!.recurrence.freq)
    }

    @Test fun recurringEditorRequiresScopeAndSingleScopeEditsOnlySelectedTitleAndTime() {
        session.close()
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
        session = EditSession.open(store, formal, selectedKey)
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
