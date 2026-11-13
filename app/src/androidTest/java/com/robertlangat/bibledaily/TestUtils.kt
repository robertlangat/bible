package com.robertlangat.bibledaily

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

typealias ComposeRule = AndroidComposeTestRule<*, *>

private const val TIMEOUT = 20_000L

/** Waits for the bundled Bible import to finish (first launch) and the verse of the day to show. */
fun ComposeRule.waitForBibleReady() {
    waitUntil(120_000) { onAllNodes(hasTestTag("votd_text")).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeRule.waitForTag(tag: String, timeout: Long = TIMEOUT) {
    waitUntil(timeout) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeRule.waitForReaderTitle(title: String) {
    waitUntil(TIMEOUT) {
        onAllNodes(hasTestTag("reader_title") and hasText(title)).fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeRule.waitForText(text: String, substring: Boolean = true) {
    waitUntil(TIMEOUT) { onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeRule.search(query: String) {
    onNodeWithTag("tab_search").performClick()
    waitForTag("search_field")
    onNodeWithTag("search_field").performTextClearance()
    onNodeWithTag("search_field").performTextInput(query)
}

/** Searches for a reference and taps the reference result (labelled [label]), which opens the Read tab. */
fun ComposeRule.openReference(query: String, expectedTitle: String, label: String = query) {
    search(query)
    waitUntil(TIMEOUT) {
        onAllNodes(hasTestTag("ref_result") and hasText(label, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithTag("ref_result").performClick()
    waitForReaderTitle(expectedTitle)
}

/** Saves a screenshot to the app's external files dir; CI pulls these with adb. */
fun ComposeRule.screenshot(name: String) {
    waitForIdle()
    try {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } catch (e: Exception) {
        Log.w("BibleDailyTest", "Screenshot $name failed", e)
    }
}
