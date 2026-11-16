package com.luiscoco.smallestagent.compose

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AgentApiTest {
    private val result = """{"result":"**Hello!**","num_turns":1,"duration_ms":42,"total_cost_usd":0.001,"is_error":false}"""
    private fun frame(type: String, data: String) = "event: $type\ndata: $data\n\n"
    @Test fun queryPostsJsonAndPreservesMetrics() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(result))
            val answer = AgentApi(server.url("/").toString()).query("Hello")
            assertEquals("**Hello!**", answer.result); assertEquals(1, answer.numTurns); assertFalse(answer.isError)
            val request = server.takeRequest(); assertEquals("/api/agent/query", request.path)
            assertEquals("Hello", JSONObject(request.body.readUtf8()).getString("prompt"))
        }
    }
    @Test fun streamRequiresFinalAndDoneAndRetainsErrorFlag() = runBlocking {
        MockWebServer().use { server ->
            val error = result.replace("\"is_error\":false", "\"is_error\":true")
            val message = """{"type":"ResultMessage","summary":"Final result","data":$error}"""
            server.enqueue(MockResponse().setBody(frame("message", message) + frame("done", "{}")))
            val seen = mutableListOf<AgentMessage>()
            val answer = AgentApi(server.url("/").toString()).stream("Hi") { seen += it }
            assertTrue(answer.isError); assertEquals("ResultMessage", seen.single().type)
            server.enqueue(MockResponse().setBody(frame("done", "{}")))
            val missing = runCatching { AgentApi(server.url("/").toString()).stream("Hi") {} }.exceptionOrNull()
            assertTrue(missing?.message?.contains("before the agent finished") == true)
        }
    }
    @Test fun preservesSafeHttpAndSseErrorDetails() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"detail":"Configure the server key"}"""))
            assertEquals("Configure the server key", runCatching { AgentApi(server.url("/").toString()).query("Hi") }.exceptionOrNull()?.message)
            server.enqueue(MockResponse().setBody(frame("error", """{"detail":"Try again"}""")))
            assertEquals("Try again", runCatching { AgentApi(server.url("/").toString()).stream("Hi") {} }.exceptionOrNull()?.message)
        }
    }
    @Test fun readsSplitUtf8CrLfCommentsAndMultilineData() {
        val bytes = ": heartbeat\r\n\r\nevent: message\r\ndata: {\r\ndata: \"text\":\"héllo 世界\"}\r\n\r\nevent: done\ndata: {}\n\n".toByteArray()
        var at = 0
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long { if (at == bytes.size) return -1; sink.writeByte(bytes[at++].toInt()); return 1 }
            override fun timeout() = Timeout.NONE
            override fun close() {}
        }.buffer()
        val events = mutableListOf<Pair<String, String>>()
        SseReader.read(source) { event, data -> events += event to data }
        assertEquals("héllo 世界", JSONObject(events[0].second).getString("text")); assertEquals("done", events[1].first)
    }
    @Test fun rejectsIncompleteFramesAndUnsafeOrInvalidUrls() {
        assertThrows(IllegalArgumentException::class.java) { SseReader.read(Buffer().writeUtf8("data: {}")) { _, _ -> } }
        assertThrows(IllegalArgumentException::class.java) { AgentApi.validateUrl("javascript:alert(1)", true) }
        assertThrows(IllegalArgumentException::class.java) { AgentApi.validateUrl("http://example.com", false) }
        assertThrows(IllegalArgumentException::class.java) { AgentApi.validateUrl("https://secret@example.com", true) }
        assertEquals("https://example.com", AgentApi.validateUrl(" https://example.com/ ", false))
    }
    @Test fun cancellationClosesAnOutstandingCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val api = AgentApi(server.url("/").toString())
            val job = launch { api.stream("Hi") {} }
            delay(100); job.cancelAndJoin(); assertTrue(job.isCancelled)
        }
    }
    @Test fun rejectsMalformedPublicResultFields() {
        assertThrows(IllegalArgumentException::class.java) { AgentResult.fromJson(JSONObject(result.replace("\"num_turns\":1", "\"num_turns\":\"one\""))) }
    }
}
