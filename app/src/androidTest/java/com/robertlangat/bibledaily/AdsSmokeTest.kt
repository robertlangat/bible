package com.robertlangat.bibledaily

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.gms.ads.AdActivity
import com.robertlangat.bibledaily.ads.AdsController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 3 (network ON): the AdMob test banner and interstitial load and show. */
@RunWith(AndroidJUnit4::class)
class AdsSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun resumedActivity(): Activity? {
        var result: Activity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return result
    }

    @Test
    fun bannerAndInterstitialShowWithTestUnits() {
        rule.waitForBibleReady()
        rule.waitUntil(120_000) { rule.onAllNodes(hasTestTag("ad_banner_loaded")).fetchSemanticsNodes().isNotEmpty() }
        rule.screenshot("12_banner_ad_home")

        rule.onNodeWithTag("tab_read").performClick()
        rule.waitForTag("next_chapter")
        rule.waitUntil(120_000) { AdsController.interstitialReady }
        repeat(AdsController.CHAPTERS_PER_INTERSTITIAL) {
            rule.onNodeWithTag("next_chapter").performClick()
            rule.waitForIdle()
        }

        val deadline = System.currentTimeMillis() + 30_000
        var shown = false
        while (System.currentTimeMillis() < deadline && !shown) {
            shown = resumedActivity() is AdActivity
            if (!shown) Thread.sleep(500)
        }
        assertTrue("Interstitial (AdActivity) should be showing", shown)
        Thread.sleep(4000)
        rule.screenshot("13_interstitial_ad")
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Thread.sleep(2000)
    }
}
