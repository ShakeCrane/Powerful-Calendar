package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.ui.v2.nativeui.NativeEventInspection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NativeEventInspectionComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun lockedEventInspectionShowsHostTextAndDismissesOnlyOnExplicitAction() {
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                NativeEventInspection(
                    model = NativeEventInspectionModel(
                        title = "预约复诊",
                        summary = "活动 · 2026-10-30 09:00",
                        notes = "带检查报告"
                    ),
                    onDismiss = { dismissed = true },
                    showDialog = { dialog ->
                        Column {
                            dialog.title()
                            dialog.text()
                            dialog.confirmButton()
                        }
                    }
                )
            }
        }

        ui.onNodeWithText("预约复诊").assertIsDisplayed()
        ui.onNodeWithText("活动 · 2026-10-30 09:00").assertIsDisplayed()
        ui.onNodeWithText("带检查报告").assertIsDisplayed()
        ui.onNodeWithText("AI 处理中，卡组只读。").assertIsDisplayed()
        assertEquals(false, dismissed)

        ui.onNodeWithText("关闭").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun eventDisplayProjectionContainsOnlyStrings() {
        val modelClass = NativeEventInspectionModel::class.java
        val displayFields = listOf("title", "summary", "notes").map(modelClass::getDeclaredField)
        assertEquals(3, displayFields.size)
        assertTrue(displayFields.all { it.type == String::class.java })
    }
}
