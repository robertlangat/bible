package com.robertlangat.bibledaily

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 2 of the device test: runs in a NEW app process after CI force-stops the app, and checks
 * that the note, bookmark and reading position written in [CoreFlowsTest] survived the restart.
 */
@RunWith(AndroidJUnit4::class)
class RestartPersistenceTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun savedDataAndReadingPositionSurviveRestart() {
        rule.waitForBibleReady()

        // Home shows where the user left off.
        rule.waitUntil(20_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag("continue_reading") and hasContentDescription("Romans 8", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithTag("tab_saved").performClick()
        rule.onNodeWithTag("saved_tab_notes").performClick()
        rule.waitForText(CoreFlowsTest.NOTE)
        rule.onNodeWithText(CoreFlowsTest.NOTE, substring = true).assertIsDisplayed()

        rule.onNodeWithTag("saved_tab_bookmarks").performClick()
        rule.waitForText("Romans 8:28")
        rule.screenshot("10_after_restart_saved")

        // The Read tab resumes at the last position.
        rule.onNodeWithTag("tab_read").performClick()
        rule.waitForReaderTitle("Romans 8")
        rule.waitForTag("verse_28")
        rule.onNodeWithTag("verse_28")
            .assertIsDisplayed()
            .assert(hasContentDescription("Has note", substring = true))
            .assert(hasContentDescription("Bookmarked", substring = true))
        rule.onNodeWithTag("reader_title").assert(hasText("Romans 8"))
        rule.screenshot("11_after_restart_reader")
    }
}
