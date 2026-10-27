package dev.powerfulcalendar.ai

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiPartialProposalLoopbackTest {
    @Test fun emptyStructuredResponseStillReturnsIncompleteReviewCard() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-10-27T01:30:00Z")
        val evidence = AiChatMessage(
            role = "user",
            content = "\u5E2E\u6211\u521B\u5EFA\u4E00\u4E2A\u95F9\u949F",
            createdAtMillis = now.toEpochMilli(),
            zoneId = zone.id
        )
        val task = checkNotNull(AiConversationTaskSnapshot.from(
            listOf(evidence), now.toEpochMilli(), zone.id
        ))
        val error = AtomicReference<Throwable?>()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 10_000
            val thread = Thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val input = socket.getInputStream().buffered()
                        fun readLine(): String {
                            val bytes = java.io.ByteArrayOutputStream()
                            while (true) {
                                val b = input.read()
                                require(b >= 0 && bytes.size() < 8192)
                                if (b == 10) break
                                if (b != 13) bytes.write(b)
                            }
                            return bytes.toString(Charsets.US_ASCII.name())
                        }
                        assertTrue(readLine().startsWith("POST /v1/chat/completions "))
                        var size = 0
                        while (true) {
                            val header = readLine()
                            if (header.isEmpty()) break
                            if (header.startsWith("Content-Length:", ignoreCase = true))
                                size = header.substringAfter(':').trim().toInt()
                        }
                        require(size in 1..100_000)
                        val bytes = ByteArray(size)
                        var read = 0
                        while (read < size) {
                            val count = input.read(bytes, read, size - read)
                            require(count > 0)
                            read += count
                        }
                        val envelope = JSONObject(String(bytes, Charsets.UTF_8))
                        val messages = envelope.getJSONArray("messages")
                        assertTrue(messages.toString().contains("events"))
                        val reply = JSONObject().put("choices", JSONArray().put(
                            JSONObject().put("message",
                                JSONObject().put("content", """{"events":[]}"""))
                                .put("finish_reason", "stop")
                        )).toString().toByteArray(Charsets.UTF_8)
                        val header = ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: " + reply.size + "\r\nConnection: close\r\n\r\n")
                            .toByteArray(Charsets.US_ASCII)
                        socket.getOutputStream().apply {
                            write(header)
                            write(reply)
                            flush()
                        }
                    }
                } catch (t: Throwable) {
                    error.set(t)
                }
            }
            thread.isDaemon = true
            thread.start()
            val client = AiClient(
                settings = AiSettings(
                    endpoint = "http://127.0.0.1:" + server.localPort + "/v1",
                    model = "test-free-local-stub",
                    enabled = true
                ),
                zone = zone,
                clock = Clock.fixed(now, zone)
            )
            val result = runBlocking { client.interpret(task, emptyList()) }
            thread.join(10_000)
            error.get()?.let { throw AssertionError("Loopback server failure", it) }
            assertEquals(1, result.proposals.size)
            assertFalse(result.proposals.single().form.isPublishable())
            assertTrue(result.warnings.isNotEmpty())
            assertEquals(task.taskId, result.taskSnapshot?.taskId)
        }
    }
}
