package com.robertlangat.bibledaily.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedCard
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertlangat.bibledaily.data.Books
import com.robertlangat.bibledaily.data.Translation
import com.robertlangat.bibledaily.ui.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: SearchViewModel,
    translation: Translation,
    onOpen: (book: Int, chapter: Int, verse: Int?) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    Scaffold(topBar = { TopAppBar(title = { Text("Search") }) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_field"),
                singleLine = true,
                label = { Text("Words, phrases or references") },
                placeholder = { Text("e.g. John 3:16, Psalm 23, love one another") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            )
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())

            val results = state.results
            if (results == null) {
                Text(
                    "Search the ${translation.name} offline. Try a reference like “John 3:16”, “Jn 3:16” or " +
                        "“Psalm 23”, a word like “shepherd”, or a phrase like “love one another”. " +
                        "Put a phrase in quotes to match it exactly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }

            val summary = when {
                results.reference != null && results.verses.isEmpty() -> "Reference found"
                results.verses.isEmpty() && results.reference == null -> "No results for “${results.query}”"
                results.truncated -> "Showing the first ${results.verses.size} verses"
                else -> "${results.verses.size} verse${if (results.verses.size == 1) "" else "s"} found"
            }
            Text(
                summary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("search_summary"),
            )

            LazyColumn(Modifier.fillMaxSize().testTag("search_results"), contentPadding = PaddingValues(bottom = 16.dp)) {
                results.reference?.let { hit ->
                    item(key = "ref") {
                        OutlinedCard(
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable(onClickLabel = "Open ${hit.label}") {
                                    onOpen(hit.reference.book.number, hit.reference.chapter, hit.reference.verse)
                                }
                                .testTag("ref_result"),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(hit.label, style = MaterialTheme.typography.titleMedium)
                                Text(hit.preview, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                items(results.verses, key = { "${it.book}_${it.chapter}_${it.verse}" }) { v ->
                    val label = "${translation.bookName(Books.byNumber(v.book))} ${v.chapter}:${v.verse}"
                    ListItem(
                        headlineContent = { Text(label, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(emphasize(v.text, results.terms, MaterialTheme.colorScheme.primary)) },
                        modifier = Modifier
                            .clickable(onClickLabel = "Open $label") { onOpen(v.book, v.chapter, v.verse) }
                            .testTag("result_${v.book}_${v.chapter}_${v.verse}"),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Bolds the start-of-word occurrences of the search terms. */
private fun emphasize(text: String, terms: List<String>, color: Color): AnnotatedString {
    if (terms.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    val ranges = mutableListOf<IntRange>()
    for (term in terms) {
        var start = lower.indexOf(term)
        while (start >= 0) {
            if (start == 0 || !lower[start - 1].isLetterOrDigit()) ranges += start until start + term.length
            start = lower.indexOf(term, start + term.length)
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for (r in ranges) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color), r.first, r.last + 1)
    }
}
