package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.powerfulcalendar.ui.v2.nativeui.NativeTextDraftDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NativeTextDraftDialogComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun promptSubmitsTextOnlyThroughHostCallback() {
        var submitted: String? = null
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                NativeTextDraftDialog(
                    onDismiss = { dismissed = true },
                    onConfirm = { submitted = it },
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

        ui.onNodeWithText("用文字创建草稿").assertIsDisplayed()
        ui.onNodeWithText("生成草稿").assertIsNotEnabled()
        assertEquals(null, submitted)
        assertFalse(dismissed)

        ui.onNodeWithText("例如：明天下午 3 点开组会").performTextInput("周五下午开会")
        ui.onNodeWithText("生成草稿").performClick()
        assertEquals("周五下午开会", submitted)
        assertFalse(dismissed)
        assertTrue(submitted?.isNotBlank() == true)
    }

    @Test
    fun dismissDoesNotSubmit() {
        var submitted: String? = null
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                NativeTextDraftDialog(
                    onDismiss = { dismissed = true },
                    onConfirm = { submitted = it },
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

        ui.onNodeWithText("取消").performClick()
        assertTrue(dismissed)
        assertEquals(null, submitted)
    }
}
