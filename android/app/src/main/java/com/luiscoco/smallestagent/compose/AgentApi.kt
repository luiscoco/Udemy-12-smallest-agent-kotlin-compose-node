package com.luiscoco.smallestagent.compose

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AgentResult(val result: String, val numTurns: Int, val durationMs: Long, val cost: Double?, val isError: Boolean) {
    companion object {
        fun fromJson(json: JSONObject): AgentResult {
            require(json.get("result") is String && json.get("is_error") is Boolean)
            require(json.get("num_turns") is Number && json.get("duration_ms") is Number)
            val cost = if (json.isNull("total_cost_usd")) null else {
                require(json.get("total_cost_usd") is Number)
                json.getDouble("total_cost_usd").also { require(it.isFinite() && it >= 0) }
            }
            return AgentResult(json.getString("result"), json.getInt("num_turns"), json.getLong("duration_ms"), cost, json.getBoolean("is_error"))
                .also { require(it.numTurns >= 0 && it.durationMs >= 0) }
        }
    }
}
data class AgentMessage(val type: String, val summary: String, val data: JSONObject) {
    companion object {
        fun fromJson(json: JSONObject): AgentMessage {
            val type = json.getString("type")
            require(type in setOf("SystemMessage", "AssistantMessage", "UserMessage", "ResultMessage", "OtherMessage"))
            val data = json.getJSONObject("data")
            if (type == "ResultMessage") AgentResult.fromJson(data)
            return AgentMessage(type, json.getString("summary"), data)
        }
    }
}
data class TimelineMessage(val message: AgentMessage, val elapsed: Double)

/** Okio buffers UTF-8 bytes across socket reads; readUtf8Line handles LF/CRLF. */
object SseReader {
    fun read(source: BufferedSource, onEvent: (String, String) -> Unit) {
        var event = "message"
        val data = mutableListOf<String>()
        var length = 0
        while (true) {
            val line = source.readUtf8Line() ?: break
            require(line.length <= 1_048_576) { "The API event is too large." }
            if (line.isEmpty()) {
                if (data.isNotEmpty()) onEvent(event, data.joinToString("\n"))
                event = "message"; data.clear(); length = 0
            } else if (!line.startsWith(":")) {
                val field = line.substringBefore(':')
                val value = if (':' in line) line.substringAfter(':').removePrefix(" ") else ""
                when (field) {
                    "event" -> event = value
                    "data" -> { length += value.length; require(length <= 1_048_576) { "The API event is too large." }; data += value }
                }
            }
        }
        require(data.isEmpty()) { "The connection ended inside an API event." }
    }
}

class AgentApi(baseUrl: String, private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS).build()) {
    val baseUrl = validateUrl(baseUrl, BuildConfig.DEBUG)
    @Volatile private var active: Call? = null
    fun cancel() { active?.cancel() }
    companion object {
        fun validateUrl(value: String, allowHttp: Boolean): String {
            val url = value.trim().trimEnd('/').toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Enter a valid HTTP(S) API URL.")
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "Use an API URL without credentials, query or fragment." }
            require(allowHttp || url.isHttps) { "Release builds require an HTTPS API URL." }
            return url.toString().trimEnd('/')
        }
    }
    private fun request(path: String, prompt: String? = null): Request = Request.Builder().url(baseUrl + path).apply {
        if (prompt != null) post(JSONObject().put("prompt", prompt).toString().toRequestBody("application/json".toMediaType()))
    }.build()
    private suspend fun <T> execute(request: Request, parse: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request); active = call
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (active === call) active = null
                if (continuation.isActive) continuation.resumeWithException(IOException("Could not connect to the API. Check its URL, server and network."))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val value = response.use {
                        if (!it.isSuccessful) {
                            val detail = runCatching { JSONObject(it.body?.string().orEmpty()).getString("detail") }.getOrNull()
                            throw ApiFailure(detail ?: "The API could not process the request (HTTP ${it.code}).")
                        }
                        parse(it)
                    }
                    if (continuation.isActive) continuation.resume(value)
                } catch (error: Exception) {
                    val detail = when (error) { is ApiFailure -> error.message.orEmpty(); else -> "The API response was invalid or the stream ended before completion." }
                    if (continuation.isActive) continuation.resumeWithException(IOException(detail))
                } finally { if (active === call) active = null }
            }
        })
    }
    suspend fun health(): Boolean = execute(request("/api/health")) { response ->
        val json = JSONObject(response.body?.string().orEmpty())
        require(json.getString("status") == "ok" && json.get("api_key_configured") is Boolean)
        json.getBoolean("api_key_configured")
    }
    suspend fun query(prompt: String): AgentResult = execute(request("/api/agent/query", prompt)) { response ->
        AgentResult.fromJson(JSONObject(response.body?.string().orEmpty()))
    }
    suspend fun stream(prompt: String, onMessage: (AgentMessage) -> Unit): AgentResult = execute(request("/api/agent/stream", prompt)) { response ->
        var completed = false
        var finalResult: AgentResult? = null
        val source = response.body?.source() ?: throw ApiFailure("The API returned an empty stream.")
        SseReader.read(source) { event, text ->
            val json = JSONObject(text)
            when (event) {
                "error" -> throw ApiFailure(json.optString("detail", "The agent failed."))
                "done" -> completed = true
                "message" -> {
                    val message = AgentMessage.fromJson(json)
                    if (message.type == "ResultMessage") finalResult = AgentResult.fromJson(message.data)
                    onMessage(message)
                }
            }
        }
        if (!completed || finalResult == null) throw ApiFailure("The connection ended before the agent finished.")
        finalResult!!
    }
    private class ApiFailure(message: String) : IOException(message)
}
