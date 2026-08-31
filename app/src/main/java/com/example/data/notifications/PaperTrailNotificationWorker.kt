package com.example.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class PaperTrailNotificationWorker(
  private val context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    createNotificationChannel()

    try {
      val database = AppDatabase.getInstance(context)
      val dao = database.vaultDao()

      val now = System.currentTimeMillis()
      val maxLookahead = now + TimeUnit.DAYS.toMillis(14)

      // 1. Check expiring warranties
      val expiringWarranties = dao.getWarrantiesExpiringBetween(now, maxLookahead)
      for (item in expiringWarranties) {
        val daysLeft = item.daysUntilWarrantyExpires(now) ?: continue
        if (daysLeft in 0..item.reminderDaysBefore) {
          sendNotification(
            notificationId = ("warranty_${item.id}").hashCode(),
            title = "Warranty Expiring Soon: ${item.storeName}",
            message = if (daysLeft == 0L) {
              "The warranty for ${item.storeName} expires TODAY."
            } else {
              "The warranty for ${item.storeName} expires in $daysLeft day${if (daysLeft > 1) "s" else ""}."
            }
          )
        }
      }

      // 2. Check upcoming subscription renewals
      val upcomingSubscriptions = dao.getSubscriptionsRenewingBetween(now, maxLookahead)
      val currencyFmt = NumberFormat.getCurrencyInstance(Locale.US)
      for (item in upcomingSubscriptions) {
        val daysLeft = item.daysUntilSubscriptionRenews(now) ?: continue
        if (daysLeft in 0..item.reminderDaysBefore) {
          val costStr = currencyFmt.format(item.amount)
          sendNotification(
            notificationId = ("sub_${item.id}").hashCode(),
            title = "Upcoming Subscription Renewal: ${item.storeName}",
            message = if (daysLeft == 0L) {
              "${item.storeName} ($costStr) renews TODAY."
            } else {
              "${item.storeName} ($costStr) will renew in $daysLeft day${if (daysLeft > 1) "s" else ""}."
            }
          )
        }
      }

      return Result.success()
    } catch (e: Exception) {
      e.printStackTrace()
      return Result.retry()
    }
  }

  private fun createNotificationChannel() {
    val name = "Vault Reminders"
    val descriptionText = "Notifications for upcoming warranty expirations and subscription renewals"
    val importance = NotificationManager.IMPORTANCE_DEFAULT
    val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
      description = descriptionText
    }
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
  }

  private fun sendNotification(notificationId: Int, title: String, message: String) {
    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    val pendingIntent = PendingIntent.getActivity(
      context,
      notificationId,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_agenda)
      .setContentTitle(title)
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    try {
      NotificationManagerCompat.from(context).notify(notificationId, notification)
    } catch (e: SecurityException) {
      // Notification permission not granted
    }
  }

  companion object {
    const val CHANNEL_ID = "papertrail_vault_reminders_channel"
  }
}
