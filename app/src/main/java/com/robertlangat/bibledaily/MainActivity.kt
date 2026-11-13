package com.robertlangat.bibledaily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.robertlangat.bibledaily.ads.AdsController
import com.robertlangat.bibledaily.ui.BibleDailyRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BibleDailyRoot(appContainer) }
        AdsController.gatherConsentAndInit(this)
    }
}
