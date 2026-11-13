package com.robertlangat.bibledaily.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertlangat.bibledaily.AppContainer
import com.robertlangat.bibledaily.data.AppSettings
import com.robertlangat.bibledaily.data.Books
import com.robertlangat.bibledaily.data.ReadingPosition
import com.robertlangat.bibledaily.data.SearchResults
import com.robertlangat.bibledaily.data.ThemeMode
import com.robertlangat.bibledaily.data.Translations
import com.robertlangat.bibledaily.data.db.MarkWithText
import com.robertlangat.bibledaily.data.db.VerseEntity
import com.robertlangat.bibledaily.data.db.VerseMark
import com.robertlangat.bibledaily.domain.BibleReference
import com.robertlangat.bibledaily.domain.VerseOfDay
import com.robertlangat.bibledaily.reminder.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val ready: StateFlow<Boolean> = c.ready
    val importError: StateFlow<String?> = c.importError

    fun setTextSize(size: Float) = viewModelScope.launch { c.settings.setTextSize(size) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.settings.setTheme(mode) }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        c.settings.setReminder(enabled, hour, minute)
        if (enabled) ReminderScheduler.schedule(c.appContext, hour, minute) else ReminderScheduler.cancel(c.appContext)
    }

    fun sendTestReminder() = ReminderScheduler.sendTestNow(c.appContext)
}

data class VerseOfDayState(val reference: BibleReference, val label: String, val text: String, val date: LocalDate)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val date = MutableStateFlow(LocalDate.now())

    val verseOfDay: StateFlow<VerseOfDayState?> = combine(
        c.ready,
        date,
        c.settings.settings.map { it.translationId }.distinctUntilChanged(),
    ) { ready, day, translationId -> Triple(ready, day, translationId) }
        .mapLatest { (ready, day, translationId) ->
            if (!ready) return@mapLatest null
            val translation = Translations.byId(translationId)
            val ref = VerseOfDay.referenceFor(day)
            val verse = c.bible.verse(translation.id, ref.book.number, ref.chapter, ref.verse ?: 1)
            VerseOfDayState(ref, ref.label(translation.bookName(ref.book)), verse?.text.orEmpty(), day)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val position: StateFlow<ReadingPosition?> =
        c.settings.position.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Called on resume so the verse changes after midnight without restarting the app. */
    fun refreshDate() {
        date.value = LocalDate.now()
    }
}

data class ReaderState(
    val book: Int = 1,
    val chapter: Int = 1,
    val verses: List<VerseEntity> = emptyList(),
    val marks: Map<Int, VerseMark> = emptyMap(),
    val loading: Boolean = true,
)

/** A pending request to scroll the reader to [verse] once (book, chapter) is displayed. */
data class ScrollRequest(val book: Int, val chapter: Int, val verse: Int, val token: Long = System.nanoTime())

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(private val c: AppContainer) : ViewModel() {
    private val location = MutableStateFlow<Pair<Int, Int>?>(null)
    private val _scrollRequest = MutableStateFlow<ScrollRequest?>(null)
    val scrollRequest: StateFlow<ScrollRequest?> = _scrollRequest.asStateFlow()

    init {
        // Resume the last reading position (defaults to Genesis 1 on first launch).
        viewModelScope.launch {
            val saved = c.settings.position.first()
            if (location.value == null) {
                val book = saved?.book?.takeIf { Books.byNumberOrNull(it) != null } ?: 1
                val chapter = saved?.chapter?.coerceIn(1, Books.byNumber(book).chapters) ?: 1
                location.value = book to chapter
                _scrollRequest.value = ScrollRequest(book, chapter, saved?.verse ?: 1)
            }
        }
    }

    val state: StateFlow<ReaderState> = combine(location.filterNotNull(), c.ready) { loc, ready -> loc to ready }
        .flatMapLatest { (loc, ready) ->
            val (book, chapter) = loc
            if (!ready) {
                flowOf(ReaderState(book, chapter, loading = true))
            } else {
                val translationId = c.settings.settings.first().translationId
                val verses = flow { emit(c.bible.chapter(translationId, book, chapter)) }
                combine(verses, c.bible.observeChapterMarks(book, chapter)) { v, m ->
                    ReaderState(book, chapter, v, m.associateBy { it.verse }, loading = false)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderState())

    fun open(book: Int, chapter: Int, verse: Int? = null) {
        location.value = book to chapter
        _scrollRequest.value = ScrollRequest(book, chapter, verse ?: 1)
        savePosition(book, chapter, verse ?: 1)
    }

    fun next() {
        val loc = location.value ?: return
        Books.next(loc.first, loc.second)?.let { (b, ch) -> open(b, ch) }
    }

    fun previous() {
        val loc = location.value ?: return
        Books.previous(loc.first, loc.second)?.let { (b, ch) -> open(b, ch) }
    }

    fun consumeScroll(request: ScrollRequest) {
        if (_scrollRequest.value?.token == request.token) _scrollRequest.value = null
    }

    fun savePosition(book: Int, chapter: Int, verse: Int) = viewModelScope.launch {
        c.settings.savePosition(ReadingPosition(book, chapter, verse))
    }

    fun toggleBookmark(verse: Int) = mark(verse) { it.copy(bookmarked = !it.bookmarked) }
    fun setHighlight(verse: Int, color: Int) = mark(verse) { it.copy(highlight = color) }
    fun setNote(verse: Int, note: String?) = mark(verse) { it.copy(note = note?.trim()?.takeIf { n -> n.isNotEmpty() }) }

    private fun mark(verse: Int, transform: (VerseMark) -> VerseMark) {
        val loc = location.value ?: return
        viewModelScope.launch { c.bible.updateMark(loc.first, loc.second, verse, transform) }
    }
}

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: SearchResults? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val c: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(query.debounce(250), c.ready) { q, ready -> q to ready }
                .mapLatest { (q, ready) ->
                    if (q.isBlank() || !ready) {
                        null
                    } else {
                        _state.value = _state.value.copy(searching = true)
                        val translation = Translations.byId(c.settings.settings.first().translationId)
                        c.bible.search(translation, q)
                    }
                }
                .collect { results -> _state.value = _state.value.copy(searching = false, results = results) }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
        // Drop results for the previous query straight away so stale results are never tapped.
        _state.value = _state.value.copy(query = value, results = null, searching = value.isNotBlank())
    }
}

class SavedViewModel(c: AppContainer) : ViewModel() {
    private val t = Translations.default.id
    val bookmarks: StateFlow<List<MarkWithText>> =
        c.bible.observeBookmarks(t).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val highlights: StateFlow<List<MarkWithText>> =
        c.bible.observeHighlights(t).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes: StateFlow<List<MarkWithText>> =
        c.bible.observeNotes(t).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val bible = c.bible

    fun removeBookmark(m: VerseMark) = viewModelScope.launch {
        bible.updateMark(m.book, m.chapter, m.verse) { it.copy(bookmarked = false) }
    }
    fun removeHighlight(m: VerseMark) = viewModelScope.launch {
        bible.updateMark(m.book, m.chapter, m.verse) { it.copy(highlight = 0) }
    }
    fun removeNote(m: VerseMark) = viewModelScope.launch {
        bible.updateMark(m.book, m.chapter, m.verse) { it.copy(note = null) }
    }
}
