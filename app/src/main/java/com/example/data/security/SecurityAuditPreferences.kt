package com.example.data.security

import android.content.Context
import android.content.SharedPreferences

object SecurityAuditPreferences {
  private const val PREFS_NAME = "security_audit_prefs"
  private const val KEY_STRICT_GATE_ENABLED = "strict_gate_enabled"
  private const val KEY_LAST_AUDIT_TIME = "last_audit_time"

  private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun isStrictGateEnabled(context: Context): Boolean {
    // Enabled by default so users receive transparent hardware alerts if on a broken/permissive ROM
    return getPrefs(context).getBoolean(KEY_STRICT_GATE_ENABLED, true)
  }

  fun setStrictGateEnabled(context: Context, enabled: Boolean) {
    getPrefs(context).edit().putBoolean(KEY_STRICT_GATE_ENABLED, enabled).apply()
  }

  fun getLastAuditTime(context: Context): Long {
    return getPrefs(context).getLong(KEY_LAST_AUDIT_TIME, 0L)
  }

  fun setLastAuditTime(context: Context, time: Long) {
    getPrefs(context).edit().putLong(KEY_LAST_AUDIT_TIME, time).apply()
  }
}
