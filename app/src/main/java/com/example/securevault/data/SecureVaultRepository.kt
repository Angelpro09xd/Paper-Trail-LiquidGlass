package com.example.securevault.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.example.securevault.model.SecureFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream

sealed interface DecryptionResult {
  data class Success(val bytes: ByteArray) : DecryptionResult
  data class TooLargeToPreview(val fileSizeBytes: Long) : DecryptionResult
}

class SecureVaultRepository(
  private val dao: SecureVaultDao,
  private val context: Context
) {
  private val TAG = "SecureVaultRepository"

  companion object {
    const val MAX_PREVIEW_SIZE_BYTES = 80L * 1024 * 1024 // 80 MB safety threshold for volatile preview
  }

  private val vaultDir: File by lazy {
    File(context.filesDir, "securevault").apply {
      if (!exists()) {
        mkdirs()
      }
    }
  }

  val allFiles: Flow<List<SecureFileItem>> = dao.getAllFiles()

  suspend fun importFile(
    uri: Uri,
    cipher: Cipher
  ): Result<SecureFileItem> = withContext(Dispatchers.IO) {
    var blobFile: File? = null
    try {
      // 1. Resolve original filename and MIME type from URI metadata
      var fileName = "secure_document"
      var fileSize: Long = -1

      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (nameIndex != -1) {
            val name = cursor.getString(nameIndex)
            if (!name.isNullOrBlank()) fileName = name
          }
          if (sizeIndex != -1) {
            fileSize = cursor.getLong(sizeIndex)
          }
        }
      }

      val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

      // 2. Capture IV from initialized cipher before streaming write
      val iv = cipher.iv
      val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)

      // 3. Save ciphertext directly to disk via CipherOutputStream with a fixed small buffer (constant memory)
      val blobName = UUID.randomUUID().toString()
      val targetBlobFile = File(vaultDir, blobName)
      blobFile = targetBlobFile

      context.contentResolver.openInputStream(uri)?.use { input ->
        CipherOutputStream(FileOutputStream(targetBlobFile), cipher).use { cipherOut ->
          val buffer = ByteArray(8192)
          var bytesRead: Int
          while (input.read(buffer).also { bytesRead = it } != -1) {
            cipherOut.write(buffer, 0, bytesRead)
          }
          cipherOut.flush()
        }
      } ?: return@withContext Result.failure(IllegalStateException("Could not read file from URI"))

      val actualSize = if (fileSize > 0) fileSize else targetBlobFile.length()

      // 4. Store metadata in the encrypted SecureVault database
      val item = SecureFileItem(
        originalFileName = fileName,
        mimeType = mimeType,
        fileSizeBytes = actualSize,
        encryptedBlobPath = blobName,
        dateAdded = System.currentTimeMillis(),
        iv = ivB64
      )

      val generatedId = dao.insertFile(item)
      val savedItem = item.copy(id = generatedId)
      Log.i(TAG, "Successfully imported and encrypted file $fileName ($actualSize bytes) into SecureVault")
      Result.success(savedItem)
    } catch (e: Throwable) {
      blobFile?.let {
        if (it.exists()) {
          it.delete()
        }
      }
      Log.e(TAG, "Error importing file to SecureVault: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun decryptFile(
    item: SecureFileItem,
    cipher: Cipher,
    maxPreviewSizeBytes: Long = MAX_PREVIEW_SIZE_BYTES
  ): Result<DecryptionResult> = withContext(Dispatchers.IO) {
    try {
      val blobFile = File(vaultDir, item.encryptedBlobPath)
      if (!blobFile.exists()) {
        return@withContext Result.failure(IllegalStateException("Encrypted blob file does not exist on disk"))
      }

      // Check fast path if known metadata size exceeds max preview threshold
      if (item.fileSizeBytes > maxPreviewSizeBytes) {
        return@withContext Result.success(DecryptionResult.TooLargeToPreview(item.fileSizeBytes))
      }

      val baos = ByteArrayOutputStream()
      var isOversized = false
      var totalRead = 0L

      FileInputStream(blobFile).use { fis ->
        CipherInputStream(fis, cipher).use { cis ->
          val buffer = ByteArray(8192)
          var bytesRead: Int
          while (cis.read(buffer).also { bytesRead = it } != -1) {
            totalRead += bytesRead
            if (totalRead > maxPreviewSizeBytes) {
              isOversized = true
              break
            }
            baos.write(buffer, 0, bytesRead)
          }
        }
      }

      if (isOversized) {
        val reportedSize = if (item.fileSizeBytes > 0) item.fileSizeBytes else totalRead
        Result.success(DecryptionResult.TooLargeToPreview(reportedSize))
      } else {
        Result.success(DecryptionResult.Success(baos.toByteArray()))
      }
    } catch (e: Throwable) {
      Log.e(TAG, "Decryption failed for item ${item.id}: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun deleteFile(item: SecureFileItem) = withContext(Dispatchers.IO) {
    try {
      val blobFile = File(vaultDir, item.encryptedBlobPath)
      if (blobFile.exists()) {
        blobFile.delete()
      }
      dao.deleteFile(item)
      Log.i(TAG, "Deleted file ${item.id} and removed encrypted blob.")
    } catch (e: Throwable) {
      Log.e(TAG, "Error deleting file ${item.id}: ${e.message}", e)
    }
  }
}
