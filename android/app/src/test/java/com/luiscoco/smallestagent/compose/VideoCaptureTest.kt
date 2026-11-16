package com.luiscoco.smallestagent.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import org.json.JSONObject

/** Production-tool renders of the real composables, not device screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun shot(name: String, first: String? = null, after: String? = null) {
        compose.waitForIdle()
        val top = first?.let { compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top.toInt() - 22 } ?: 0
        val bottom = after?.let { compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top.toInt() - 42 }
        val snackbar = if (name == "error") compose.onAllNodesWithText("Controlled error fixture. Check backend configuration, then retry.", useUnmergedTree = true).fetchSemanticsNodes().last().boundsInRoot else null
        val output = File(System.getProperty("course.root"), "video/captures/$name.png")
        output.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val image = if (first != null && bottom != null) Bitmap.createBitmap(bitmap, 16, top.coerceAtLeast(0), bitmap.width - 32, (bottom - top).coerceAtMost(bitmap.height - top)) else bitmap
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (snackbar != null && snackbar.top > bitmap.height / 2) {
                val x = (snackbar.left.toInt() - 16).coerceAtLeast(0)
                val y = (snackbar.top.toInt() - 16).coerceAtLeast(0)
                val w = (snackbar.width.toInt() + 32).coerceAtMost(bitmap.width - x)
                val h = (snackbar.height.toInt() + 32).coerceAtMost(bitmap.height - y)
                File(output.parentFile, "snackbar.png").outputStream().use { Bitmap.createBitmap(bitmap, x, y, w, h).compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
    @Test fun renderActualComposeTeachingStates() {
        var state by mutableStateOf(LabState(prompt = "Hello world!", health = "Controlled fixture · no provider call"))
        compose.setContent { CourseApp(state, CourseActions(section = { state = state.copy(section = it) })) }
        compose.onNodeWithTag("lesson-list").performScrollToIndex(1)
        shot("prompt", "01 / INPUT", "02 / OUTPUT")
        val answer = AgentResult("# Fixture answer\nIllustrative answer from a controlled state fixture.\n```kotlin\nprintln(\"Hello world!\")\n```", 1, 1250, null, false)
        compose.runOnIdle { state = state.copy(result = answer, status = "success", messages = listOf(
            TimelineMessage(AgentMessage("SystemMessage", "Controlled fixture: initialization", JSONObject().put("subtype", "init")), 0.0),
            TimelineMessage(AgentMessage("AssistantMessage", "Controlled fixture: assistant message", JSONObject().put("text", "Hello world!")), 0.4),
            TimelineMessage(AgentMessage("ResultMessage", "Controlled fixture: final result", JSONObject().put("result", answer.result).put("num_turns", 1).put("duration_ms", 1250).put("total_cost_usd", JSONObject.NULL).put("is_error", false)), 1.2))) }
        compose.onNodeWithTag("lesson-list").performScrollToIndex(2)
        shot("answer", "02 / OUTPUT", "LIVE / SDK MESSAGES")
        compose.onNodeWithTag("lesson-list").performScrollToIndex(3)
        shot("timeline", "LIVE / SDK MESSAGES", "The same agent, with a native application around it.")
        compose.runOnIdle { state = state.copy(result = AgentResult("The agent could not complete this prompt. Check the server configuration.", 1, 1250, null, true), status = "error", error = "The agent could not complete this prompt. Check the server configuration.") }
        compose.onNodeWithTag("lesson-list").performScrollToIndex(2)
        shot("result-error", "02 / OUTPUT", "LIVE / SDK MESSAGES")
        compose.runOnIdle { state = state.copy(result = null, error = "Controlled error fixture. Check backend configuration, then retry.", notice = "Controlled error fixture. Check backend configuration, then retry.") }
        shot("error", "02 / OUTPUT", "LIVE / SDK MESSAGES")
        compose.runOnIdle { state = state.copy(notice = "") }
        compose.onNodeWithTag("tab-run").performScrollTo().performClick()
        compose.onNodeWithTag("lesson-list").performScrollToIndex(3)
        shot("settings", "2. Configure your public API URL", "3. Build and install Android")
    }
}
