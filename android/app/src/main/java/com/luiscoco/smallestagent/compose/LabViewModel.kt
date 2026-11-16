package com.luiscoco.smallestagent.compose

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class LabState(
    val prompt: String = "Say hello to the world in one sentence.", val mode: String = "stream",
    val section: String = "lab", val status: String = "idle", val error: String = "", val notice: String = "",
    val result: AgentResult? = null, val messages: List<TimelineMessage> = emptyList(), val elapsed: Double = 0.0,
    val apiUrl: String = "http://10.0.2.2:8700", val dark: Boolean = false, val health: String = "Checking API…"
)
class LabViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("smallest-agent", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(LabState(apiUrl = prefs.getString("api_url", "http://10.0.2.2:8700")!!, dark = prefs.getBoolean("dark", false)))
    val state = mutable.asStateFlow()
    private var locked = false
    private var runJob: Job? = null
    private var activeApi: AgentApi? = null
    init { refreshHealth() }
    fun prompt(value: String) { mutable.update { it.copy(prompt = value) } }
    fun mode(value: String) { if (!locked && value in listOf("stream", "simple")) mutable.update { it.copy(mode = value) } }
    fun section(value: String) { mutable.update { it.copy(section = value) } }
    fun toggleTheme() { val dark = !mutable.value.dark; prefs.edit().putBoolean("dark", dark).apply(); mutable.update { it.copy(dark = dark) } }
    fun notice(value: String) { mutable.update { it.copy(notice = value) } }
    fun saveUrl(value: String) {
        try {
            require(!locked) { "Wait for the active request before changing the API URL." }
            val url = AgentApi.validateUrl(value, BuildConfig.DEBUG)
            prefs.edit().putString("api_url", url).apply()
            mutable.update { it.copy(apiUrl = url, notice = "API URL saved.") }
            refreshHealth()
        } catch (error: IllegalArgumentException) { notice(error.message ?: "The API URL is invalid.") }
    }
    fun refreshHealth() { viewModelScope.launch {
        val url = mutable.value.apiUrl
        val health = runCatching { AgentApi(url).health() }.fold(
            onSuccess = { if (it) "API ready · key configured" else "API ready · configure backend key" },
            onFailure = { "API offline · check URL and server" }
        )
        mutable.update { if (it.apiUrl == url) it.copy(health = health) else it }
    } }
    fun run() {
        val trimmed = mutable.value.prompt.trim()
        if (locked || trimmed.isEmpty() || trimmed.length > 4000) return
        locked = true
        val mode = mutable.value.mode
        mutable.update { it.copy(status = "loading", error = "", result = null, messages = emptyList(), elapsed = 0.0) }
        runJob = viewModelScope.launch {
            val started = System.nanoTime()
            val clock = launch { while (true) { mutable.update { it.copy(elapsed = (System.nanoTime() - started) / 1e9) }; delay(100) } }
            try {
                val api = AgentApi(mutable.value.apiUrl); activeApi = api
                val result = withTimeout(180_000) {
                    if (mode == "simple") api.query(trimmed) else api.stream(trimmed) { message ->
                        mutable.update { old ->
                            val rows = if (old.messages.size < 500) old.messages + TimelineMessage(message, (System.nanoTime() - started) / 1e9) else old.messages
                            old.copy(messages = rows, result = if (message.type == "ResultMessage") AgentResult.fromJson(message.data) else old.result)
                        }
                    }
                }
                mutable.update { it.copy(result = result, status = if (result.isError) "error" else "success", error = if (result.isError) result.result else "", notice = if (result.isError) "The agent reported an error." else "Answer received.") }
            } catch (error: Exception) {
                val detail = if (error is CancellationException) "The request was canceled or timed out." else error.message ?: "The request could not finish."
                mutable.update { it.copy(status = "error", error = detail, notice = detail) }
            } finally {
                clock.cancel(); activeApi?.cancel(); activeApi = null; locked = false
                mutable.update { it.copy(elapsed = (System.nanoTime() - started) / 1e9) }
            }
        }
    }
    fun cancel() { runJob?.cancel(); activeApi?.cancel() }
    override fun onCleared() { cancel(); super.onCleared() }
}
