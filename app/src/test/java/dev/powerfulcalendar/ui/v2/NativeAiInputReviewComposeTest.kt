package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiImageConsentReview
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiOcrTextReview
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiFileReview
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

    @Test
    fun fileReviewEditsTextAndConsentBeforeHostSubmission() {
        val extractedText = mutableStateOf("含有可删除隐私内容")
        var consentEnabled = false
        var submitted: String? = null
        ui.setContent {
            PowerCalTheme {
                NativeAiFileReview(
                    filename = "notes.txt",
                    text = extractedText.value,
                    note = "已完整提取文本",
                    truncated = false,
                    icsPreflight = null,
                    rememberConsentAvailable = true,
                    rememberConsent = consentEnabled,
                    directIcsImport = false,
                    busy = false,
                    onTextChange = { extractedText.value = it },
                    onRememberConsentChange = { consentEnabled = it },
                    onSubmit = { submitted = it },
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

        ui.onNodeWithText("核对文件 · notes.txt").assertIsDisplayed()
        ui.onNodeWithText("已完整提取文本").assertIsDisplayed()
        ui.onNode(hasSetTextAction()).performTextReplacement("已删除隐私内容")
        ui.onNode(isToggleable()).performClick()
        assertEquals(true, consentEnabled)
        assertEquals(null, submitted)

        ui.onNodeWithText("交给 AI 分析并澄清").performClick()
        assertEquals("已删除隐私内容", submitted)
    }

    @Test
    fun unchangedValidIcsReviewUsesHostSelectedDeterministicImportAction() {
        var submitted: String? = null
        ui.setContent {
            PowerCalTheme {
                NativeAiFileReview(
                    filename = "calendar.ics",
                    text = "BEGIN:VCALENDAR",
                    note = "已完整提取文本",
                    truncated = false,
                    icsPreflight = "ICS 本地预检：单次 1 条、重复系列 0 个；需要澄清 0 项。",
                    rememberConsentAvailable = false,
                    rememberConsent = false,
                    directIcsImport = true,
                    busy = false,
                    onTextChange = {},
                    onRememberConsentChange = {},
                    onSubmit = { submitted = it },
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

        ui.onNodeWithText("ICS 本地预检：单次 1 条、重复系列 0 个；需要澄清 0 项。").assertIsDisplayed()
        ui.onNodeWithText("按 ICS 规则生成草稿").performClick()
        assertEquals("BEGIN:VCALENDAR", submitted)
    }
}
