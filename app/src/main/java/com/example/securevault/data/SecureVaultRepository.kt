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
    private const val CHUNK_SIZE = 1024 * 1024 // 1 MB chunking eliminates Java heap buffering OOM
    private val CHUNK_MAGIC = byteArrayOf('S'.code.toByte(), 'V'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())
  }

  private fun deriveChunkIv(baseIv: ByteArray, chunkIndex: Long): ByteArray {
    val iv = baseIv.copyOf()
    for (i in 0 until 8) {
      val shift = (7 - i) * 8
      val byteVal = ((chunkIndex ushr shift) and 0xFF).toByte()
      iv[4 + i] = (iv[4 + i].toInt() xor byteVal.toInt()).toByte()
    }
    return iv
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

      // 4. Software AES-GCM base IV generation for chunked streaming
      val dekCipherForIv = Cipher.getInstance(DEK_TRANSFORMATION)
      dekCipherForIv.init(Cipher.ENCRYPT_MODE, dek)
      val contentIv = dekCipherForIv.iv
      val contentIvB64 = Base64.encodeToString(contentIv, Base64.NO_WRAP)

      // Immediately zero out the raw DEK byte array from memory
      rawDekBytes.fill(0)

      // 5. Chunk-encrypt content directly to disk: 1MB chunks with individual AES-GCM authentication tags
      // Memory footprint is constant (~2MB) even for 1GB - 10GB movies or game APKs.
      val blobName = UUID.randomUUID().toString()
      val targetBlobFile = File(vaultDir, blobName)
      blobFile = targetBlobFile

      var totalBytesWritten = 0L
      var chunkIndex = 0L
      val plainBuffer = ByteArray(CHUNK_SIZE)

      FileOutputStream(targetBlobFile).use { fos ->
        BufferedOutputStream(fos, 64 * 1024).use { out ->
          // Write format magic header
          out.write(CHUNK_MAGIC)

          context.contentResolver.openInputStream(uri)?.let { BufferedInputStream(it, 64 * 1024) }?.use { input ->
            while (true) {
              var bytesReadThisChunk = 0
              while (bytesReadThisChunk < CHUNK_SIZE) {
                val r = input.read(plainBuffer, bytesReadThisChunk, CHUNK_SIZE - bytesReadThisChunk)
                if (r == -1) break
                bytesReadThisChunk += r
              }

              if (bytesReadThisChunk == 0) break // EOF

              val chunkIv = deriveChunkIv(contentIv, chunkIndex)
              val chunkCipher = Cipher.getInstance(DEK_TRANSFORMATION)
              chunkCipher.init(Cipher.ENCRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_LENGTH, chunkIv))
              val cipherBytes = chunkCipher.doFinal(plainBuffer, 0, bytesReadThisChunk)

              val len = cipherBytes.size
              out.write((len ushr 24) and 0xFF)
              out.write((len ushr 16) and 0xFF)
              out.write((len ushr 8) and 0xFF)
              out.write(len and 0xFF)
              out.write(cipherBytes)

              totalBytesWritten += bytesReadThisChunk
              chunkIndex++
              onProgress(totalBytesWritten, fileSize)
            }
            out.flush()
          } ?: return@withContext Result.failure(IllegalStateException("Could not read file from URI"))
        }
      }

      val actualSize = if (fileSize > 0) fileSize else totalBytesWritten

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

      var unwrappedDekBytesToWipe: ByteArray? = null

      try {
        val dekKey: SecretKeySpec?
        val contentIvBytes: ByteArray?
        val legacyStreamCipher: Cipher?

        if (item.wrappedDek.isNotEmpty()) {
          val wrappedDekBytes = Base64.decode(item.wrappedDek, Base64.NO_WRAP)
          val unwrappedDekBytes = cipher.doFinal(wrappedDekBytes)
          unwrappedDekBytesToWipe = unwrappedDekBytes
          dekKey = SecretKeySpec(unwrappedDekBytes, "AES")
          contentIvBytes = Base64.decode(item.iv, Base64.NO_WRAP)
          legacyStreamCipher = null
        } else {
          dekKey = null
          contentIvBytes = null
          legacyStreamCipher = cipher
        }

        var totalBytesWritten = 0L

        context.contentResolver.openOutputStream(destinationUri)?.let { BufferedOutputStream(it, 64 * 1024) }?.use { output ->
          BufferedInputStream(FileInputStream(blobFile), 64 * 1024).use { bis ->
            val header = ByteArray(4)
            bis.mark(4)
            val headerRead = bis.read(header)
            val isChunked = headerRead == 4 && header.contentEquals(CHUNK_MAGIC)

            if (isChunked && dekKey != null && contentIvBytes != null) {
              var chunkIndex = 0L
              val lenBuffer = ByteArray(4)

              while (true) {
                var lenRead = 0
                while (lenRead < 4) {
                  val r = bis.read(lenBuffer, lenRead, 4 - lenRead)
                  if (r == -1) break
                  lenRead += r
                }
                if (lenRead < 4) break // EOF

                val cipherLen = ((lenBuffer[0].toInt() and 0xFF) shl 24) or
                                ((lenBuffer[1].toInt() and 0xFF) shl 16) or
                                ((lenBuffer[2].toInt() and 0xFF) shl 8) or
                                (lenBuffer[3].toInt() and 0xFF)

                if (cipherLen <= 0 || cipherLen > CHUNK_SIZE + 1024) {
                  throw IllegalStateException("Corrupt chunk length: $cipherLen")
                }

                val cipherBuffer = ByteArray(cipherLen)
                var cipherRead = 0
                while (cipherRead < cipherLen) {
                  val r = bis.read(cipherBuffer, cipherRead, cipherLen - cipherRead)
                  if (r == -1) throw java.io.EOFException("Unexpected EOF reading cipher chunk")
                  cipherRead += r
                }

                val chunkIv = deriveChunkIv(contentIvBytes, chunkIndex)
                val chunkCipher = Cipher.getInstance(DEK_TRANSFORMATION)
                chunkCipher.init(Cipher.DECRYPT_MODE, dekKey, GCMParameterSpec(GCM_TAG_LENGTH, chunkIv))
                val plainBytes = chunkCipher.doFinal(cipherBuffer)

                output.write(plainBytes)
                totalBytesWritten += plainBytes.size
                chunkIndex++
                onProgress(totalBytesWritten, item.fileSizeBytes)
              }
            } else {
              // Legacy non-chunked fallback
              bis.reset()
              val actualStreamCipher: Cipher = legacyStreamCipher ?: run {
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, contentIvBytes!!)
                Cipher.getInstance(DEK_TRANSFORMATION).apply {
                  init(Cipher.DECRYPT_MODE, dekKey!!, spec)
                }
              }
              CipherInputStream(bis, actualStreamCipher).use { cis ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (cis.read(buffer).also { bytesRead = it } != -1) {
                  output.write(buffer, 0, bytesRead)
                  totalBytesWritten += bytesRead
                  onProgress(totalBytesWritten, item.fileSizeBytes)
                }
              }
            }
            output.flush()
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

      if (item.fileSizeBytes > maxPreviewSizeBytes) {
        return@withContext Result.success(DecryptionResult.TooLargeToPreview(item.fileSizeBytes))
      }

      var unwrappedDekBytesToWipe: ByteArray? = null

      try {
        val dekKey: SecretKeySpec?
        val contentIvBytes: ByteArray?
        val legacyStreamCipher: Cipher?

        if (item.wrappedDek.isNotEmpty()) {
          val wrappedDekBytes = Base64.decode(item.wrappedDek, Base64.NO_WRAP)
          val unwrappedDekBytes = cipher.doFinal(wrappedDekBytes)
          unwrappedDekBytesToWipe = unwrappedDekBytes
          dekKey = SecretKeySpec(unwrappedDekBytes, "AES")
          contentIvBytes = Base64.decode(item.iv, Base64.NO_WRAP)
          legacyStreamCipher = null
        } else {
          dekKey = null
          contentIvBytes = null
          legacyStreamCipher = cipher
        }

        val baos = ByteArrayOutputStream()
        var isOversized = false
        var totalRead = 0L

        BufferedInputStream(FileInputStream(blobFile), 64 * 1024).use { bis ->
          val header = ByteArray(4)
          bis.mark(4)
          val headerRead = bis.read(header)
          val isChunked = headerRead == 4 && header.contentEquals(CHUNK_MAGIC)

          if (isChunked && dekKey != null && contentIvBytes != null) {
            var chunkIndex = 0L
            val lenBuffer = ByteArray(4)

            while (true) {
              var lenRead = 0
              while (lenRead < 4) {
                val r = bis.read(lenBuffer, lenRead, 4 - lenRead)
                if (r == -1) break
                lenRead += r
              }
              if (lenRead < 4) break // EOF

              val cipherLen = ((lenBuffer[0].toInt() and 0xFF) shl 24) or
                              ((lenBuffer[1].toInt() and 0xFF) shl 16) or
                              ((lenBuffer[2].toInt() and 0xFF) shl 8) or
                              (lenBuffer[3].toInt() and 0xFF)

              if (cipherLen <= 0 || cipherLen > CHUNK_SIZE + 1024) {
                throw IllegalStateException("Corrupt chunk length: $cipherLen")
              }

              val cipherBuffer = ByteArray(cipherLen)
              var cipherRead = 0
              while (cipherRead < cipherLen) {
                val r = bis.read(cipherBuffer, cipherRead, cipherLen - cipherRead)
                if (r == -1) throw java.io.EOFException("Unexpected EOF reading chunk")
                cipherRead += r
              }

              val chunkIv = deriveChunkIv(contentIvBytes, chunkIndex)
              val chunkCipher = Cipher.getInstance(DEK_TRANSFORMATION)
              chunkCipher.init(Cipher.DECRYPT_MODE, dekKey, GCMParameterSpec(GCM_TAG_LENGTH, chunkIv))
              val plainBytes = chunkCipher.doFinal(cipherBuffer)

              totalRead += plainBytes.size
              if (totalRead > maxPreviewSizeBytes) {
                isOversized = true
                break
              }
              baos.write(plainBytes)
              chunkIndex++
            }
          } else {
            // Legacy fallback
            bis.reset()
            val actualStreamCipher: Cipher = legacyStreamCipher ?: run {
              val spec = GCMParameterSpec(GCM_TAG_LENGTH, contentIvBytes!!)
              Cipher.getInstance(DEK_TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, dekKey!!, spec)
              }
            }
            CipherInputStream(bis, actualStreamCipher).use { cis ->
              val buffer = ByteArray(64 * 1024)
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
        }

        if (isOversized) {
          val reportedSize = if (item.fileSizeBytes > 0) item.fileSizeBytes else totalRead
          Result.success(DecryptionResult.TooLargeToPreview(reportedSize))
        } else {
          Result.success(DecryptionResult.Success(baos.toByteArray()))
        }
      } finally {
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
