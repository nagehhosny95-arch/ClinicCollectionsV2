package com.nageh.cliniccollections.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nageh.cliniccollections.ClinicApp
import com.nageh.cliniccollections.aed
import com.nageh.cliniccollections.collectionState
import com.nageh.cliniccollections.formatDate
import com.nageh.cliniccollections.stateLabel
import com.nageh.cliniccollections.MainActivity
import com.nageh.cliniccollections.R
import com.nageh.cliniccollections.data.AppDatabase
import com.nageh.cliniccollections.data.InvoiceEntity
import com.nageh.cliniccollections.data.PaymentStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

object ReminderScheduler {

    /** Both reminders fire at 10:30 local device time. */
    private const val HOUR = 10
    private const val MINUTE = 30
    private val OFFSETS = listOf(-1, 0)

    fun schedule(context: Context, item: InvoiceEntity) {
        cancel(context, item.id)
        if (item.computedStatus() == PaymentStatus.PAID) return
        OFFSETS.forEach { scheduleOne(context, item, it) }
    }

    fun rescheduleAll(context: Context, invoices: List<InvoiceEntity>) {
        invoices.forEach { schedule(context, it) }
    }

    private fun scheduleOne(context: Context, item: InvoiceEntity, offset: Int) {
        val triggerAt = item.collectionDate
            .plusDays(offset.toLong())
            .atTime(HOUR, MINUTE)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (triggerAt <= System.currentTimeMillis()) return

        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_INVOICE_ID, item.id)
            .putExtra(EXTRA_OFFSET, offset)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(item.id, offset),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val canUseExact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

        // Falls back to an inexact idle-safe alarm if the exact-alarm permission is revoked
        // between the check and the call.
        try {
            if (canUseExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, id: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        OFFSETS.forEach { offset ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(id, offset),
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun requestCode(id: Long, offset: Int): Int =
        ((id * 2 + (offset + 1)) and 0x7FFFFFFF).toInt()

    const val EXTRA_INVOICE_ID = "invoice_id"
    const val EXTRA_OFFSET = "offset"
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val invoiceId = intent.getLongExtra(ReminderScheduler.EXTRA_INVOICE_ID, -1L)
        val offset = intent.getIntExtra(ReminderScheduler.EXTRA_OFFSET, 0)

        receiverScope.launch {
            try {
                if (invoiceId < 0) return@launch
                val item = AppDatabase.get(appContext).invoiceDao().get(invoiceId) ?: return@launch
                if (item.computedStatus() == PaymentStatus.PAID) return@launch
                showNotification(appContext, item, offset)
            } finally {
                pending.finish()
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun showNotification(context: Context, invoice: InvoiceEntity, offset: Int) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) return

    val title = if (offset == -1) {
        "Collection tomorrow: ${invoice.clinicName}"
    } else {
        "Collection today: ${invoice.clinicName}"
    }
    val status = stateLabel(collectionState(invoice))
    val summary = "${aed(invoice.dueAmountMinor)} - ${invoice.invoiceNumber}"
    val expanded = buildString {
        append(invoice.clinicName).append('\n')
        append("Amount: ").append(aed(invoice.dueAmountMinor)).append('\n')
        append("Invoice: ").append(invoice.invoiceNumber).append('\n')
        append("Collection date: ").append(formatDate(invoice.collectionDate)).append('\n')
        append("Due date: ").append(formatDate(invoice.dueDate)).append('\n')
        append("Status: ").append(status)
    }

    val openIntent = Intent(context, MainActivity::class.java)
        .putExtra(ReminderScheduler.EXTRA_INVOICE_ID, invoice.id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    val contentIntent = PendingIntent.getActivity(
        context,
        (invoice.id and 0x7FFFFFFF).toInt(),
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, ClinicApp.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(summary)
        .setStyle(NotificationCompat.BigTextStyle().bigText(expanded).setBigContentTitle(title))
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        // Channel settings win on API 26+, but these keep the intent explicit and
        // cover any OEM that falls back to builder-level defaults.
        .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
        .setVibrate(longArrayOf(0L, 400L, 200L, 400L))
        .setOnlyAlertOnce(false)
        .build()

    NotificationManagerCompat.from(context)
        .notify(((invoice.id * 10 + (offset + 1)) and 0x7FFFFFFF).toInt(), notification)
}

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        receiverScope.launch {
            try {
                AppDatabase.get(appContext).invoiceDao().getAllOnce()
                    .filter { it.computedStatus() != PaymentStatus.PAID }
                    .forEach { ReminderScheduler.schedule(appContext, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
