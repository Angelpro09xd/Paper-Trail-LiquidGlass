package com.example.securevault.media

import android.net.Uri
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.example.securevault.data.SecureVaultRepository
import com.example.securevault.logging.CryptoLogger
import com.example.securevault.model.SecureFileItem
import java.io.BufferedInputStream
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Media3 DataSource implementation that stream-decrypts video and audio media on-the-fly
 * without ever persisting decrypted bytes to disk or holding the complete decrypted file in RAM.
 *
 * AES-GCM in CTR-like chunked streaming mode supports seeking by discarding decrypted bytes/chunks
 * up to [DataSpec.position].
 */
class SecureVaultDecryptingDataSource(
  private val repository: SecureVaultRepository,
  private val item: SecureFileItem,
  private val authorizedCipher: Cipher?
) : BaseDataSource(/* isNetwork = */ false) {

  private var uri: Uri? = null
  private var decryptedStream: InputStream? = null
  private var bytesRemaining: Long = 0L
  private var opened = false

  override fun open(dataSpec: DataSpec): Long {
    uri = dataSpec.uri
    transferInitializing(dataSpec)

    val cipher = authorizedCipher ?: throw IllegalStateException("No authorized biometric cipher provided for media playback")

    val blobHandle = repository.findBlobHandle(item.encryptedBlobPath)
      ?: throw IllegalStateException("Encrypted media blob not found: ${item.encryptedBlobPath}")
    if (!blobHandle.exists) {
      throw IllegalStateException("Encrypted media blob does not exist on disk")
    }

    val rawInput = blobHandle.openInputStream()
      ?: throw IllegalStateException("Failed to open input stream for media blob: ${item.encryptedBlobPath}")
    val bis = BufferedInputStream(rawInput, 64 * 1024)

    // Reuse Keystore unwrapping logic directly from repository
    val unwrappedDekBytes = repository.unwrapDek(item, cipher)
    val dekKey = if (unwrappedDekBytes != null) SecretKeySpec(unwrappedDekBytes, "AES") else null
    val contentIvBytes = if (item.iv.isNotEmpty()) Base64.decode(item.iv, Base64.NO_WRAP) else null

    val header = ByteArray(4)
    bis.mark(4)
    val headerRead = bis.read(header)
    val isChunked = headerRead == 4 && header.contentEquals(SecureVaultRepository.CHUNK_MAGIC)

    val stream: InputStream = if (isChunked && dekKey != null && contentIvBytes != null) {
      SecureVaultChunkedDecryptedStream(bis, dekKey, contentIvBytes, unwrappedDekBytes)
    } else {
      bis.reset()
      val actualStreamCipher: Cipher = if (dekKey != null && contentIvBytes != null) {
        val spec = GCMParameterSpec(SecureVaultRepository.GCM_TAG_LENGTH, contentIvBytes)
        Cipher.getInstance(SecureVaultRepository.DEK_TRANSFORMATION).apply {
          init(Cipher.DECRYPT_MODE, dekKey, spec)
        }
      } else {
        cipher
      }
      CipherInputStream(bis, actualStreamCipher)
    }

    // AES-GCM chunked/streaming seek support:
    // Sequential-first approach that seeks by skipping/discarding decrypted stream bytes up to dataSpec.position.
    if (dataSpec.position > 0) {
      var skippedTotal = 0L
      while (skippedTotal < dataSpec.position) {
        val skipped = stream.skip(dataSpec.position - skippedTotal)
        if (skipped <= 0) {
          val temp = ByteArray(minOf(8192L, dataSpec.position - skippedTotal).toInt())
          val read = stream.read(temp)
          if (read == -1) break
          skippedTotal += read
        } else {
          skippedTotal += skipped
        }
      }
    }

    decryptedStream = stream
    opened = true
    transferStarted(dataSpec)

    bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
      dataSpec.length
    } else if (item.fileSizeBytes > 0) {
      val remaining = item.fileSizeBytes - dataSpec.position
      if (remaining > 0) remaining else C.LENGTH_UNSET.toLong()
    } else {
      C.LENGTH_UNSET.toLong()
    }

    CryptoLogger.info("MEDIA_STREAM", "Streaming decrypted media '${item.originalFileName}' at offset ${dataSpec.position}")
    return bytesRemaining
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length == 0) return 0
    if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

    val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
      length
    } else {
      minOf(length.toLong(), bytesRemaining).toInt()
    }

    val stream = decryptedStream ?: return C.RESULT_END_OF_INPUT
    val bytesRead = stream.read(buffer, offset, bytesToRead)
    if (bytesRead == -1) {
      return C.RESULT_END_OF_INPUT
    }

    if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
      bytesRemaining -= bytesRead
    }
    bytesTransferred(bytesRead)
    return bytesRead
  }

  override fun getUri(): Uri? = uri

  override fun close() {
    uri = null
    try {
      decryptedStream?.close()
    } catch (_: Exception) {}
    decryptedStream = null
    if (opened) {
      opened = false
      transferEnded()
    }
  }

  class Factory(
    private val repository: SecureVaultRepository,
    private val item: SecureFileItem,
    private val authorizedCipher: Cipher
  ) : DataSource.Factory {
    override fun createDataSource(): DataSource {
      return SecureVaultDecryptingDataSource(repository, item, authorizedCipher)
    }
  }
}

