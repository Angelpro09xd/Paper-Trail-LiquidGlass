package com.example.securevault.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CryptoLogLevel {
  INFO,
  HARDWARE,
  SUCCESS,
  WARNING,
  ERROR
}

data class CryptoLogEntry(
  val id: Long = System.nanoTime(),
  val timestamp: Long = System.currentTimeMillis(),
  val tag: String, // e.g., KEYSTORE, DEK_GEN, ENVELOPE_WRAP, AES_GCM_CHUNK, ZEROIZE, SAF_MOVE
  val message: String,
  val level: CryptoLogLevel = CryptoLogLevel.INFO
) {
  val formattedTime: String
    get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

object CryptoLogger {
  private val _logs = MutableStateFlow<List<CryptoLogEntry>>(emptyList())
  val logs: StateFlow<List<CryptoLogEntry>> = _logs.asStateFlow()

  private const val MAX_LOGS = 200

  fun log(tag: String, message: String, level: CryptoLogLevel = CryptoLogLevel.INFO) {
    val entry = CryptoLogEntry(
      tag = tag,
      message = message,
      level = level
    )
    val current = _logs.value.toMutableList()
    current.add(0, entry) // newest first
    if (current.size > MAX_LOGS) {
      _logs.value = current.take(MAX_LOGS)
    } else {
      _logs.value = current
    }
  }

  fun info(tag: String, message: String) = log(tag, message, CryptoLogLevel.INFO)
  fun hardware(tag: String, message: String) = log(tag, message, CryptoLogLevel.HARDWARE)
  fun success(tag: String, message: String) = log(tag, message, CryptoLogLevel.SUCCESS)
  fun warn(tag: String, message: String) = log(tag, message, CryptoLogLevel.WARNING)
  fun error(tag: String, message: String) = log(tag, message, CryptoLogLevel.ERROR)

  fun clearLogs() {
    _logs.value = emptyList()
  }
}
