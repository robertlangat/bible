package com.robertlangat.bibledaily.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.robertlangat.bibledaily.MainActivity
import com.robertlangat.bibledaily.R
import com.robertlangat.bibledaily.appContainer
import com.robertlangat.bibledaily.data.Translations
import com.robertlangat.bibledaily.domain.VerseOfDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZonedDateTime

object ReminderScheduler {
    const val CHANNEL_ID = "verse_of_the_day"
    const val ACTION_REMIND = "com.robertlangat.bibledaily.action.DAILY_VERSE"
    const val EXTRA_TEST = "test"
    private const val REQUEST_CODE = 1001
    private const val NOTIFICATION_ID = 2001
    private const val WINDOW_MS = 10 * 60 * 1000L

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Schedules the next reminder at hour:minute local time (today if still ahead, otherwise tomorrow). */
    fun schedule(context: Context, hour: Int, minute: Int) {
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        // An inexact window alarm needs no special "exact alarm" permission and is battery friendly.
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            WINDOW_MS,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun sendTestNow(context: Context) {
        context.sendBroadcast(
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND).putExtra(EXTRA_TEST, true),
        )
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun showVerseNotification(context: Context) {
        if (!canPostNotifications(context)) return
        val container = context.appContainer
        withTimeoutOrNull(60_000) { container.awaitReady() } ?: return
        val translation = Translations.byId(container.settings.settings.first().translationId)
        val ref = VerseOfDay.referenceFor(LocalDate.now())
        val verse = container.bible.verse(translation.id, ref.book.number, ref.chapter, ref.verse ?: 1)
        val label = ref.label(translation.bookName(ref.book))
        val text = verse?.text ?: return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle("Verse of the day · $label")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n— $label (${translation.abbreviation})"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w("BibleDaily", "Notification permission missing", e)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = appContext.appContainer.settings.settings.first()
                val isTest = intent.getBooleanExtra(ReminderScheduler.EXTRA_TEST, false)
                if (settings.reminderEnabled || isTest) {
                    ReminderScheduler.showVerseNotification(appContext)
                }
                if (settings.reminderEnabled && !isTest) {
                    ReminderScheduler.schedule(appContext, settings.reminderHour, settings.reminderMinute)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Re-arms the daily reminder after a reboot, app update, or clock/time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = appContext.appContainer.settings.settings.first()
                if (settings.reminderEnabled) {
                    ReminderScheduler.schedule(appContext, settings.reminderHour, settings.reminderMinute)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
