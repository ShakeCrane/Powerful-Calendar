package dev.powerfulcalendar.ai

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.TemporalKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Explicit live-provider probe. Run only on a disposable/synthetic test request with an existing
 * user-configured endpoint and credential. The model override is in-memory and never saved.
 */
@RunWith(AndroidJUnit4::class)
class DeepSeekFlashLiveProbeTest {
    @Test
    fun authorizedFlashReturnsUsableSyntheticActivityProposal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stored = AiSettingsStore.load(context)
        assertTrue("An enabled AI endpoint must already be configured", stored.configured)
        assertTrue("A user-configured credential must already exist", stored.apiKey.isNotBlank())
        assertTrue("Remote AI endpoint must use HTTPS", stored.endpoint.startsWith("https://"))

        val settings = stored.copy(
            model = MODEL_ID,
            enabled = true,
            shareContext = false
        )
        val zone = ZoneId.of("Asia/Shanghai")
        val started = SystemClock.elapsedRealtime()
        val result = runBlocking {
            AiClient(settings, zone).interpret(SYNTHETIC_INPUT, emptyList())
        }
        val elapsedMs = SystemClock.elapsedRealtime() - started
        val proposal = result.proposals.firstOrNull()

        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putString("probe", "deepseek_flash_structured_synthetic")
                putLong("elapsed_ms", elapsedMs)
                putInt("proposal_count", result.proposals.size)
                putInt("warning_count", result.warnings.size)
            }
        )

        assertNotNull("Expected one usable structured proposal", proposal)
        assertEquals(TemporalKind.ACTIVITY, proposal!!.form.kind)
        assertEquals("合成模型验证会议", proposal.form.title)
        assertTrue("Proposal must contain valid activity times", proposal.form.isPublishable())
        val start = LocalDateTime.of(2026, 11, 2, 14, 0).atZone(zone).toInstant().toEpochMilli()
        val end = LocalDateTime.of(2026, 11, 2, 14, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(start, proposal.form.parseStart())
        assertEquals(end, proposal.form.parseEnd())
    }

    private companion object {
        const val MODEL_ID = "deepseek/deepseek-v4.1-flash"
        const val SYNTHETIC_INPUT =
            "仅生成结构化提案，不要创建或发布任何真实数据。为合成验收创建一条活动：" +
                "标题“合成模型验证会议”，2026-11-02 14:00 至 14:30，时区 Asia/Shanghai。"
    }
}
