package dev.powerfulcalendar.ui.v2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebSkinFailureRecoveryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeRecoveryShowsRetainedFormAndOffersCheckpointRetry() {
        var retryCount = 0
        val snapshot = WebSkinEditRecoverySnapshot(
            description = "新建提醒",
            form = DraftForm(
                kind = TemporalKind.ALARM,
                title = "恢复中的标题",
                startText = "2026-10-28T09:00"
            ),
            error = "injected storage failure"
        )
        compose.setContent {
            PowerCalTheme {
                WebSkinFailureRecoveryDialog(
                    recovery = snapshot,
                    busy = false,
                    onRetry = { retryCount++ }
                )
            }
        }

        compose.onNodeWithText("编辑暂存失败").assertIsDisplayed()
        compose.onNodeWithText("恢复中的标题").assertIsDisplayed()
        compose.onNodeWithText("重试安全暂存").performClick()
        compose.runOnIdle { assertEquals(1, retryCount) }
    }
}
