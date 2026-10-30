package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiStuckBatchReleaseReview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NativeAiStuckBatchReleaseReviewComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun releaseButtonRemainsDisabledUntilHostReportsSessionEligible() {
        var confirmed = false
        ui.setContent {
            PowerCalTheme {
                NativeAiStuckBatchReleaseReview(
                    canConfirm = false,
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

        ui.onNodeWithText("强制解除 AI 批次锁？").assertIsDisplayed()
        ui.onNodeWithText("当前执行结果数量可能无法确定", substring = true).assertIsDisplayed()
        ui.onNodeWithText("解除并作废旧授权").assertIsNotEnabled()
        ui.onNodeWithText("解除并作废旧授权").performClick()
        assertFalse(confirmed)
    }

    @Test
    fun eligibleHostConfirmationCallsOnlyExplicitReleaseAction() {
        var confirmed = false
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                NativeAiStuckBatchReleaseReview(
                    canConfirm = true,
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

        assertFalse(confirmed)
        ui.onNodeWithText("解除并作废旧授权").performClick()
        assertTrue(confirmed)
        assertFalse(dismissed)
    }
}
