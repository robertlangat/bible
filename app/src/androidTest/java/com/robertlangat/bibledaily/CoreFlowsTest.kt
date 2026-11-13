package com.robertlangat.bibledaily

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.content.IntentCompat
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Phase 1 of the device test. CI runs it on a freshly installed app with the emulator in
 * airplane mode, then force-stops the app and runs [RestartPersistenceTest].
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CoreFlowsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun t1_firstLaunchWorksOfflineAndShowsVerseOfDay() {
        // The device is in airplane mode and the app has no INTERNET permission at all.
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
        assertEquals("Emulator must be in airplane mode for this test", 1, airplane)
        val permission = context.packageManager.checkPermission(android.Manifest.permission.INTERNET, context.packageName)
        assertEquals(PackageManager.PERMISSION_DENIED, permission)

        rule.waitForBibleReady()
        rule.onNodeWithTag("votd_text").assertIsDisplayed()
        rule.onNodeWithTag("votd_ref").assertIsDisplayed()
        val text = rule.onNodeWithTag("votd_text").fetchSemanticsNode().config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
            .joinToString("")
        assertTrue("Verse of the day text should not be empty", text.length > 10)
        rule.screenshot("01_home_first_launch_offline")
    }

    @Test
    fun t2_chapterNavigation() {
        rule.waitForBibleReady()
        rule.onNodeWithTag("tab_read").performClick()

        rule.onNodeWithTag("reader_title").performClick()
        rule.waitForTag("tab_old")
        rule.onNodeWithTag("tab_old").performClick()
        rule.onNodeWithTag("book_1").performClick()
        rule.waitForTag("chapter_grid")
        rule.onNodeWithTag("chapter_1").performClick()
        rule.waitForReaderTitle("Genesis 1")
        rule.waitForTag("verse_1")
        rule.onNodeWithTag("verse_1").assert(hasContentDescription("In the beginning, God created", substring = true))
        rule.screenshot("02_reader_genesis_1")

        rule.onNodeWithTag("next_chapter").performClick()
        rule.waitForReaderTitle("Genesis 2")
        rule.onNodeWithTag("verse_1").assert(hasContentDescription("Genesis 2:1", substring = true))

        rule.onNodeWithTag("prev_chapter").performClick()
        rule.waitForReaderTitle("Genesis 1")

        // Crossing a book boundary: Genesis 50 -> Exodus 1 -> back to Genesis 50.
        rule.onNodeWithTag("reader_title").performClick()
        rule.waitForTag("book_list")
        rule.onNodeWithTag("book_1").performClick()
        rule.waitForTag("chapter_grid")
        rule.onNodeWithTag("chapter_grid").performScrollToNode(hasTestTag("chapter_50"))
        rule.onNodeWithTag("chapter_50").performClick()
        rule.waitForReaderTitle("Genesis 50")
        rule.onNodeWithTag("next_chapter").performClick()
        rule.waitForReaderTitle("Exodus 1")
        rule.onNodeWithTag("prev_chapter").performClick()
        rule.waitForReaderTitle("Genesis 50")

        // New Testament via the picker.
        rule.onNodeWithTag("reader_title").performClick()
        rule.waitForTag("tab_new")
        rule.onNodeWithTag("tab_new").performClick()
        rule.onNodeWithTag("book_list").performScrollToNode(hasTestTag("book_66"))
        rule.onNodeWithTag("book_66").performClick()
        rule.waitForTag("chapter_grid")
        rule.onNodeWithTag("chapter_grid").performScrollToNode(hasTestTag("chapter_22"))
        rule.onNodeWithTag("chapter_22").performClick()
        rule.waitForReaderTitle("Revelation 22")
    }

    @Test
    fun t3_searchReferencesAndText() {
        rule.waitForBibleReady()

        // "John 3:16" opens John 3 scrolled to verse 16.
        rule.openReference("John 3:16", "John 3")
        rule.waitForTag("verse_16")
        rule.onNodeWithTag("verse_16")
            .assertIsDisplayed()
            .assert(hasContentDescription("John 3:16. For God so loved the world", substring = true))
        rule.screenshot("03_search_john_3_16_opened")

        // Abbreviation.
        rule.search("Jn 3:16")
        rule.waitUntil(20_000) {
            rule.onAllNodes(hasTestTag("ref_result") and hasText("John 3:16", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.screenshot("04_search_jn_3_16")

        // Whole chapter.
        rule.openReference("Psalm 23", "Psalms 23", label = "Psalms 23")
        rule.waitForTag("verse_1")
        rule.onNodeWithTag("verse_1").assert(hasContentDescription("Yahweh is my shepherd", substring = true))

        // Phrase search, then open a text result.
        rule.search("born anew")
        rule.waitForTag("result_43_3_3")
        rule.screenshot("05_search_phrase")
        rule.onNodeWithTag("result_43_3_3").performClick()
        rule.waitForReaderTitle("John 3")
        rule.onNodeWithTag("verse_3").assertIsDisplayed()

        // Word search returns results.
        rule.search("shepherd")
        rule.waitForTag("search_summary")
        rule.waitUntil(20_000) {
            rule.onAllNodes(hasTestTag("search_summary") and hasText("verses found", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun t4_shareVerseIncludesReferenceAndTranslation() {
        rule.waitForBibleReady()
        rule.openReference("John 3:16", "John 3")
        rule.waitForTag("verse_16")

        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_CHOOSER))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            rule.onNodeWithTag("verse_16").performClick()
            rule.waitForTag("action_share")
            rule.screenshot("06_verse_actions")
            rule.onNodeWithTag("action_share").performClick()
            rule.waitForIdle()

            val chooser = Intents.getIntents().lastOrNull { it.action == Intent.ACTION_CHOOSER }
            assertNotNull("A share chooser should have been launched", chooser)
            val send = IntentCompat.getParcelableExtra(chooser!!, Intent.EXTRA_INTENT, Intent::class.java)
            assertNotNull(send)
            assertEquals(Intent.ACTION_SEND, send!!.action)
            assertEquals("text/plain", send.type)
            val shared = send.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            assertTrue(shared, shared.startsWith("For God so loved the world"))
            assertTrue(shared, shared.contains("John 3:16"))
            assertTrue(shared, shared.contains("World English Bible"))
            android.util.Log.i("BibleDailyTest", "Shared text: $shared")
        } finally {
            Intents.release()
        }
    }

    @Test
    fun t5_bookmarkHighlightAndNoteAreSaved() {
        rule.waitForBibleReady()
        rule.openReference("Romans 8:28", "Romans 8")
        rule.waitForTag("verse_28")

        rule.onNodeWithTag("verse_28").performClick()
        rule.waitForTag("action_bookmark")
        rule.onNodeWithTag("action_bookmark").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasTestTag("action_bookmark") and hasContentDescription("Remove bookmark"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("highlight_1").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasTestTag("highlight_1") and SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.Selected, true,
            )).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("action_note").performClick()
        rule.waitForTag("note_field")
        rule.onNodeWithTag("note_field").performTextInput(NOTE)
        rule.onNodeWithTag("note_save").performClick()

        rule.waitUntil(10_000) {
            rule.onAllNodes(hasTestTag("verse_28") and hasContentDescription("Has note", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("verse_28")
            .assert(hasContentDescription("Bookmarked", substring = true))
            .assert(hasContentDescription("Highlighted", substring = true))
        rule.screenshot("07_reader_marked_verse")

        rule.onNodeWithTag("tab_saved").performClick()
        rule.onNodeWithTag("saved_tab_notes").performClick()
        rule.waitForText(NOTE)
        rule.onNodeWithText(NOTE, substring = true).assertIsDisplayed()
        rule.screenshot("08_saved_notes")

        // Leave the reader on Romans 8:28 so phase 2 can check that the position is restored.
        rule.onNodeWithTag("tab_read").performClick()
        rule.waitForReaderTitle("Romans 8")
        rule.waitForIdle()
        Thread.sleep(1500) // let DataStore flush the position
    }

    @Test
    fun t6_textSizeAndDarkModeSettings() {
        rule.waitForBibleReady()
        rule.onNodeWithTag("open_settings").performClick()
        rule.waitForTag("theme_dark")
        rule.onNodeWithTag("theme_dark").performClick()
        rule.onNodeWithTag("attribution").assertExists()
        rule.screenshot("09_settings_dark")
        rule.onNodeWithTag("theme_system").performClick()
        rule.onNodeWithTag("settings_back").performClick()
        rule.waitForTag("votd_text")
        assertFalse(rule.onAllNodes(hasTestTag("theme_dark")).fetchSemanticsNodes().isNotEmpty())
    }

    companion object {
        const val NOTE = "Persistence test note"
    }
}
