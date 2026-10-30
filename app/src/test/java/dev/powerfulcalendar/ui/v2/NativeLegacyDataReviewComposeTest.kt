package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.ui.v2.nativeui.NativeLegacyDataReview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NativeLegacyDataReviewComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun migrationReviewExplainsReadOnlySourceAndWaitsForHostAction() {
        var confirmed = false
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                NativeLegacyDataReview(
                    kind = NativeLegacyDataReviewKind.MIGRATE_DATABASE,
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

        ui.onNodeWithText("迁移旧版事件？").assertIsDisplayed()
        ui.onNodeWithText("只读打开", substring = true).assertIsDisplayed()
        ui.onNodeWithText("不会被删除或修改", substring = true).assertIsDisplayed()
        assertFalse(confirmed)
        ui.onNodeWithText("开始迁移").performClick()
        assertTrue(confirmed)
        assertFalse(dismissed)
    }

    @Test
    fun flatBackupReviewWarnsThatAuthenticityCannotBeVerified() {
        var confirmed = false
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                NativeLegacyDataReview(
                    kind = NativeLegacyDataReviewKind.RESTORE_FLAT_BACKUP,
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

        ui.onNodeWithText("恢复早期格式备份？").assertIsDisplayed()
        ui.onNodeWithText("没有 SHA-256 或 lineageId", substring = true).assertIsDisplayed()
        ui.onNodeWithText("AI 授权不会恢复", substring = true).assertIsDisplayed()
        assertFalse(confirmed)
        assertFalse(dismissed)

        ui.onNodeWithText("仍要恢复").performClick()
        assertTrue(confirmed)
        assertFalse(dismissed)
    }
}
