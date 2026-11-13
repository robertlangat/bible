package com.robertlangat.bibledaily.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val textSizeSp: Float = DEFAULT_TEXT_SIZE,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val translationId: String = Translations.default.id,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 7,
    val reminderMinute: Int = 0,
) {
    companion object {
        const val DEFAULT_TEXT_SIZE = 19f
        const val MIN_TEXT_SIZE = 14f
        const val MAX_TEXT_SIZE = 34f
    }
}

data class ReadingPosition(val book: Int, val chapter: Int, val verse: Int)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val textSize = floatPreferencesKey("text_size")
        val theme = stringPreferencesKey("theme")
        val translation = stringPreferencesKey("translation")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val lastBook = intPreferencesKey("last_book")
        val lastChapter = intPreferencesKey("last_chapter")
        val lastVerse = intPreferencesKey("last_verse")
    }

    private val prefs: Flow<Preferences> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<AppSettings> = prefs.map { p ->
        AppSettings(
            textSizeSp = p[Keys.textSize] ?: AppSettings.DEFAULT_TEXT_SIZE,
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.LIGHT,
            translationId = p[Keys.translation] ?: Translations.default.id,
            reminderEnabled = p[Keys.reminderEnabled] ?: false,
            reminderHour = p[Keys.reminderHour] ?: 7,
            reminderMinute = p[Keys.reminderMinute] ?: 0,
        )
    }.distinctUntilChanged()

    val position: Flow<ReadingPosition?> = prefs.map { p ->
        val book = p[Keys.lastBook] ?: return@map null
        ReadingPosition(book, p[Keys.lastChapter] ?: 1, p[Keys.lastVerse] ?: 1)
    }.distinctUntilChanged()

    suspend fun setTextSize(size: Float) {
        context.dataStore.edit {
            it[Keys.textSize] = size.coerceIn(AppSettings.MIN_TEXT_SIZE, AppSettings.MAX_TEXT_SIZE)
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.theme] = mode.name }
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.reminderEnabled] = enabled
            it[Keys.reminderHour] = hour
            it[Keys.reminderMinute] = minute
        }
    }

    suspend fun savePosition(position: ReadingPosition) {
        context.dataStore.edit {
            it[Keys.lastBook] = position.book
            it[Keys.lastChapter] = position.chapter
            it[Keys.lastVerse] = position.verse
        }
    }
}
