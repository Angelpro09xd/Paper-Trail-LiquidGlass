package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.notifications.ReminderScheduler
import com.example.data.repository.VaultRepository
import com.example.data.security.BiometricAuthManager

class PaperTrailApp : Application() {

  val database: AppDatabase by lazy {
    AppDatabase.getInstance(this)
  }

  val repository: VaultRepository by lazy {
    VaultRepository(database.vaultDao(), this)
  }

  val authManager: BiometricAuthManager by lazy {
    BiometricAuthManager(this)
  }

  override fun onCreate() {
    super.onCreate()
    try {
      ReminderScheduler.schedulePeriodicReminders(this)
    } catch (e: Throwable) {
      // Handled for test / in-memory harnesses
    }
  }
}
