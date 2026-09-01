package com.example.data

import android.content.Context
import android.content.SharedPreferences

object TutorialPreferences {
  private const val PREFS_NAME = "papertrail_tutorial_prefs"
  private const val KEY_HAS_SEEN_TUTORIAL = "has_seen_onboarding"

  private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun hasSeenTutorial(context: Context): Boolean {
    return getPrefs(context).getBoolean(KEY_HAS_SEEN_TUTORIAL, false)
  }

  fun setTutorialSeen(context: Context, seen: Boolean = true) {
    getPrefs(context).edit().putBoolean(KEY_HAS_SEEN_TUTORIAL, seen).apply()
  }
}
