package com.example.securevault.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import com.example.securevault.logging.CryptoLogger
import java.io.File

object SafeFileMoveHelper {
  private const val TAG = "SafeFileMoveHelper"

  /**
   * Attempts to delete the source document after it has been safely encrypted into SecureVault.
   * Supports DocumentsContract URIs, content URIs, and direct file paths.
   */
  fun deleteSourceFile(context: Context, uri: Uri): Boolean {
    val cr: ContentResolver = context.contentResolver

    // 1. If it's a file:// scheme URI
    if (uri.scheme == "file") {
      try {
        val path = uri.path
        if (path != null) {
          val file = File(path)
          if (file.exists() && file.delete()) {
            CryptoLogger.success("FILE_MOVE", "Source file wiped from storage: ${file.name}")
            return true
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed file scheme delete: ${e.message}")
      }
    }

    // 2. If it's a DocumentsContract document URI
    if (DocumentsContract.isDocumentUri(context, uri)) {
      try {
        val deleted = DocumentsContract.deleteDocument(cr, uri)
        if (deleted) {
          CryptoLogger.success("FILE_MOVE", "SAF original document deleted: $uri")
          return true
        }
      } catch (e: Exception) {
        Log.w(TAG, "DocumentsContract.deleteDocument failed: ${e.message}")
      }
    }

    // 3. Standard ContentResolver.delete
    try {
      val rows = cr.delete(uri, null, null)
      if (rows > 0) {
        CryptoLogger.success("FILE_MOVE", "Content provider source deleted ($rows rows affected)")
        return true
      }
    } catch (e: Exception) {
      Log.w(TAG, "ContentResolver.delete failed: ${e.message}")
    }

    CryptoLogger.warn("FILE_MOVE", "Source file delete permission not granted by provider for: $uri (Original kept)")
    return false
  }
}
