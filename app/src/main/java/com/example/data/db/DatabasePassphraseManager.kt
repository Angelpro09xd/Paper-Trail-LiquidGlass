package com.example.data.db

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object DatabasePassphraseManager {
  private const val TAG = "DatabasePassphraseMgr"
  private const val PREFS_FILE = "papertrail_secure_vault_prefs"
  private const val KEY_DB_PASSPHRASE = "vault_db_encryption_key_v1"

  @Volatile
  var isFallbackMode: Boolean = false
    private set

  fun getOrCreatePassphrase(context: Context): ByteArray {
    val prefs = getEncryptedPrefs(context)
    val existingKeyB64 = prefs.getString(KEY_DB_PASSPHRASE, null)
    if (existingKeyB64 != null) {
      return Base64.decode(existingKeyB64, Base64.NO_WRAP)
    }

    // Generate random 256-bit (32-byte) key
    val random = SecureRandom()
    val newKey = ByteArray(32)
    random.nextBytes(newKey)

    val encoded = Base64.encodeToString(newKey, Base64.NO_WRAP)
    prefs.edit().putString(KEY_DB_PASSPHRASE, encoded).apply()
    return newKey
  }

  private fun getEncryptedPrefs(context: Context): SharedPreferences {
    return try {
      val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

      EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
      ).also {
        isFallbackMode = false
      }
    } catch (e: Exception) {
      Log.w(TAG, "EncryptedSharedPreferences unavailable: ${e.message}. Using private SharedPreferences fallback.")
      isFallbackMode = true
      // Fallback for JVM test environments or older devices without Android Keystore support
      context.getSharedPreferences(PREFS_FILE + "_fallback", Context.MODE_PRIVATE)
    }
  }
}
