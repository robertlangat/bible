package com.robertlangat.bibledaily.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.robertlangat.bibledaily.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "BibleDailyAds"

/**
 * AdMob integration (test ad units, see app/build.gradle.kts):
 *  - App open ad: shown when the user returns to the app from the background (never on a cold start).
 *  - Banner: adaptive banner above the bottom navigation bar; it takes no space until an ad loads.
 *  - Interstitial: at most once every [CHAPTERS_PER_INTERSTITIAL] chapter changes, and never within
 *    [MIN_FULLSCREEN_GAP_MS] of another full-screen ad.
 * All Bible features keep working offline; ads simply don't load without a connection.
 */
object AdsController : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    const val CHAPTERS_PER_INTERSTITIAL = 3
    const val MIN_FULLSCREEN_GAP_MS = 2 * 60 * 1000L
    private const val APP_OPEN_MAX_AGE_MS = 4 * 60 * 60 * 1000L
    private const val RETRY_MS = 60_000L

    private lateinit var app: Application
    private val main = Handler(Looper.getMainLooper())
    private var initialized = false

    private var currentActivity: Activity? = null
    private var firstForeground = true
    private var suppressNextAppOpen = false
    private var showingFullScreen = false
    private var lastFullScreenAt = 0L

    private var appOpenAd: AppOpenAd? = null
    private var appOpenLoadedAt = 0L
    private var appOpenLoading = false

    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var chapterChanges = 0

    val interstitialReady: Boolean get() = interstitial != null
    val appOpenReady: Boolean get() = appOpenAd != null
    private val appOpenEnabled: Boolean get() = BuildConfig.ADMOB_APP_OPEN_ID.isNotBlank()

    private val _ready = MutableStateFlow(false)
    /** True once consent allows ads and the SDK has been started. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var consentInfo: ConsentInformation? = null

    /**
     * Runs Google's User Messaging Platform consent flow (shows the GDPR/US-states form only where
     * required and configured in AdMob › Privacy & messaging), then starts ads if allowed.
     * Call from the main activity's onCreate.
     */
    fun gatherConsentAndInit(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        consentInfo = info
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form error: ${error.message}")
                    if (info.canRequestAds()) init(activity.application)
                }
            },
            { error ->
                Log.w(TAG, "Consent info update failed: ${error.message}")
                if (info.canRequestAds()) init(activity.application)
            },
        )
        // Consent from a previous session lets ads start right away.
        if (info.canRequestAds()) init(activity.application)
    }

    /** Whether Settings should show the "Ad privacy choices" entry (required in some regions). */
    fun privacyOptionsRequired(): Boolean =
        consentInfo?.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options error: ${error.message}")
        }
    }

    private fun init(application: Application) {
        if (initialized) return
        initialized = true
        app = application
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        _ready.value = true
        // The SDK recommends initializing off the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(app) {
                main.post {
                    loadAppOpen()
                    loadInterstitial()
                }
            }
        }
    }

    /** Call before launching another app's screen (e.g. the share sheet) so returning doesn't show an ad. */
    fun suppressNextAppOpenAd() {
        suppressNextAppOpen = true
    }

    // ---- App open ---------------------------------------------------------------------------

    private fun loadAppOpen() {
        if (!appOpenEnabled || appOpenLoading || appOpenAd != null) return
        appOpenLoading = true
        AppOpenAd.load(
            app,
            BuildConfig.ADMOB_APP_OPEN_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    appOpenLoadedAt = SystemClock.elapsedRealtime()
                    appOpenLoading = false
                    Log.i(TAG, "App open ad loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenLoading = false
                    Log.i(TAG, "App open ad failed to load: ${error.message}")
                    main.postDelayed({ loadAppOpen() }, RETRY_MS)
                }
            },
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        // Process came to the foreground.
        if (firstForeground) {
            firstForeground = false
            return
        }
        if (suppressNextAppOpen) {
            suppressNextAppOpen = false
            return
        }
        currentActivity?.let { showAppOpen(it) }
    }

    private fun showAppOpen(activity: Activity) {
        if (!initialized || !appOpenEnabled || showingFullScreen) return
        val ad = appOpenAd
        if (ad == null || SystemClock.elapsedRealtime() - appOpenLoadedAt > APP_OPEN_MAX_AGE_MS) {
            appOpenAd = null
            loadAppOpen()
            return
        }
        ad.fullScreenContentCallback = fullScreenCallback("App open") {
            appOpenAd = null
            loadAppOpen()
        }
        showingFullScreen = true
        ad.show(activity)
    }

    // ---- Interstitial -----------------------------------------------------------------------

    private fun loadInterstitial() {
        if (interstitialLoading || interstitial != null) return
        interstitialLoading = true
        InterstitialAd.load(
            app,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    interstitialLoading = false
                    Log.i(TAG, "Interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    Log.i(TAG, "Interstitial failed to load: ${error.message}")
                    main.postDelayed({ loadInterstitial() }, RETRY_MS)
                }
            },
        )
    }

    /** Called when the reader moves to the previous/next chapter. */
    fun onChapterChanged(context: Context) {
        if (!initialized) return
        chapterChanges++
        val activity = context.findActivity() ?: return
        val ad = interstitial
        val sinceLast = SystemClock.elapsedRealtime() - lastFullScreenAt
        if (ad == null) {
            loadInterstitial()
            return
        }
        if (chapterChanges < CHAPTERS_PER_INTERSTITIAL || showingFullScreen ||
            (lastFullScreenAt != 0L && sinceLast < MIN_FULLSCREEN_GAP_MS)
        ) return
        chapterChanges = 0
        ad.fullScreenContentCallback = fullScreenCallback("Interstitial") {
            interstitial = null
            loadInterstitial()
        }
        showingFullScreen = true
        ad.show(activity)
    }

    private fun fullScreenCallback(name: String, onDone: () -> Unit) = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            Log.i(TAG, "$name ad shown")
            lastFullScreenAt = SystemClock.elapsedRealtime()
        }

        override fun onAdDismissedFullScreenContent() {
            Log.i(TAG, "$name ad dismissed")
            showingFullScreen = false
            lastFullScreenAt = SystemClock.elapsedRealtime()
            onDone()
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            Log.i(TAG, "$name ad failed to show: ${error.message}")
            showingFullScreen = false
            onDone()
        }
    }

    // ---- Activity tracking ------------------------------------------------------------------

    override fun onActivityStarted(activity: Activity) {
        if (activity !is AdActivity) currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is AdActivity) currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Adaptive banner. Collapses to zero height until an ad has loaded (e.g. offline or before consent). */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val adsReady by AdsController.ready.collectAsState()
    if (!adsReady) return
    var loaded by remember { mutableStateOf(false) }
    val widthDp = LocalConfiguration.current.screenWidthDp
    val main = remember { Handler(Looper.getMainLooper()) }
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .then(if (loaded) Modifier else Modifier.height(0.dp))
            .semantics { contentDescription = "Advertisement" }
            .testTag(if (loaded) "ad_banner_loaded" else "ad_banner"),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                val view = this
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        loaded = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loaded = false
                        Log.i(TAG, "Banner failed to load: ${error.message}")
                        main.postDelayed({ if (view.isAttachedToWindow) view.loadAd(AdRequest.Builder().build()) }, 60_000L)
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
    )
}
