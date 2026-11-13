package com.robertlangat.bibledaily.ui.read

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertlangat.bibledaily.data.AppSettings
import com.robertlangat.bibledaily.data.Books
import com.robertlangat.bibledaily.data.Translation
import com.robertlangat.bibledaily.data.db.VerseEntity
import com.robertlangat.bibledaily.data.db.VerseMark
import com.robertlangat.bibledaily.data.db.hasNote
import com.robertlangat.bibledaily.domain.ShareText
import com.robertlangat.bibledaily.ui.ReaderViewModel
import com.robertlangat.bibledaily.ui.common.NoteDialog
import com.robertlangat.bibledaily.ui.common.VerseActionsSheet
import com.robertlangat.bibledaily.ui.common.copyVerse
import com.robertlangat.bibledaily.ui.common.shareVerse
import com.robertlangat.bibledaily.ui.theme.highlightColor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    settings: AppSettings,
    translation: Translation,
    dark: Boolean,
    listState: LazyListState,
    onTextSize: (Float) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scrollRequest by vm.scrollRequest.collectAsStateWithLifecycle()
    val book = Books.byNumber(state.book)
    val bookName = translation.bookName(book)
    val title = "$bookName ${state.chapter}"
    val context = LocalContext.current

    var showPicker by rememberSaveable { mutableStateOf(false) }
    var selectedVerse by rememberSaveable { mutableStateOf<Int?>(null) }
    var noteVerse by rememberSaveable { mutableStateOf<Int?>(null) }

    // Scroll to a requested verse once its chapter has loaded.
    LaunchedEffect(scrollRequest, state.loading, state.book, state.chapter, state.verses.size) {
        val r = scrollRequest ?: return@LaunchedEffect
        if (state.loading || state.book != r.book || state.chapter != r.chapter || state.verses.isEmpty()) {
            return@LaunchedEffect
        }
        val index = state.verses.indexOfFirst { it.verse >= r.verse }
        listState.scrollToItem(if (r.verse <= 1 || index < 0) 0 else index + 1)
        vm.consumeScroll(r)
    }

    // Remember the first visible verse as the reading position.
    val currentState by rememberUpdatedState(state)
    val currentRequest by rememberUpdatedState(scrollRequest)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .debounce(700)
            .collect { index ->
                val s = currentState
                if (currentRequest == null && !s.loading && s.verses.isNotEmpty()) {
                    val verse = s.verses.getOrNull((index - 1).coerceAtLeast(0))?.verse ?: 1
                    vm.savePosition(s.book, s.chapter, verse)
                }
            }
    }

    val prev = Books.previous(state.book, state.chapter)
    val next = Books.next(state.book, state.chapter)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClickLabel = "Choose book and chapter") { showPicker = true }
                            .testTag("reader_title")
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onTextSize(settings.textSizeSp - 2f) },
                        enabled = settings.textSizeSp > AppSettings.MIN_TEXT_SIZE,
                        modifier = Modifier.semantics { contentDescription = "Decrease text size" },
                    ) { Text("A−", fontSize = 15.sp) }
                    TextButton(
                        onClick = { onTextSize(settings.textSizeSp + 2f) },
                        enabled = settings.textSizeSp < AppSettings.MAX_TEXT_SIZE,
                        modifier = Modifier.semantics { contentDescription = "Increase text size" },
                    ) { Text("A+", fontSize = 19.sp) }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val prevLabel = prev?.let { "${translation.bookName(Books.byNumber(it.first))} ${it.second}" }
                    val nextLabel = next?.let { "${translation.bookName(Books.byNumber(it.first))} ${it.second}" }
                    TextButton(
                        onClick = { vm.previous() },
                        enabled = prev != null,
                        modifier = Modifier
                            .semantics { contentDescription = "Previous chapter" + (prevLabel?.let { ", $it" } ?: "") }
                            .testTag("prev_chapter"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                        Text(prevLabel ?: "")
                    }
                    TextButton(
                        onClick = { vm.next() },
                        enabled = next != null,
                        modifier = Modifier
                            .semantics { contentDescription = "Next chapter" + (nextLabel?.let { ", $it" } ?: "") }
                            .testTag("next_chapter"),
                    ) {
                        Text(nextLabel ?: "")
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        },
    ) { inner ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.size(12.dp))
                    Text("Preparing the Bible for offline reading…")
                }
            }
        } else {
            val fontSize = settings.textSizeSp.sp
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(inner).testTag("verse_list"),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "header") {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                            .semantics { heading() },
                    )
                }
                items(state.verses, key = { it.verse }) { verse ->
                    VerseRow(
                        verse = verse,
                        reference = "$bookName ${state.chapter}:${verse.verse}",
                        mark = state.marks[verse.verse],
                        fontSize = fontSize,
                        dark = dark,
                        onClick = { selectedVerse = verse.verse },
                    )
                }
                item(key = "footer") {
                    Column(Modifier.padding(16.dp)) {
                        HorizontalDivider()
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "${translation.name} (${translation.abbreviation}) · ${translation.license}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        BookPickerSheet(
            translation = translation,
            currentBook = state.book,
            onDismiss = { showPicker = false },
            onPick = { b, ch ->
                showPicker = false
                vm.open(b, ch)
            },
        )
    }

    selectedVerse?.let { number ->
        val verse = state.verses.firstOrNull { it.verse == number }
        if (verse == null) {
            selectedVerse = null
        } else {
            val reference = "$bookName ${state.chapter}:$number"
            val shareText = ShareText.format(verse.text, reference, translation.name, translation.abbreviation)
            VerseActionsSheet(
                reference = reference,
                text = verse.text,
                mark = state.marks[number],
                onDismiss = { selectedVerse = null },
                onToggleBookmark = { vm.toggleBookmark(number) },
                onHighlight = { vm.setHighlight(number, it) },
                onNote = {
                    selectedVerse = null
                    noteVerse = number
                },
                onCopy = {
                    selectedVerse = null
                    copyVerse(context, shareText, reference)
                },
                onShare = {
                    selectedVerse = null
                    shareVerse(context, shareText, reference)
                },
            )
        }
    }

    noteVerse?.let { number ->
        NoteDialog(
            reference = "$bookName ${state.chapter}:$number",
            initial = state.marks[number]?.note.orEmpty(),
            onDismiss = { noteVerse = null },
            onSave = {
                vm.setNote(number, it)
                noteVerse = null
            },
            onDelete = {
                vm.setNote(number, null)
                noteVerse = null
            },
        )
    }
}

