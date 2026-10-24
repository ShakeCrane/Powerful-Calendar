package dev.powerfulcalendar.ai

import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Each invocation verifies app-private chat history in a newly started app process. */
@RunWith(AndroidJUnit4::class)
class AiChatHistoryProcessRestartTest {
    @Test
    fun syntheticConversationSurvivesProcessAndSystemRestarts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val cycle = requireNotNull(args.getString(ARG_CYCLE)).toInt()
        require(cycle in 0..RESTART_COUNT)

        val context = instrumentation.targetContext
        val historyFile = File(context.filesDir, HISTORY_FILENAME)
        var verified = false
        try {
            if (cycle == 0) {
                assertTrue(
                    "Refusing to replace existing chat history",
                    !historyFile.exists()
                )
                assertTrue(AiChatHistoryStore.load(context).isEmpty())
                AiChatHistoryStore.save(context, SYNTHETIC_MESSAGES)
            }

            assertTrue("Chat history file must exist", historyFile.isFile)
            assertTrue("Chat history file must contain serialized data", historyFile.length() > 0L)
            assertEquals(SYNTHETIC_MESSAGES, AiChatHistoryStore.load(context))

            instrumentation.sendStatus(
                0,
                Bundle().apply {
                    putString("result", "PASS")
                    putInt("cycle", cycle)
                    putInt("process_pid", Process.myPid())
                    putInt("message_count", AiChatHistoryStore.load(context).size)
                    putString("history_file", HISTORY_FILENAME)
                }
            )
            verified = true
        } finally {
            // The test writes this file only after proving the fresh app sandbox was empty.
            // Preserve it on every failed cycle for diagnosis.
            if (cycle == RESTART_COUNT && verified) {
                assertTrue("Remove only the synthetic test conversation", historyFile.delete())
            }
        }
    }

    private companion object {
        const val ARG_CYCLE = "chatHistoryRestartCycle"
        const val RESTART_COUNT = 12
        const val HISTORY_FILENAME = "powercal-chat-history.json"
        val SYNTHETIC_MESSAGES = listOf(
            AiChatMessage("user", "PC-ISSUE-136 synthetic user message"),
            AiChatMessage("assistant", "PC-ISSUE-136 synthetic assistant reply", "synthetic")
        )
    }
}
