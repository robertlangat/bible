package com.robertlangat.bibledaily.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robertlangat.bibledaily.BuildConfig
import com.robertlangat.bibledaily.ads.AdsController
import com.robertlangat.bibledaily.data.AppSettings
import com.robertlangat.bibledaily.data.ThemeMode
import com.robertlangat.bibledaily.data.Translation
import com.robertlangat.bibledaily.reminder.ReminderScheduler
import com.robertlangat.bibledaily.ui.formatTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    translation: Translation,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onTextSize: (Float) -> Unit,
    onReminder: (enabled: Boolean, hour: Int, minute: Int) -> Unit,
    onTestReminder: () -> Unit,
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionDenied = false
            onReminder(true, settings.reminderHour, settings.reminderMinute)
        } else {
            permissionDenied = true
        }
    }

    fun enableReminder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ReminderScheduler.canPostNotifications(context)) {
            AdsController.suppressNextAppOpenAd()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onReminder(true, settings.reminderHour, settings.reminderMinute)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Appearance")
            Column(Modifier.selectableGroup()) {
                listOf(ThemeMode.SYSTEM to "Use phone setting", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                    .forEach { (mode, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = settings.themeMode == mode, role = Role.RadioButton) { onTheme(mode) }
                                .testTag("theme_${mode.name.lowercase()}")
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.themeMode == mode, onClick = null)
                            Text(label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
            }

            HorizontalDivider()
            SectionTitle("Text size")
            var size by remember(settings.textSizeSp) { mutableFloatStateOf(settings.textSizeSp) }
            Slider(
                value = size,
                onValueChange = { size = it },
                onValueChangeFinished = { onTextSize(size) },
                valueRange = AppSettings.MIN_TEXT_SIZE..AppSettings.MAX_TEXT_SIZE,
                steps = ((AppSettings.MAX_TEXT_SIZE - AppSettings.MIN_TEXT_SIZE) / 2f).roundToInt() - 1,
                modifier = Modifier
                    .semantics {
                        contentDescription = "Text size"
                        stateDescription = "${size.roundToInt()} points"
                    }
                    .testTag("text_size_slider"),
            )
            Text(
                "In the beginning, God created the heavens and the earth.",
                fontSize = size.sp,
                lineHeight = (size * 1.5f).sp,
            )

            HorizontalDivider()
            SectionTitle("Daily reminder")
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = settings.reminderEnabled, role = Role.Switch) { on ->
                        if (on) enableReminder() else onReminder(false, settings.reminderHour, settings.reminderMinute)
                    }
                    .testTag("reminder_switch")
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Remind me every day")
                    Text(
                        "Shows the verse of the day as a notification",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.reminderEnabled, onCheckedChange = null)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Time: ${formatTime(context, settings.reminderHour, settings.reminderMinute)}",
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> onReminder(settings.reminderEnabled, hour, minute) },
                            settings.reminderHour,
                            settings.reminderMinute,
                            DateFormat.is24HourFormat(context),
                        ).show()
                    },
                    modifier = Modifier.testTag("reminder_time"),
                ) { Text("Change time") }
            }
            if (permissionDenied) {
                Text(
                    "Notifications are blocked for Bible Daily. Allow them in Android Settings › Apps › Bible Daily › Notifications to get the daily reminder.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = {
                if (ReminderScheduler.canPostNotifications(context)) {
                    onTestReminder()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    AdsController.suppressNextAppOpenAd()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }) { Text("Send a test reminder now") }

            HorizontalDivider()
            SectionTitle("About this Bible")
            Text("${translation.name} (${translation.abbreviation}) · ${translation.languageName}", style = MaterialTheme.typography.titleMedium)
            Text(translation.editionNote, style = MaterialTheme.typography.bodyMedium)
            Text("License: ${translation.license}", style = MaterialTheme.typography.bodyMedium)
            Text("Source: ${translation.source}", style = MaterialTheme.typography.bodyMedium)
            Text(translation.sourceUrl, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(translation.attribution, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("attribution"))

            HorizontalDivider()
            SectionTitle("About Bible Daily")
            Text(
                "Version ${BuildConfig.VERSION_NAME}. Reading, search and saved items work fully offline with no account. " +
                    "Bookmarks, highlights, notes and settings are stored only on this phone. " +
                    "The internet is used only to show ads (Google AdMob test ads in this build).",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}
