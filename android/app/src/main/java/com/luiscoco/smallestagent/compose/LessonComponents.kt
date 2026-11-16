package com.luiscoco.smallestagent.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.caverock.androidsvg.SVG
import org.json.JSONArray

data class WorkflowStep(val number: Int, val title: String, val source: String, val language: String, val text: String, val code: String, val explanation: String)
fun loadSteps(context: Context): List<WorkflowStep> {
    val json = JSONArray(context.assets.open("workflow.json").bufferedReader().use { it.readText() })
    return (0 until json.length()).map { n -> json.getJSONObject(n).let { WorkflowStep(it.getInt("number"), it.getString("title"), it.getString("source"), it.getString("language"), it.getString("text"), it.getString("code"), it.getString("explanation")) } }
}

object CodeColors {
    private val token = Regex("""(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|//[^\n]*|#[^\n]*|\b(?:fun|class|val|var|if|else|when|return|try|catch|finally|suspend|private|public|import|package|const|let|async|await|function|export|new|throw|for|while|true|false|null|interface|type|def|yield|from|None|with)\b|\b\d+(?:\.\d+)?\b)""")
    fun highlight(code: String, language: String): AnnotatedString {
        if (language !in setOf("kotlin", "typescript", "javascript", "json", "powershell", "python", "xml")) return AnnotatedString(code)
        return buildAnnotatedString {
            append(code)
            token.findAll(code).forEach { match ->
                val value = match.value
                val color = when {
                    value.startsWith("//") || value.startsWith("#") -> Color(0xFFB0BED3)
                    value.startsWith('"') || value.startsWith('\'') -> Color(0xFFA8E6B5)
                    value.first().isDigit() -> Color(0xFFFFCB89)
                    else -> Color(0xFFC4A7FF)
                }
                addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
            }
        }
    }
}
@Composable fun CodeBlock(code: String, language: String = "kotlin", label: String = language) {
    val context = LocalContext.current
    var copied by remember(code) { mutableStateOf(false) }
    Surface(color = Color(0xFF14243C), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = Color(0xFFC5D4ED), fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, code)); copied = true
                }) { Text(if (copied) "Copied" else "Copy", color = Color(0xFF97D9FF)) }
            }
            SelectionContainer { Text(remember(code, language) { CodeColors.highlight(code, language) }, fontFamily = FontFamily.Monospace, fontSize = 18.sp, lineHeight = 29.sp, color = Color(0xFFE7EDFA), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp)) }
        }
    }
}

/** Model content becomes Compose text and spans; no HTML or WebView execution. */
@Composable fun MarkdownAnswer(text: String) {
    val uri = LocalUriHandler.current
    // Preserve fence blocks while separating plain paragraphs.
    val blocks = remember(text) {
        val matches = Regex("""```([^\n]*)\n([\s\S]*?)```""").findAll(text).toList()
        val result = mutableListOf<Triple<Boolean, String, String>>()
        var at = 0
        matches.forEach { match -> if (match.range.first > at) result += Triple(false, "", text.substring(at, match.range.first)); result += Triple(true, match.groupValues[1].trim(), match.groupValues[2]); at = match.range.last + 1 }
        if (at < text.length) result += Triple(false, "", text.substring(at))
        result
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { (code, language, body) ->
            if (code) CodeBlock(body, language.ifEmpty { "text" }) else body.lines().filter { it.isNotBlank() }.forEach { line ->
                val heading = Regex("^#{1,6} ").containsMatchIn(line)
                val cleaned = line.replace(Regex("^#{1,6} "), "")
                val link = Regex("""^\[([^]]+)]\((https?://[^\s)]+)\)$""").matchEntire(cleaned)
                if (link != null) TextButton(onClick = { runCatching { uri.openUri(link.groupValues[2]) } }) { Text(link.groupValues[1], fontSize = 21.sp) }
                else SelectionContainer {
                    Text(buildAnnotatedString {
                        val pieces = Regex("""\*\*([^*]+)\*\*""").findAll(cleaned).toList(); var at = 0
                        pieces.forEach { match -> append(cleaned.substring(at, match.range.first)); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }; at = match.range.last + 1 }; append(cleaned.substring(at))
                    }, style = if (heading) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

class WorkflowView(context: Context) : View(context) {
    private val svg = SVG.getFromAsset(context.assets, "workflow.svg")
    init { contentDescription = "UML workflow: five lifelines and sixteen interactions between student, Kotlin Compose client, Node API, Agent SDK and Claude."; setLayerType(LAYER_TYPE_SOFTWARE, null) }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); svg.renderToCanvas(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat())) }
}
@Composable fun WorkflowDiagram() {
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0xFFF8FBFF))) {
        AndroidView(factory = { WorkflowView(it) }, modifier = Modifier.width(1440.dp).height(1660.dp))
    }
}
