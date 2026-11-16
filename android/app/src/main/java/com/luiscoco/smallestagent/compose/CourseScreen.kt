@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.luiscoco.smallestagent.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CourseActions(
    val section: (String) -> Unit = {}, val prompt: (String) -> Unit = {}, val mode: (String) -> Unit = {},
    val run: () -> Unit = {}, val cancel: () -> Unit = {}, val theme: () -> Unit = {},
    val saveUrl: (String) -> Unit = {}, val refresh: () -> Unit = {}, val notice: (String) -> Unit = {},
    val copy: () -> Unit = {}, val save: () -> Unit = {}, val share: () -> Unit = {}, val svg: () -> Unit = {}, val puml: () -> Unit = {}
)
private val tabs = listOf("lab" to "Agent lab", "learn" to "What you will learn", "run" to "How to run", "architecture" to "Architecture & workflow", "walkthrough" to "Code walkthrough")
private val typography = Typography(
    bodyLarge = TextStyle(fontSize = 21.sp, lineHeight = 34.sp), bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 28.sp),
    bodySmall = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    labelSmall = TextStyle(fontSize = 16.sp), labelMedium = TextStyle(fontSize = 16.sp), titleSmall = TextStyle(fontSize = 18.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), headlineMedium = TextStyle(fontSize = 29.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold), displaySmall = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold)
)
@Composable fun CourseApp(state: LabState, actions: CourseActions) {
    val colors = if (state.dark) darkColorScheme(primary = Color(0xFF9CC8FF), background = Color(0xFF0B1729), surface = Color(0xFF10223A), onBackground = Color(0xFFEDF3FF), onSurface = Color(0xFFEDF3FF))
        else lightColorScheme(primary = Color(0xFF1266D4), background = Color(0xFFF5F8FE), surface = Color.White, onBackground = Color(0xFF172E50), onSurface = Color(0xFF172E50))
    MaterialTheme(colorScheme = colors, typography = typography) {
        val context = LocalContext.current
        val steps = remember(context) { loadSteps(context) }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(state.notice) { if (state.notice.isNotEmpty()) { snack.showSnackbar(state.notice); actions.notice("") } }
        Scaffold(containerColor = colors.background, snackbarHost = { SnackbarHost(snack) }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("◈ COCO / AGENT ACADEMY", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Text("LESSON 0 · NATIVE ANDROID", style = MaterialTheme.typography.bodyMedium) }
                    TextButton(onClick = actions.theme) { Text(if (state.dark) "Light mode" else "Dark mode") }
                }
                ScrollableTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == state.section }.coerceAtLeast(0), edgePadding = 8.dp, containerColor = colors.background) {
                    tabs.forEach { (key, label) -> Tab(selected = state.section == key, onClick = { actions.section(key) }, modifier = Modifier.testTag("tab-$key"), text = { Text(label, fontSize = 17.sp) }) }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    key(state.section) {
                        LazyColumn(Modifier.fillMaxSize().widthIn(max = 1100.dp).imePadding().testTag("lesson-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            item("hero") { Hero() }
                            when (state.section) {
                                "lab" -> labItems(state, actions)
                                "learn" -> learnItems()
                                "run" -> runItems(state, actions)
                                "architecture" -> {
                                    item { Heading("UML architecture & workflow") }
                                    item { LessonCard(0) { Text("Five lifelines, sixteen interactions. The SDK owns the model transport. Scroll horizontally to inspect the whole diagram."); WorkflowDiagram(); FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(actions.svg) { Text("Save UML SVG") }; OutlinedButton(actions.puml) { Text("Save PlantUML") } } } }
                                    items(steps, key = { "step-${it.number}" }) { step -> StepCard(step) }
                                }
                                "walkthrough" -> {
                                    item { Heading("Code walkthrough"); Text("Five actual source excerpts connect native input to the server SDK and a safe answer.") }
                                    items(listOf(1, 2, 5, 8, 12).map { steps[it - 1] }, key = { "code-${it.number}" }) { StepCard(it) }
                                }
                            }
                            item { Text("THE SMALLEST AGENT · Kotlin + Compose + Node.js", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp)) }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun Hero() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("BUILD IT. RUN IT. UNDERSTAND IT.", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("The smallest agent.\nNative on Android.", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Text("One prompt, one agent, one clear answer. Learn the Claude Agent SDK with Kotlin, Jetpack Compose and a Node.js TypeScript server.")
        Image(painterResource(R.drawable.astronaut), "Course astronaut", Modifier.size(140.dp).align(Alignment.CenterHorizontally))
    }
}
@Composable fun Heading(text: String) { Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
@Composable private fun LessonCard(tone: Int, content: @Composable ColumnScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .2f
    val light = listOf(0xFFEAF3FF, 0xFFE9F7EF, 0xFFF3EDFF, 0xFFFFF1E7)
    val night = listOf(0xFF172F50, 0xFF14382F, 0xFF302640, 0xFF3C2D27)
    Card(colors = CardDefaults.cardColors(containerColor = Color((if (dark) night else light)[tone % 4])), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
@Composable private fun StepCard(step: WorkflowStep) { LessonCard((step.number - 1) % 4) { Heading("${step.number}. ${step.title}"); Text(step.text); CodeBlock(step.code, step.language, step.source); Text(step.explanation, style = MaterialTheme.typography.bodyMedium) } }
private fun LazyListScope.labItems(state: LabState, actions: CourseActions) {
    item("input") { LessonCard(0) {
        Text("01 / INPUT", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Heading("Give your agent a mission")
        Text("Start small. Ask a question and follow the SDK messages back to the answer.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value = state.prompt, onValueChange = actions.prompt, label = { Text("Your prompt") }, enabled = state.status != "loading", minLines = 5, modifier = Modifier.fillMaxWidth().testTag("prompt").onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp && event.key == Key.Enter && event.isCtrlPressed) { actions.run(); true } else false
        }, textStyle = MaterialTheme.typography.bodyLarge)
        Text("${state.prompt.trim().length} / 4000 characters · ${state.health}", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Say hello to the world in one sentence.", "Explain an AI agent simply.", "Show a tiny Kotlin example.").forEach { example -> OutlinedButton(onClick = { actions.prompt(example) }, enabled = state.status != "loading") { Text(example) } } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = state.mode == "stream", onClick = { actions.mode("stream") }, enabled = state.status != "loading", label = { Text("Stream", fontSize = 18.sp) })
            FilterChip(selected = state.mode == "simple", onClick = { actions.mode("simple") }, enabled = state.status != "loading", label = { Text("Simple", fontSize = 18.sp) })
            Button(onClick = actions.run, enabled = state.status != "loading" && state.prompt.trim().length in 1..4000) { Text(if (state.status == "loading") "Running…" else "Run agent ↗") }
            if (state.status == "loading") OutlinedButton(actions.cancel) { Text("Cancel") }
        }
        Text("${state.status} · ${"%.1f".format(state.elapsed)}s", style = MaterialTheme.typography.bodyMedium)
        Text("Submitting a prompt may use API credits. Your key stays on the server.", style = MaterialTheme.typography.bodyMedium)
    } }
    item("answer") { LessonCard(1) {
        Text("02 / OUTPUT", style = MaterialTheme.typography.bodyMedium)
        Heading("Your agent’s answer")
        if (state.error.isNotEmpty()) Text(state.error, color = MaterialTheme.colorScheme.error)
        val result = state.result
        if (result != null) {
            MarkdownAnswer(result.result)
            Text("${result.numTurns} turn(s) · ${result.durationMs} ms · ${result.cost?.let { "Estimated $%.5f".format(it) } ?: "Cost unavailable"} · ${if (result.isError) "error" else "success"}", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(actions.copy) { Text("Copy answer") }; OutlinedButton(actions.save) { Text("Save Markdown") }; OutlinedButton(actions.share) { Text("Share answer") } }
        } else Text(if (state.status == "loading") "Your agent is working. Watch the SDK timeline." else "Your answer will appear here after you run the agent.", style = MaterialTheme.typography.bodyMedium)
    } }
    item("timeline-title") { LessonCard(2) { Text("LIVE / SDK MESSAGES", style = MaterialTheme.typography.bodyMedium); Heading("Follow the conversation"); Text("Real SDK events, projected safely. Expand a row to inspect colored JSON.", style = MaterialTheme.typography.bodyMedium); if (state.messages.isEmpty()) Text("Waiting for your first mission…", style = MaterialTheme.typography.bodyMedium) } }
    items(state.messages.indices.toList(), key = { "event-$it" }) { index ->
        val row = state.messages[index]
        var expanded by remember { mutableStateOf(false) }
        LessonCard(2) { TextButton(onClick = { expanded = !expanded }) { Text("${"%.1f".format(row.elapsed)}s · ${row.message.type} ${if (expanded) "−" else "+"}") }; Text(row.message.summary, style = MaterialTheme.typography.bodyMedium); if (expanded) CodeBlock(row.message.data.toString(2), "json", "Safe SDK event") }
    }
    if (state.messages.size >= 500) item { Text("Timeline display capped at 500 rows. Final results continue to be processed.", style = MaterialTheme.typography.bodyMedium) }
    item { LessonCard(3) { Heading("The same agent, with a native application around it."); Text("Prompt → Compose → typed HTTP client → Node API → Claude Agent SDK → Claude."); OutlinedButton(onClick = { actions.section("architecture") }) { Text("Explore the architecture") } } }
}
private fun LazyListScope.learnItems() {
    item { Heading("What you will learn") }
    val topics = listOf(
        "01 · A native interface" to "Kotlin and Jetpack Compose render Android controls directly. Material 3 cards, text fields and tabs make the lesson readable without an embedded web application.",
        "02 · The smallest agent" to "The Node server calls query({ prompt, options }) from the TypeScript Claude Agent SDK, then iterates messages until the SDK supplies a final result.",
        "03 · Private configuration" to "Only Node reads ANTHROPIC_API_KEY from backend/.env. Android stores a public API URL and theme preference; it never receives the Claude key.",
        "04 · Stream or simple" to "Simple returns one JSON result. Stream reads real SDK lifecycle, assistant and final projections over SSE. These are SDK messages rather than token deltas.",
        "05 · Constrain the lesson" to "Tools, settings, skills, MCP, plugins and persisted sessions are disabled. Input bounds, cancellation, deadlines and safe errors protect the teaching flow.",
        "06 · Read completion correctly" to "Result metrics include turns, duration and estimated USD cost. is_error stays an error even when a done event confirms transport completion."
    )
    items(topics.indices.toList()) { n -> LessonCard(n) { Heading(topics[n].first); Text(topics[n].second) } }
}
private fun LazyListScope.runItems(state: LabState, actions: CourseActions) {
    item { Heading("Run it on Windows") }
    item { LessonCard(0) { Heading("1. Start the Node server"); Text("Use Node.js 24 LTS. Begin in Lesson 0, then stay in this new folder. No Python, .NET or earlier project is required."); CodeBlock("""
        cd .\12-smallest-agent-kotlin-compose-node
        npm install
        npm run setup
        if (!(Test-Path backend\.env)) { Copy-Item backend\.env.example backend\.env }
        notepad backend\.env
        # Set your key and save, then:
        npm run dev
    """.trimIndent(), "powershell", "Windows PowerShell"); Text("API: http://127.0.0.1:8700/api/health. Stop the server with Ctrl+C. Health reports key presence, not authenticated readiness.", style = MaterialTheme.typography.bodyMedium) } }
    item { LessonCard(1) { Heading("2. Configure your public API URL"); var url by remember(state.apiUrl) { mutableStateOf(state.apiUrl) }; OutlinedTextField(url, { url = it }, label = { Text("API base URL") }, modifier = Modifier.fillMaxWidth()); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { actions.saveUrl(url) }) { Text("Save API URL") }; OutlinedButton(actions.refresh) { Text("Check connection") } }; Text(state.health, style = MaterialTheme.typography.bodyMedium); Text("Android emulator: http://10.0.2.2:8700. For USB use adb reverse tcp:8700 tcp:8700 and set http://127.0.0.1:8700. A phone’s localhost points to the phone. For LAN set API_HOST=0.0.0.0 privately and use the PC’s address; allow the firewall port. Release builds require HTTPS.") } }
    item { LessonCard(2) { Heading("3. Build and install Android"); Text("Open the android folder in Android Studio, select an API 26+ device or emulator, then Run. The Windows helper discovers an installed Android Studio/Visual Studio JDK and SDK, or uses JAVA_HOME and ANDROID_HOME."); CodeBlock("""
        # Second terminal, application root
        npm run android:build
        npm run android:test
        npm run android:lint
        # With one authorized device/emulator attached:
        npm run android:install
    """.trimIndent(), "powershell"); Text("The debug APK appears in android/app/build/outputs/apk/debug. Debug builds allow local HTTP; release manifests block it. Native signing and Play Store submission are separate actions.", style = MaterialTheme.typography.bodyMedium) } }
    item { LessonCard(3) { Heading("4. Private configuration and checks"); CodeBlock("ANTHROPIC_API_KEY=sk-ant-your-own-key\nCLAUDE_MODEL=\nAGENT_MAX_TURNS=1\nAGENT_SYSTEM_PROMPT=You are a concise teaching assistant.\nAPI_HOST=127.0.0.1\nAPI_PORT=8700\nALLOWED_ORIGINS=", "powershell", "backend/.env · edit on the server"); Text("Leave CLAUDE_MODEL empty to use the SDK default. Turns accept 1–10. Process environment overrides this backend’s file; restart Node after edits. Native clients do not need browser Origin. CORS is not authentication. Add authentication and cost/rate limits before public use."); CodeBlock("npm run typecheck\nnpm test\nnpm run build", "powershell") } }
}
