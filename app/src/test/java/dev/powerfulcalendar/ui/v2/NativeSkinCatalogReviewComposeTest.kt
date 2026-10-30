package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.ui.v2.nativeui.NativeSkinCatalogReview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NativeSkinCatalogReviewComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun activationReviewShowsRecoveryInstructionsAndWaitsForHostConfirmation() {
        var confirmed = false
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                NativeSkinCatalogReview(
                    kind = NativeSkinCatalogReviewKind.ACTIVATE,
                    skinName = "Example skin",
                    recoveryGesture = "在时间轴连续点击两处位置可回退",
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

        ui.onNodeWithText("切换皮肤前请确认").assertIsDisplayed()
        ui.onNodeWithText("Example skin", substring = true).assertIsDisplayed()
        ui.onNodeWithText("在时间轴连续点击两处位置可回退", substring = true).assertIsDisplayed()
        assertFalse(confirmed)
        assertFalse(dismissed)

        ui.onNodeWithText("确认切换").performClick()
        assertTrue(confirmed)
        assertFalse(dismissed)
    }

    @Test
    fun deletionReviewExplainsSkinOnlyEffectAndRoutesConfirmationToHost() {
        var confirmed = false
        ui.setContent {
            PowerCalTheme {
                NativeSkinCatalogReview(
                    kind = NativeSkinCatalogReviewKind.DELETE,
                    skinName = "Old pack",
                    onConfirm = { confirmed = true },
                    onDismiss = {},
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

        ui.onNodeWithText("删除皮肤？").assertIsDisplayed()
        ui.onNodeWithText("此操作仅删除皮肤素材，日历事件不会改变。", substring = true)
            .assertIsDisplayed()
        assertFalse(confirmed)
        ui.onNodeWithText("确认删除").performClick()
        assertTrue(confirmed)
    }
}
