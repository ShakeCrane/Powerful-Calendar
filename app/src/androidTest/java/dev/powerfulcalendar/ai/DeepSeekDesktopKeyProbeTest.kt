package dev.powerfulcalendar.ai

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.TemporalKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** One opt-in live request using a temporary host-provided key file; never saves AI settings. */
@RunWith(AndroidJUnit4::class)
class DeepSeekDesktopKeyProbeTest {
    @Test
    fun officialFlashEndpointReturnsSyntheticActivityProposal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "Pass powercalDesktopDeepSeekProbe=true to make the single authorized request",
            InstrumentationRegistry.getArguments().getString("powercalDesktopDeepSeekProbe") == "true"
        )
        assumeTrue("This probe is scoped to Android API 35", android.os.Build.VERSION.SDK_INT == 35)

        val context = instrumentation.targetContext
        val keyFile = File(context.filesDir, KEY_FILE)
        try {
            val key = context.openFileInput(KEY_FILE).bufferedReader(Charsets.UTF_8)
                .use { it.readText().trim() }
            assertTrue("Temporary API key file is missing or malformed", key.length in 32..512)

            val settings = AiSettings(
                endpoint = ENDPOINT,
                model = MODEL,
                apiKey = key,
                enabled = true,
                shareContext = false
            )
            val zone = ZoneId.of("Asia/Shanghai")
            val startedAt = SystemClock.elapsedRealtime()
            val result = runBlocking {
                AiClient(settings, zone, connectTimeoutMs = 15_000, readTimeoutMs = 45_000)
                    .interpret(SYNTHETIC_INPUT, emptyList())
            }
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt

            val proposal = result.proposals.singleOrNull()
            assertTrue("Expected one proposal without parser warnings", result.warnings.isEmpty())
            assertTrue("Expected one synthetic Activity proposal", proposal != null)
            assertEquals(TemporalKind.ACTIVITY, proposal!!.form.kind)
            assertEquals(TITLE, proposal.form.title)
            assertTrue("Proposal fields must be publishable", proposal.form.isPublishable())

            val expectedStart = LocalDateTime.of(2026, 11, 2, 14, 0)
                .atZone(zone).toInstant().toEpochMilli()
            val expectedEnd = LocalDateTime.of(2026, 11, 2, 14, 30)
                .atZone(zone).toInstant().toEpochMilli()
            assertEquals(expectedStart, proposal.form.parseStart())
            assertEquals(expectedEnd, proposal.form.parseEnd())

            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("probe", "deepseek_desktop_key_structured_synthetic")
                putLong("elapsed_ms", elapsedMs)
                putInt("request_count", 1)
                putInt("proposal_count", result.proposals.size)
                putInt("warning_count", result.warnings.size)
                putString("model", MODEL)
            })
        } finally {
            keyFile.delete()
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.deepseek.com"
        const val MODEL = "deepseek-flash"
        const val KEY_FILE = "deepseek-live-key"
        const val TITLE = "桌面密钥合成验证会议"
        const val SYNTHETIC_INPUT =
            "仅生成一条可审查的结构化提案，不要创建、保存或发布日历数据。" +
                "请创建 ACTIVITY：标题“桌面密钥合成验证会议”，" +
                "日期 2026-11-02，14:00 至 14:30，时区 Asia/Shanghai。"
    }
}
