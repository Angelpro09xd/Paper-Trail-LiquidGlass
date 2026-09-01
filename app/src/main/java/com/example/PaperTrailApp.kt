package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.notifications.ReminderScheduler
import com.example.data.repository.VaultRepository
import com.example.data.security.BiometricAuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaperTrailApp : Application() {

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    // Prewarm database on background thread immediately at process start
    appScope.launch {
      try {
        database.vaultDao()
      } catch (e: Throwable) {
        // Fallback or initialization exception handled within AppDatabase.getInstance()
      }
    }

    try {
      ReminderScheduler.schedulePeriodicReminders(this)
    } catch (e: Throwable) {
      // Handled for test / in-memory harnesses
    }
  }
}
