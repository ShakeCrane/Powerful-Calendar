package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.ui.v2.nativeui.NativeCalendarImportPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NativeCalendarImportPromptComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun promptExplainsSnapshotAndConfirmInvokesOnlyImportAction() {
        var imported = false
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                NativeCalendarImportPrompt(
                    onImportOnce = { imported = true },
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

        ui.onNodeWithText("导入系统日历？").assertIsDisplayed()
        ui.onNodeWithText("一次性快照", substring = true).assertIsDisplayed()
        ui.onNodeWithText("以后不会监测或同步原日历", substring = true).assertIsDisplayed()
        ui.onNodeWithText("导入一次").performClick()
        assertTrue(imported)
        assertFalse(dismissed)
    }

    @Test
    fun deferAndSystemDismissBothUseHostDismissAction() {
        var dismissed = 0
        lateinit var renderedDialog: NativeCalendarImportPromptDialog
        ui.setContent {
            PowerCalTheme {
                NativeCalendarImportPrompt(
                    onImportOnce = {},
                    onDismiss = { dismissed++ },
                    showDialog = { dialog ->
                        renderedDialog = dialog
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

        ui.onNodeWithText("暂不导入").performClick()
        assertTrue(dismissed == 1)
        ui.runOnIdle { renderedDialog.onDismissRequest() }
        assertTrue(dismissed == 2)
    }
}
