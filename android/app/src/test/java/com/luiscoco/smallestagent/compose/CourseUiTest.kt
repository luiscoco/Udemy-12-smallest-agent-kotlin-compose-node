package com.luiscoco.smallestagent.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CourseUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun allLessonTabsWorkAndRetainNativePromptAndResult() {
        var state by mutableStateOf(LabState(prompt = "Keep my native prompt", result = AgentResult("Fixture retained answer", 1, 42, null, false)))
        compose.setContent { CourseApp(state, CourseActions(section = { state = state.copy(section = it) }, theme = { state = state.copy(dark = !state.dark) })) }
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        listOf("learn" to "What you will learn", "run" to "Run it on Windows", "architecture" to "UML architecture & workflow", "walkthrough" to "Code walkthrough").forEach { (tab, title) ->
            compose.onNodeWithTag("tab-$tab").performScrollTo().performClick()
            compose.onNodeWithTag("lesson-list").performScrollToNode(hasText(title) and heading)
            compose.onNode(hasText(title) and heading).assertExists()
            compose.onNodeWithTag("tab-$tab").assertIsSelected()
        }
        compose.onNodeWithTag("tab-lab").performScrollTo().performClick()
        compose.onNodeWithTag("lesson-list").performScrollToNode(hasTestTag("prompt"))
        compose.onNodeWithTag("prompt").assertTextContains("Keep my native prompt")
        compose.onNodeWithTag("lesson-list").performScrollToNode(hasText("Fixture retained answer"))
        compose.onNodeWithText("Fixture retained answer").assertExists()
        compose.onNodeWithText("Dark mode").performClick(); compose.onNodeWithText("Light mode").assertExists()
    }
    @Test fun architectureHasTheSixteenthActualSourceExplanation() {
        compose.setContent { CourseApp(LabState(section = "architecture"), CourseActions()) }
        compose.onNodeWithTag("lesson-list").performScrollToNode(hasText("16. Key: SSE error / input: 422"))
        compose.onNodeWithText("16. Key: SSE error / input: 422").assertExists()
    }
    @Test fun codeHighlightingPreservesContentAndUnknownLanguagesRemainPlainText() {
        val code = "val hello = \"world\"\n// comment\nreturn 42"
        val highlighted = CodeColors.highlight(code, "kotlin")
        assertEquals(code, highlighted.text); assertTrue(highlighted.spanStyles.size >= 4)
        assertTrue(CodeColors.highlight("<script>alert(1)</script>", "unknown").spanStyles.isEmpty())
    }
}
