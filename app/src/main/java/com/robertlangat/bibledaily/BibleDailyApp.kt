package com.robertlangat.bibledaily

import android.app.Application
import android.content.Context
import android.util.Log
import com.robertlangat.bibledaily.ads.AdsController
import com.robertlangat.bibledaily.data.BibleRepository
import com.robertlangat.bibledaily.data.SettingsRepository
import com.robertlangat.bibledaily.data.Translations
import com.robertlangat.bibledaily.data.db.BibleDatabase
import com.robertlangat.bibledaily.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Simple manual dependency container shared by the UI, receivers and tests. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: BibleDatabase = BibleDatabase.create(appContext)
    val settings = SettingsRepository(appContext)
    val bible = BibleRepository(appContext, database)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _ready = MutableStateFlow(false)
    /** True once the bundled Bible text is available in the local database. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    fun startImport() {
        scope.launch(Dispatchers.IO) {
            try {
                bible.ensureImported(Translations.default)
                _ready.value = true
            } catch (e: Exception) {
                Log.e("BibleDaily", "Bible import failed", e)
                _importError.value = e.message ?: e.toString()
            }
        }
    }

    suspend fun awaitReady() {
        ready.first { it }
    }
}

class BibleDailyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.createChannel(this)
        container.startImport()
        AdsController.init(this)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as BibleDailyApp).container
