package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.ui.v2.nativeui.NativeFormalWriteReview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NativeFormalWriteReviewComposeTest {
    @get:Rule val ui = createComposeRule()

    private val model = NativeFormalWriteReviewModel(
        title = "确认发布正式事件",
        targetSummary = "草稿 #42 · 新建",
        scopeSummary = "范围：整个系列",
        occurrenceSummary = "原始实例：2026-10-30T09:00",
        explanation = "只有在此宿主对话框确认后才会写入正式事件。",
        rows = listOf(NativeFormalWriteReviewRow("标题", "旧标题", "新标题")),
        confirmLabel = "确认",
        resolving = false
    )

    @Test
    fun reviewShowsHostProjectedSnapshotAndRoutesApproval() {
        var confirmed = false
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                NativeFormalWriteReview(
                    model = model,
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                    showDialog = { dialog ->
                        Column {
                            dialog.title()
                            dialog.text()
                            dialog.confirmButton()
                            dialog.dismissButton()
                        }
                    }
                )
            }
        }

        ui.onNodeWithText("确认发布正式事件").assertIsDisplayed()
        ui.onNodeWithText("草稿 #42 · 新建").assertIsDisplayed()
        ui.onNodeWithText("范围：整个系列").assertIsDisplayed()
        ui.onNodeWithText("原始实例：2026-10-30T09:00").assertIsDisplayed()
        ui.onNodeWithText("标题：旧标题 → 新标题").assertIsDisplayed()
        ui.onNodeWithText("只有在此宿主对话框确认后才会写入正式事件。").assertIsDisplayed()
        assertFalse(confirmed)
        assertFalse(dismissed)

        ui.onNodeWithText("确认").performClick()
        assertTrue(confirmed)
        assertFalse(dismissed)
    }

    @Test
    fun resolvingStateDisablesBothHostActions() {
        var confirmed = false
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                NativeFormalWriteReview(
                    model = model.copy(resolving = true),
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                    showDialog = { dialog ->
                        Column {
                            dialog.title()
                            dialog.text()
                            dialog.confirmButton()
                            dialog.dismissButton()
                        }
                    }
                )
            }
        }

        ui.onNodeWithText("确认").assertIsNotEnabled()
        ui.onNodeWithText("取消").assertIsNotEnabled()
        assertFalse(confirmed)
        assertFalse(dismissed)
    }
}
