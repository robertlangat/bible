package com.robertlangat.bibledaily.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.robertlangat.bibledaily.ads.AdsController
import com.robertlangat.bibledaily.data.db.VerseMark
import com.robertlangat.bibledaily.data.db.hasNote
import com.robertlangat.bibledaily.ui.theme.HighlightColors
import com.robertlangat.bibledaily.ui.theme.HighlightNames

fun shareVerse(context: Context, text: String, reference: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, reference)
    }
    AdsController.suppressNextAppOpenAd()
    context.startActivity(Intent.createChooser(send, "Share $reference"))
}

fun copyVerse(context: Context, text: String, reference: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(reference, text))
    // Android 13+ shows its own clipboard confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied $reference", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseActionsSheet(
    reference: String,
    text: String,
    mark: VerseMark?,
    onDismiss: () -> Unit,
    onToggleBookmark: () -> Unit,
    onHighlight: (Int) -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .testTag("verse_actions"),
        ) {
            Text(reference, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val bookmarked = mark?.bookmarked == true
                ActionButton(
                    icon = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    label = if (bookmarked) "Bookmarked" else "Bookmark",
                    description = if (bookmarked) "Remove bookmark" else "Bookmark verse",
                    tag = "action_bookmark",
                    onClick = onToggleBookmark,
                )
                ActionButton(
                    Icons.Filled.Edit,
                    if (mark?.hasNote() == true) "Edit note" else "Note",
                    if (mark?.hasNote() == true) "Edit note" else "Add note",
                    "action_note",
                    onNote,
                )
                ActionButton(Icons.Filled.ContentCopy, "Copy", "Copy verse", "action_copy", onCopy)
                ActionButton(Icons.Filled.Share, "Share", "Share verse", "action_share", onShare)
            }
            Spacer(Modifier.height(16.dp))
            Text("Highlight", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                HighlightColors.forEachIndexed { i, color ->
                    val index = i + 1
                    val selected = mark?.highlight == index
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable(role = Role.RadioButton) { onHighlight(if (selected) 0 else index) }
                            .semantics {
                                contentDescription = "Highlight ${HighlightNames[i]}"
                                this.selected = selected
                            }
                            .testTag("highlight_$index"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                    }
                }
                if ((mark?.highlight ?: 0) != 0) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(role = Role.Button) { onHighlight(0) }
                            .semantics { contentDescription = "Remove highlight" }
                            .testTag("highlight_0"),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, contentDescription = null) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, description: String, tag: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(tag)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun NoteDialog(
    reference: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note · $reference") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Your note") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("note_field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, modifier = Modifier.testTag("note_save")) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) {
                    TextButton(onClick = onDelete, modifier = Modifier.testTag("note_delete")) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
