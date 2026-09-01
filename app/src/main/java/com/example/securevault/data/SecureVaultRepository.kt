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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

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
    private const val DEK_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
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
    cipher: Cipher,
    onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
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

      // 2. Generate a random 256-bit software AES Data Encryption Key (DEK)
      val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
      val dek = keyGen.generateKey()
      val rawDekBytes = dek.encoded

      // 3. Wrap (encrypt) the DEK using the hardware Keystore-backed cipher
      val wrappedDekBytes = cipher.doFinal(rawDekBytes)
      val wrappedDekB64 = Base64.encodeToString(wrappedDekBytes, Base64.NO_WRAP)
      val dekWrapIv = cipher.iv
      val dekIvB64 = if (dekWrapIv != null) Base64.encodeToString(dekWrapIv, Base64.NO_WRAP) else ""

      // 4. Initialize software AES-GCM cipher with DEK for high-throughput bulk content stream
      val dekCipher = Cipher.getInstance(DEK_TRANSFORMATION)
      dekCipher.init(Cipher.ENCRYPT_MODE, dek)
      val contentIv = dekCipher.iv
      val contentIvB64 = Base64.encodeToString(contentIv, Base64.NO_WRAP)

      // Immediately zero out the raw DEK byte array from memory
      rawDekBytes.fill(0)

      // 5. Stream-encrypt content directly to disk via CipherOutputStream with high-throughput tiered buffering
      val blobName = UUID.randomUUID().toString()
      val targetBlobFile = File(vaultDir, blobName)
      blobFile = targetBlobFile

      // Adapt buffer size: 128KB for >50MB files (movies, game APKs), 64KB for >5MB, 16KB for smaller items
      val bufferSize = when {
        fileSize > 50L * 1024 * 1024 -> 128 * 1024
        fileSize > 5L * 1024 * 1024 -> 64 * 1024
        else -> 16 * 1024
      }

      var totalBytesWritten = 0L
      context.contentResolver.openInputStream(uri)?.let { BufferedInputStream(it, bufferSize) }?.use { input ->
        CipherOutputStream(BufferedOutputStream(FileOutputStream(targetBlobFile), bufferSize), dekCipher).use { cipherOut ->
          val buffer = ByteArray(bufferSize)
          var bytesRead: Int
          while (input.read(buffer).also { bytesRead = it } != -1) {
            cipherOut.write(buffer, 0, bytesRead)
            totalBytesWritten += bytesRead
            onProgress(totalBytesWritten, fileSize)
          }
          cipherOut.flush()
        }
      } ?: return@withContext Result.failure(IllegalStateException("Could not read file from URI"))

      val actualSize = if (fileSize > 0) fileSize else targetBlobFile.length()

      // 6. Store metadata with wrapped DEK and respective IVs in the encrypted database
      val item = SecureFileItem(
        originalFileName = fileName,
        mimeType = mimeType,
        fileSizeBytes = actualSize,
        encryptedBlobPath = blobName,
        dateAdded = System.currentTimeMillis(),
        iv = contentIvB64,
        wrappedDek = wrappedDekB64,
        dekIv = dekIvB64
      )

      val generatedId = dao.insertFile(item)
      val savedItem = item.copy(id = generatedId)
      Log.i(TAG, "Successfully imported and envelope-encrypted file $fileName ($actualSize bytes) into SecureVault")
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

  suspend fun exportFile(
    item: SecureFileItem,
    destinationUri: Uri,
    cipher: Cipher,
    onProgress: (bytesExported: Long, totalBytes: Long) -> Unit = { _, _ -> }
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val blobFile = File(vaultDir, item.encryptedBlobPath)
      if (!blobFile.exists()) {
        return@withContext Result.failure(IllegalStateException("Encrypted blob file does not exist on disk"))
      }

      val streamCipher: Cipher
      var unwrappedDekBytesToWipe: ByteArray? = null

      try {
        if (item.wrappedDek.isNotEmpty()) {
          val wrappedDekBytes = Base64.decode(item.wrappedDek, Base64.NO_WRAP)
          val unwrappedDekBytes = cipher.doFinal(wrappedDekBytes)
          unwrappedDekBytesToWipe = unwrappedDekBytes
          val dekKey = SecretKeySpec(unwrappedDekBytes, "AES")

          val contentIvBytes = Base64.decode(item.iv, Base64.NO_WRAP)
          val dekCipher = Cipher.getInstance(DEK_TRANSFORMATION)
          val spec = GCMParameterSpec(GCM_TAG_LENGTH, contentIvBytes)
          dekCipher.init(Cipher.DECRYPT_MODE, dekKey, spec)
          streamCipher = dekCipher
        } else {
          streamCipher = cipher
        }

        val bufferSize = when {
          item.fileSizeBytes > 50L * 1024 * 1024 -> 128 * 1024
          item.fileSizeBytes > 5L * 1024 * 1024 -> 64 * 1024
          else -> 16 * 1024
        }

        var totalBytesWritten = 0L
        context.contentResolver.openOutputStream(destinationUri)?.let { BufferedOutputStream(it, bufferSize) }?.use { output ->
          BufferedInputStream(FileInputStream(blobFile), bufferSize).use { fis ->
            CipherInputStream(fis, streamCipher).use { cis ->
              val buffer = ByteArray(bufferSize)
              var bytesRead: Int
              while (cis.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalBytesWritten += bytesRead
                onProgress(totalBytesWritten, item.fileSizeBytes)
              }
              output.flush()
            }
          }
        } ?: return@withContext Result.failure(IllegalStateException("Could not open destination output stream"))

        Log.i(TAG, "Successfully exported file ${item.originalFileName} ($totalBytesWritten bytes)")
        Result.success(Unit)
      } finally {
        unwrappedDekBytesToWipe?.fill(0)
      }
    } catch (e: Throwable) {
      Log.e(TAG, "Export failed for item ${item.id}: ${e.message}", e)
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

      val streamCipher: Cipher
      var unwrappedDekBytesToWipe: ByteArray? = null

      try {
        if (item.wrappedDek.isNotEmpty()) {
          // 1. Unwrap DEK using the authenticated hardware Keystore cipher
          val wrappedDekBytes = Base64.decode(item.wrappedDek, Base64.NO_WRAP)
          val unwrappedDekBytes = cipher.doFinal(wrappedDekBytes)
          unwrappedDekBytesToWipe = unwrappedDekBytes
          val dekKey = SecretKeySpec(unwrappedDekBytes, "AES")

          // 2. Initialize software AES-GCM cipher with DEK and content IV for bulk streaming
          val contentIvBytes = Base64.decode(item.iv, Base64.NO_WRAP)
          val dekCipher = Cipher.getInstance(DEK_TRANSFORMATION)
          val spec = GCMParameterSpec(GCM_TAG_LENGTH, contentIvBytes)
          dekCipher.init(Cipher.DECRYPT_MODE, dekKey, spec)
          streamCipher = dekCipher
        } else {
          // Legacy fallback for entries encrypted directly with hardware Keystore
          streamCipher = cipher
        }

        val baos = ByteArrayOutputStream()
        var isOversized = false
        var totalRead = 0L

        // Adapt buffer size: 64KB for large files to maximize flash storage throughput, 16KB for smaller items
        val bufferSize = if (item.fileSizeBytes > 5L * 1024 * 1024) 64 * 1024 else 16 * 1024

        BufferedInputStream(FileInputStream(blobFile), bufferSize).use { fis ->
          CipherInputStream(fis, streamCipher).use { cis ->
            val buffer = ByteArray(bufferSize)
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
      } finally {
        // Discard and securely zero out unwrapped DEK bytes immediately
        unwrappedDekBytesToWipe?.fill(0)
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
