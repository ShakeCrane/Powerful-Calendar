package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.ui.v2.nativeui.NativeDirectEventEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h900dp-xhdpi")
class NativeDirectEventEditorModuleTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun nativeEditorShellRoutesScopeTypeAndFormalActionsThroughHostCallbacks() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = java.time.Instant.parse("2026-10-21T04:00:00Z").toEpochMilli()
        val initialForm = DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Native 编辑器",
            startText = DraftForm.formatInstant(now + 3_600_000L, zone),
            endText = DraftForm.formatInstant(now + 7_200_000L, zone),
            zoneId = zone.id
        ).toNativeDeckForm(now)
        val selectedKinds = mutableListOf<NativeDeckKind>()
        val selectedScopes = mutableListOf<NativeDirectEditScope>()
        var saves = 0
        var discards = 0
        var exits = 0
        var deletes = 0

        ui.setContent {
            PowerCalTheme {
                var form by remember { mutableStateOf(initialForm) }
                var scope by remember { mutableStateOf<NativeDirectEditScope?>(null) }
                var hasChanges by remember { mutableStateOf(false) }
                val card = NativeDeckCard(
                    cardKey = "formal:77",
                    sequenceLabel = "000077",
                    operation = NativeDeckOperation.REPLACE,
                    operationLabel = "修改",
                    status = NativeDeckStatus.PENDING,
                    draftVersion = 0L,
                    form = form
                )
                val appearance = nativeDeckAppearance()
                val formContext = NativeDeckFormContext(
                    form = form,
                    card = card,
                    aiLocked = false,
                    appearance = appearance,
                    actions = NativeDeckFormActions(
                        onChange = { form = it },
                        onPickStartDate = {},
                        onPickStartTime = {},
                        onPickEndDate = {},
                        onPickEndTime = {},
                        onDeadlineDateToInstant = {}
                    )
                )
                NativeDirectEventEditor(
                    NativeDirectEventEditorContext(
                        editorKey = "editor-77",
                        editorRevision = 0,
                        form = formContext,
                        error = null,
                        hasChanges = hasChanges,
                        busy = false,
                        canChangeKind = true,
                        requiresRecurrenceScope = true,
                        selectedRecurrenceScope = scope,
                        recurrenceScopes = NativeDirectEditScope.entries.map {
                            NativeDirectEditScopeOption(it, selected = it == scope, enabled = true)
                        },
                        onChangeKind = {
                            selectedKinds += it
                            form = form.copy(kind = it)
                            hasChanges = true
                        },
                        onChangeRecurrenceScope = {
                            selectedScopes += it
                            scope = it
                        },
                        onSave = { saves++ },
                        onDiscardChanges = { discards++; hasChanges = false },
                        onExit = { exits++ },
                        onDelete = { deletes++ }
                    )
                )
            }
        }

        ui.onNodeWithText("编辑事件 000077").assertIsDisplayed()
        ui.onNodeWithText("保存修改").assertIsNotEnabled()
        ui.onNodeWithText("整个系列").performClick()
        ui.onNodeWithText("保存修改").assertIsEnabled()
        ui.onNodeWithText("活动 ▾").performClick()
        ui.onNodeWithText("截止").performClick()
        ui.onNodeWithText("截止 ▾").assertIsDisplayed()
        ui.onNodeWithText("放弃本次修改").performClick()
        ui.onNodeWithText("保存修改").performClick()
        ui.onNodeWithText("存档并退出").performClick()
        ui.onNodeWithText("删除事件").performClick()
        assertEquals(0, deletes)
        ui.onNodeWithText("确认删除").performClick()

        assertEquals(listOf(NativeDeckKind.DEADLINE), selectedKinds)
        assertEquals(listOf(NativeDirectEditScope.WHOLE_SERIES), selectedScopes)
        assertEquals(1, saves)
        assertEquals(1, discards)
        assertEquals(1, exits)
        assertEquals(1, deletes)
    }
}