/**
 * Decrypted chunk-by-chunk InputStream that reads AES-256-GCM chunks (1MB each)
 * and purges previous plaintext blocks as playback progresses.
 */
private class SecureVaultChunkedDecryptedStream(
  private val underlyingStream: InputStream,
  private val dekKey: SecretKeySpec,
  private val contentIvBytes: ByteArray,
  private var rawDekBytesToWipe: ByteArray?
) : InputStream() {

  private var chunkIndex = 0L
  private var currentPlainChunk: ByteArray? = null
  private var currentChunkPos = 0
  private var isEof = false
  private val lenBuffer = ByteArray(4)

  private fun loadNextChunk(): Boolean {
    if (isEof) return false

    // Zero out old chunk from RAM before reading next chunk
    currentPlainChunk?.fill(0)
    currentPlainChunk = null
    currentChunkPos = 0

    var lenRead = 0
    while (lenRead < 4) {
      val r = underlyingStream.read(lenBuffer, lenRead, 4 - lenRead)
      if (r == -1) break
      lenRead += r
    }
    if (lenRead < 4) {
      isEof = true
      return false
    }

    val cipherLen = ((lenBuffer[0].toInt() and 0xFF) shl 24) or
                    ((lenBuffer[1].toInt() and 0xFF) shl 16) or
                    ((lenBuffer[2].toInt() and 0xFF) shl 8) or
                    (lenBuffer[3].toInt() and 0xFF)

    if (cipherLen <= 0 || cipherLen > SecureVaultRepository.CHUNK_SIZE + 1024) {
      isEof = true
      throw IllegalStateException("Corrupt chunk length in secure stream: $cipherLen")
    }

    val cipherBuffer = ByteArray(cipherLen)
    var cipherRead = 0
    while (cipherRead < cipherLen) {
      val r = underlyingStream.read(cipherBuffer, cipherRead, cipherLen - cipherRead)
      if (r == -1) {
        isEof = true
        throw java.io.EOFException("Unexpected EOF reading cipher chunk")
      }
      cipherRead += r
    }

    val chunkIv = SecureVaultRepository.deriveChunkIv(contentIvBytes, chunkIndex)
    val chunkCipher = Cipher.getInstance(SecureVaultRepository.DEK_TRANSFORMATION)
    chunkCipher.init(Cipher.DECRYPT_MODE, dekKey, GCMParameterSpec(SecureVaultRepository.GCM_TAG_LENGTH, chunkIv))
    currentPlainChunk = chunkCipher.doFinal(cipherBuffer)
    currentChunkPos = 0
    chunkIndex++
    return true
  }

  override fun read(): Int {
    if (currentPlainChunk == null || currentChunkPos >= currentPlainChunk!!.size) {
      if (!loadNextChunk()) return -1
    }
    val chunk = currentPlainChunk ?: return -1
    val b = chunk[currentChunkPos].toInt() and 0xFF
    currentChunkPos++
    return b
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    if (len == 0) return 0
    if (currentPlainChunk == null || currentChunkPos >= currentPlainChunk!!.size) {
      if (!loadNextChunk()) return -1
    }
    val chunk = currentPlainChunk ?: return -1
    val available = chunk.size - currentChunkPos
    val toCopy = minOf(len, available)
    System.arraycopy(chunk, currentChunkPos, b, off, toCopy)
    currentChunkPos += toCopy
    return toCopy
  }

  override fun skip(n: Long): Long {
    if (n <= 0) return 0
    var remaining = n
    val skipBuf = ByteArray(minOf(remaining, 8192L).toInt())
    while (remaining > 0) {
      val toRead = minOf(remaining, skipBuf.size.toLong()).toInt()
      val read = read(skipBuf, 0, toRead)
      if (read == -1) break
      remaining -= read
    }
    return n - remaining
  }

  override fun close() {
    try {
      underlyingStream.close()
    } catch (_: Exception) {}
    currentPlainChunk?.fill(0)
    currentPlainChunk = null
    rawDekBytesToWipe?.fill(0)
    rawDekBytesToWipe = null
    CryptoLogger.hardware("ZEROIZE", "Media stream closed. Decrypted RAM buffers zeroized.")
  }
}