@Composable
private fun VerseRow(
    verse: VerseEntity,
    reference: String,
    mark: VerseMark?,
    fontSize: TextUnit,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val background = mark?.let { highlightColor(it.highlight, dark) } ?: Color.Transparent
    val description = buildString {
        append(reference).append(". ").append(verse.text)
        if (mark?.bookmarked == true) append(". Bookmarked")
        if ((mark?.highlight ?: 0) != 0) append(". Highlighted")
        if (mark?.hasNote() == true) append(". Has note")
    }
    val numberColor = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(onClickLabel = "Open verse actions", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("verse_${verse.verse}")
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontSize = fontSize * 0.65f,
                        fontWeight = FontWeight.Bold,
                        color = numberColor,
                        baselineShift = BaselineShift.Superscript,
                    ),
                ) { append("${verse.verse} ") }
                append(verse.text)
            },
            fontSize = fontSize,
            lineHeight = fontSize * 1.5f,
            modifier = Modifier.weight(1f),
        )
        if (mark != null && (mark.bookmarked || mark.hasNote())) {
            Column(Modifier.padding(start = 4.dp, top = 4.dp)) {
                if (mark.bookmarked) {
                    Icon(Icons.Filled.Bookmark, contentDescription = null, tint = numberColor, modifier = Modifier.size(18.dp))
                }
                if (mark.hasNote()) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = numberColor, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookPickerSheet(
    translation: Translation,
    currentBook: Int,
    onDismiss: () -> Unit,
    onPick: (Int, Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var testament by rememberSaveable { mutableIntStateOf(if (currentBook <= 39) 0 else 1) }
    var pickedBook by rememberSaveable { mutableStateOf<Int?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val picked = pickedBook
        if (picked == null) {
            TabRow(selectedTabIndex = testament) {
                Tab(
                    selected = testament == 0,
                    onClick = { testament = 0 },
                    text = { Text("Old Testament") },
                    modifier = Modifier.testTag("tab_old"),
                )
                Tab(
                    selected = testament == 1,
                    onClick = { testament = 1 },
                    text = { Text("New Testament") },
                    modifier = Modifier.testTag("tab_new"),
                )
            }
            val books = remember(testament) { Books.all.filter { if (testament == 0) it.number <= 39 else it.number > 39 } }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp).testTag("book_list")) {
                items(books, key = { it.number }) { b ->
                    ListItem(
                        headlineContent = { Text(translation.bookName(b)) },
                        supportingContent = { Text(if (b.chapters == 1) "1 chapter" else "${b.chapters} chapters") },
                        modifier = Modifier
                            .clickable(onClickLabel = "Choose a chapter") { pickedBook = b.number }
                            .testTag("book_${b.number}"),
                    )
                }
            }
        } else {
            val b = Books.byNumber(picked)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = { pickedBook = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to books")
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    translation.bookName(b),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(64.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).testTag("chapter_grid"),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(b.chapters) { i ->
                    val chapter = i + 1
                    Box(
                        Modifier
                            .padding(4.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { onPick(b.number, chapter) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${translation.bookName(b)} chapter $chapter"
                            }
                            .testTag("chapter_$chapter"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$chapter", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}
