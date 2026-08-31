package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.VaultItem
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
  entities = [VaultItem::class],
  version = 1,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun vaultDao(): VaultDao

  companion object {
    private const val DATABASE_NAME = "papertrail_encrypted_vault.db"

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
      }
    }

    private fun buildDatabase(appContext: Context): AppDatabase {
      val passphrase = DatabasePassphraseManager.getOrCreatePassphrase(appContext)
      val factory = SupportOpenHelperFactory(passphrase)

      return try {
        Room.databaseBuilder(appContext, AppDatabase::class.java, DATABASE_NAME)
          .openHelperFactory(factory)
          .fallbackToDestructiveMigration()
          .build()
      } catch (e: Throwable) {
        Room.databaseBuilder(appContext, AppDatabase::class.java, DATABASE_NAME)
          .fallbackToDestructiveMigration()
          .build()
      }
    }
  }
}
