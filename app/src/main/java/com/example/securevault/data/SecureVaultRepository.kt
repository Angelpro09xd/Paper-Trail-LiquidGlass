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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import javax.crypto.Cipher

class SecureVaultRepository(
  private val dao: SecureVaultDao,
  private val context: Context
) {
  private val TAG = "SecureVaultRepository"
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

      // 2. Read raw bytes from input stream
      val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return@withContext Result.failure(IllegalStateException("Could not read file from URI"))

      val actualSize = if (fileSize > 0) fileSize else rawBytes.size.toLong()

      // 3. Encrypt raw bytes with the hardware-backed AES-GCM Cipher
      val ciphertext = cipher.doFinal(rawBytes)
      val iv = cipher.iv
      val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)

      // 4. Save ciphertext to an opaque, UUID-named file on disk
      val blobName = UUID.randomUUID().toString()
      val blobFile = File(vaultDir, blobName)
      FileOutputStream(blobFile).use { fos ->
        fos.write(ciphertext)
        fos.flush()
      }

      // 5. Store metadata in the encrypted SecureVault database
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
      Log.e(TAG, "Error importing file to SecureVault: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun decryptFile(
    item: SecureFileItem,
    cipher: Cipher
  ): Result<ByteArray> = withContext(Dispatchers.IO) {
    try {
      val blobFile = File(vaultDir, item.encryptedBlobPath)
      if (!blobFile.exists()) {
        return@withContext Result.failure(IllegalStateException("Encrypted blob file does not exist on disk"))
      }

      val ciphertext = FileInputStream(blobFile).use { it.readBytes() }
      val decryptedBytes = cipher.doFinal(ciphertext)
      Result.success(decryptedBytes)
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
