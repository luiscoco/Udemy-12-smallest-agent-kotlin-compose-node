package com.luiscoco.smallestagent.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

class MainActivity : ComponentActivity() {
    private val model: LabViewModel by viewModels()
    private val saveMarkdown = registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) runCatching { requireNotNull(contentResolver.openOutputStream(uri)).use { it.write(model.state.value.result?.result.orEmpty().toByteArray()) } }.fold({ model.notice("Markdown saved.") }, { model.notice("The document could not be saved.") })
    }
    private val saveSvg = registerForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) { uri ->
        if (uri != null) runCatching { requireNotNull(contentResolver.openOutputStream(uri)).use { out -> assets.open("workflow.svg").use { it.copyTo(out) } } }.fold({ model.notice("SVG saved.") }, { model.notice("The diagram could not be saved.") })
    }
    private val savePuml = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching { requireNotNull(contentResolver.openOutputStream(uri)).use { out -> assets.open("workflow.puml").use { it.copyTo(out) } } }.fold({ model.notice("PlantUML saved.") }, { model.notice("The diagram could not be saved.") })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !state.dark
                    isAppearanceLightNavigationBars = !state.dark
                }
            }
            CourseApp(state, CourseActions(
                section = model::section, prompt = model::prompt, mode = model::mode,
                run = model::run, cancel = model::cancel, theme = model::toggleTheme,
                saveUrl = model::saveUrl, refresh = model::refreshHealth, notice = model::notice,
                copy = { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Agent answer", state.result?.result.orEmpty())); model.notice("Answer copied.") },
                save = { saveMarkdown.launch("agent-answer.md") },
                share = { shareAnswer(state.result?.result.orEmpty()) },
                svg = { saveSvg.launch("workflow.svg") }, puml = { savePuml.launch("workflow.puml") }
            ))
        }
    }
    private fun shareAnswer(text: String) {
        runCatching {
            val directory = File(cacheDir, "exports").apply { mkdirs() }
            val file = File(directory, "agent-answer.md").apply { writeText(text) }
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/markdown"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            startActivity(Intent.createChooser(intent, "Share agent answer"))
        }.onFailure { model.notice("Sharing is unavailable. Use Save Markdown or Copy.") }
    }
}
