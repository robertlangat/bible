package com.robertlangat.bibledaily.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertlangat.bibledaily.data.Books
import com.robertlangat.bibledaily.data.Translation
import com.robertlangat.bibledaily.data.db.MarkWithText
import com.robertlangat.bibledaily.data.db.VerseMark
import com.robertlangat.bibledaily.ui.SavedViewModel
import com.robertlangat.bibledaily.ui.theme.highlightColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    vm: SavedViewModel,
    translation: Translation,
    dark: Boolean,
    onOpen: (book: Int, chapter: Int, verse: Int?) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val highlights by vm.highlights.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Saved") }) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            val tabs = listOf(
                Triple("Bookmarks", bookmarks.size, "saved_tab_bookmarks"),
                Triple("Highlights", highlights.size, "saved_tab_highlights"),
                Triple("Notes", notes.size, "saved_tab_notes"),
            )
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, (label, count, tag) ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text("$label ($count)") },
                        modifier = Modifier.testTag(tag),
                    )
                }
            }
            val items = when (tab) {
                0 -> bookmarks
                1 -> highlights
                else -> notes
            }
            if (items.isEmpty()) {
                Text(
                    when (tab) {
                        0 -> "No bookmarks yet. Tap a verse while reading and choose Bookmark."
                        1 -> "No highlights yet. Tap a verse while reading and pick a colour."
                        else -> "No notes yet. Tap a verse while reading and choose Note."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(items, key = { "${it.mark.book}-${it.mark.chapter}-${it.mark.verse}" }) { item ->
                        SavedRow(
                            item = item,
                            kind = tab,
                            translation = translation,
                            dark = dark,
                            onOpen = { onOpen(item.mark.book, item.mark.chapter, item.mark.verse) },
                            onRemove = {
                                when (tab) {
                                    0 -> vm.removeBookmark(item.mark)
                                    1 -> vm.removeHighlight(item.mark)
                                    else -> vm.removeNote(item.mark)
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun VerseMark.label(translation: Translation) =
    "${translation.bookName(Books.byNumber(book))} $chapter:$verse"

@Composable
private fun SavedRow(
    item: MarkWithText,
    kind: Int,
    translation: Translation,
    dark: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val label = item.mark.label(translation)
    val what = when (kind) {
        0 -> "bookmark"
        1 -> "highlight"
        else -> "note"
    }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Column {
                if (kind == 2 && !item.mark.note.isNullOrBlank()) {
                    Text(item.mark.note.orEmpty(), fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary)
                }
                Text(item.verseText.orEmpty(), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = highlightColor(item.mark.highlight, dark)?.let { color ->
            { Box(Modifier.size(16.dp).clip(CircleShape).background(color)) }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove $what for $label")
            }
        },
        modifier = Modifier
            .clickable(onClickLabel = "Open $label", onClick = onOpen)
            .testTag("saved_item"),
    )
}
