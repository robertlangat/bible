package com.robertlangat.bibledaily.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertlangat.bibledaily.data.AppSettings
import com.robertlangat.bibledaily.data.Books
import com.robertlangat.bibledaily.data.Translation
import com.robertlangat.bibledaily.domain.ShareText
import com.robertlangat.bibledaily.ui.HomeViewModel
import com.robertlangat.bibledaily.ui.common.copyVerse
import com.robertlangat.bibledaily.ui.common.shareVerse
import com.robertlangat.bibledaily.ui.formatTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    settings: AppSettings,
    translation: Translation,
    ready: Boolean,
    importError: String?,
    onOpen: (book: Int, chapter: Int, verse: Int?) -> Unit,
    onContinueReading: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val votd by vm.verseOfDay.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshDate() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bible Daily") },
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val v = votd
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Verse of the day",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (v != null) {
                        Text(
                            v.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            v.text,
                            fontSize = (settings.textSizeSp + 1).sp,
                            lineHeight = ((settings.textSizeSp + 1) * 1.45f).sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("votd_text"),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${v.label} (${translation.abbreviation})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("votd_ref"),
                        )
                        Spacer(Modifier.height(12.dp))
                        val share = ShareText.format(v.text, v.label, translation.name, translation.abbreviation)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { onOpen(v.reference.book.number, v.reference.chapter, v.reference.verse) },
                                modifier = Modifier.testTag("votd_read"),
                            ) { Text("Read chapter") }
                            IconButton(onClick = { copyVerse(context, share, v.label) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy verse of the day")
                            }
                            IconButton(onClick = { shareVerse(context, share, v.label) }, modifier = Modifier.testTag("votd_share")) {
                                Icon(Icons.Filled.Share, contentDescription = "Share verse of the day")
                            }
                        }
                    } else if (importError != null) {
                        Spacer(Modifier.height(12.dp))
                        Text("Could not prepare the Bible text: $importError")
                    } else {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(if (ready) "Loading…" else "Preparing the Bible for offline reading…")
                        }
                    }
                }
            }

            val pos = position
            val continueLabel = pos?.let { "${translation.bookName(Books.byNumber(it.book))} ${it.chapter}" } ?: "Genesis 1"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Continue reading", onClick = onContinueReading)
                    .semantics(mergeDescendants = true) { contentDescription = "Continue reading, $continueLabel" }
                    .testTag("continue_reading"),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(if (pos == null) "Start reading" else "Continue reading", style = MaterialTheme.typography.labelLarge)
                        Text(continueLabel, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Daily reminder", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (settings.reminderEnabled) "On at ${formatTime(context, settings.reminderHour, settings.reminderMinute)}" else "Off",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    OutlinedButton(onClick = onOpenSettings) { Text("Change") }
                }
            }

            Text(
                "${translation.name} (${translation.abbreviation}) · ${translation.license}. Works fully offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
