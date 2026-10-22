package dev.powerfulcalendar.ai

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiPresentationContractTest {
    @Test fun noHardTruncationOfVisibleConversation() {
        val reply = "这是完整解释。" + "内容".repeat(750)
        val rendered = AiChatActionParser.parse(reply)
        assertEquals(reply, rendered.text)
        assertNull(rendered.proposalSource)
    }

    @Test fun malformedInternalMarkerNeverLeaksIntoChat() {
        val rendered = AiChatActionParser.parse("日程建议[[POWERCAL_DRAFT]]{bad")
        assertEquals("日程建议", rendered.text)
    }

    @Test fun rawToolJsonIsNotPresentedAsAssistantProse() {
        val rendered = AiChatActionParser.parse("""{"events":[{"title":"private"}]}""")
        assertFalse(rendered.text.contains("private"))
        assertTrue(rendered.text.contains("尚未"))
    }

    @Test fun consentBindsProviderAndCanBeRevoked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AiFileFallbackConsent.revoke(context)
        val first = AiSettings("https://first.example/v1", "model-a", enabled = true)
        val other = first.copy(endpoint = "https://other.example/v1")
        assertFalse(AiFileFallbackConsent.allowed(context, first))
        AiFileFallbackConsent.grant(context, first)
        assertTrue(AiFileFallbackConsent.allowed(context, first))
        assertFalse(AiFileFallbackConsent.allowed(context, other))
        AiFileFallbackConsent.revoke(context)
        assertFalse(AiFileFallbackConsent.allowed(context, first))
    }
}
