package com.robertlangat.bibledaily.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.robertlangat.bibledaily.AppContainer
import com.robertlangat.bibledaily.data.Translations
import com.robertlangat.bibledaily.ui.home.HomeScreen
import com.robertlangat.bibledaily.ui.read.ReaderScreen
import com.robertlangat.bibledaily.ui.saved.SavedScreen
import com.robertlangat.bibledaily.ui.search.SearchScreen
import com.robertlangat.bibledaily.ui.settings.SettingsScreen
import com.robertlangat.bibledaily.ui.theme.BibleDailyTheme
import com.robertlangat.bibledaily.ui.theme.isAppInDarkTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class MainTab(val label: String, val icon: ImageVector, val tag: String) {
    HOME("Home", Icons.Filled.Home, "tab_home"),
    READ("Read", Icons.AutoMirrored.Filled.MenuBook, "tab_read"),
    SEARCH("Search", Icons.Filled.Search, "tab_search"),
    SAVED("Saved", Icons.Filled.Bookmarks, "tab_saved"),
}

fun formatTime(context: Context, hour: Int, minute: Int): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern))
}

@Composable
fun BibleDailyRoot(container: AppContainer) {
    val mainVm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(container) } })
    val homeVm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container) } })
    val readerVm: ReaderViewModel = viewModel(factory = viewModelFactory { initializer { ReaderViewModel(container) } })
    val searchVm: SearchViewModel = viewModel(factory = viewModelFactory { initializer { SearchViewModel(container) } })
    val savedVm: SavedViewModel = viewModel(factory = viewModelFactory { initializer { SavedViewModel(container) } })

    val settings by mainVm.settings.collectAsStateWithLifecycle()
    val ready by mainVm.ready.collectAsStateWithLifecycle()
    val importError by mainVm.importError.collectAsStateWithLifecycle()
    val translation = Translations.byId(settings.translationId)
    val dark = isAppInDarkTheme(settings.themeMode)

    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Hoisted so the reading scroll position survives switching tabs.
    val readerListState = rememberLazyListState()

    val openVerse: (Int, Int, Int?) -> Unit = { book, chapter, verse ->
        readerVm.open(book, chapter, verse)
        tab = MainTab.READ
    }
    val setTextSize: (Float) -> Unit = { mainVm.setTextSize(it) }

    BibleDailyTheme(dark = dark) {
        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(
                settings = settings,
                translation = translation,
                onBack = { showSettings = false },
                onTheme = { mainVm.setTheme(it) },
                onTextSize = setTextSize,
                onReminder = { enabled, hour, minute -> mainVm.setReminder(enabled, hour, minute) },
                onTestReminder = { mainVm.sendTestReminder() },
            )
            return@BibleDailyTheme
        }

        BackHandler(enabled = tab != MainTab.HOME) { tab = MainTab.HOME }
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                            modifier = Modifier.testTag(t.tag),
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                when (tab) {
                    MainTab.HOME -> HomeScreen(
                        vm = homeVm,
                        settings = settings,
                        translation = translation,
                        ready = ready,
                        importError = importError,
                        onOpen = openVerse,
                        onContinueReading = { tab = MainTab.READ },
                        onOpenSettings = { showSettings = true },
                    )
                    MainTab.READ -> ReaderScreen(
                        vm = readerVm,
                        settings = settings,
                        translation = translation,
                        dark = dark,
                        listState = readerListState,
                        onTextSize = setTextSize,
                    )
                    MainTab.SEARCH -> SearchScreen(vm = searchVm, translation = translation, onOpen = openVerse)
                    MainTab.SAVED -> SavedScreen(vm = savedVm, translation = translation, dark = dark, onOpen = openVerse)
                }
            }
        }
    }
}
