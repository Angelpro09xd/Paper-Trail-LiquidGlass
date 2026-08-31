package com.example.data.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {
  private const val PERIODIC_WORK_TAG = "papertrail_periodic_reminders"
  private const val ONE_TIME_WORK_TAG = "papertrail_immediate_reminder_check"

  fun schedulePeriodicReminders(context: Context) {
    try {
      val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

      // Flexible periodic request: Runs every 24 hours with a 6-hour flex interval for OEM battery optimization
      val periodicWork = PeriodicWorkRequestBuilder<PaperTrailNotificationWorker>(
        24, TimeUnit.HOURS,
        6, TimeUnit.HOURS
      )
        .setConstraints(constraints)
        .addTag(PERIODIC_WORK_TAG)
        .build()

      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        PERIODIC_WORK_TAG,
        ExistingPeriodicWorkPolicy.KEEP,
        periodicWork
      )
    } catch (e: Throwable) {
      e.printStackTrace()
    }
  }

  fun triggerImmediateCheck(context: Context) {
    try {
      val oneTimeWork = OneTimeWorkRequestBuilder<PaperTrailNotificationWorker>()
        .addTag(ONE_TIME_WORK_TAG)
        .build()

      WorkManager.getInstance(context).enqueueUniqueWork(
        ONE_TIME_WORK_TAG,
        ExistingWorkPolicy.REPLACE,
        oneTimeWork
      )
    } catch (e: Throwable) {
      e.printStackTrace()
    }
  }
}
