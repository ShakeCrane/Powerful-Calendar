package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiImageConsentReview
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiOcrTextReview
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
class NativeAiInputReviewComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun imageConsentRequiresExplicitChoiceAndBusyStateBlocksUpload() {
        var sentOriginal = false
        var choseLocalOcr = false
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                NativeAiImageConsentReview(
                    busy = true,
                    onSendOriginal = { sentOriginal = true },
                    onLocalOcr = { choseLocalOcr = true },
                    onDismiss = { dismissed = true },
                    showDialog = { dialog ->
                        SkinAlertDialog(
                            onDismissRequest = dialog.onDismissRequest,
                            title = dialog.title,
                            text = dialog.text,
                            confirmButton = dialog.confirmButton,
                            dismissButton = dialog.dismissButton
                        )
                    }
                )
            }
        }

        ui.onNodeWithText("图片识别方式").assertIsDisplayed()
        ui.onNodeWithText("发送原图给 AI").assertIsNotEnabled()
        assertFalse(sentOriginal)
        assertFalse(choseLocalOcr)

        ui.onNodeWithText("仅本地 OCR").performClick()
        assertTrue(choseLocalOcr)
        assertFalse(sentOriginal)
        assertFalse(dismissed)
    }

    @Test
    fun ocrReviewSubmitsProvidedTextOnlyOnExplicitAction() {
        var submitted: String? = null
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                NativeAiOcrTextReview(
                    text = "周五下午三点",
                    onTextChange = {},
                    onSubmit = { submitted = it },
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

        ui.onNodeWithText("核对 OCR 识别原文").assertIsDisplayed()
        ui.onNodeWithText("周五下午三点").assertIsDisplayed()
        assertEquals(null, submitted)
        assertFalse(dismissed)

        ui.onNodeWithText("发送给 AI 理解").performClick()
        assertEquals("周五下午三点", submitted)
        assertFalse(dismissed)
    }
}
