package com.example.securevault.pdf

import android.os.ParcelFileDescriptor
import android.os.SharedMemory
import android.system.OsConstants
import com.example.securevault.logging.CryptoLogger
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * Encapsulates an in-memory, RAM-backed seekable ParcelFileDescriptor generated from
 * decrypted PDF bytes using android.os.SharedMemory.
 *
 * This allows PdfRenderer to open and seek through the document without ever persisting
 * decrypted bytes to disk or exposing file handles outside the app.
 */
class SharedMemoryPdfSource private constructor(
  val sharedMemory: SharedMemory,
  val parcelFileDescriptor: ParcelFileDescriptor,
  val size: Int
) : Closeable {

  private var isClosed = false

  override fun close() {
    if (!isClosed) {
      isClosed = true
      try {
        parcelFileDescriptor.close()
      } catch (_: Exception) {}
      try {
        sharedMemory.close()
      } catch (_: Exception) {}
      CryptoLogger.hardware("SHARED_MEM", "SharedMemory PDF region closed and volatile RAM released.")
    }
  }

  companion object {
    /**
     * Allocates an anonymous SharedMemory region in RAM, writes decrypted PDF bytes into it,
     * sets read-only protection, and returns a seekable ParcelFileDescriptor.
     */
    fun create(bytes: ByteArray): SharedMemoryPdfSource {
      require(bytes.isNotEmpty()) { "Cannot create SharedMemory from empty byte array" }
      
      val sharedMemory = SharedMemory.create("securevault_pdf_preview", bytes.size)
      var buffer: ByteBuffer? = null
      try {
        buffer = sharedMemory.mapReadWrite()
        buffer.put(bytes)
      } finally {
        if (buffer != null) {
          SharedMemory.unmap(buffer)
        }
      }

      // Restrict access to read-only
      sharedMemory.setProtect(OsConstants.PROT_READ)

      val pfd: ParcelFileDescriptor = try {
        // Try getFdDup() which returns a duplicated ParcelFileDescriptor
        val getFdDupMethod = sharedMemory.javaClass.getMethod("getFdDup")
        getFdDupMethod.invoke(sharedMemory) as ParcelFileDescriptor
      } catch (_: Throwable) {
        try {
          // Try getFileDescriptor()
          val getFdMethod = sharedMemory.javaClass.getMethod("getFileDescriptor")
          val fd = getFdMethod.invoke(sharedMemory) as java.io.FileDescriptor
          ParcelFileDescriptor.dup(fd)
        } catch (_: Throwable) {
          // Fallback to internal mFileDescriptor field
          val field = sharedMemory.javaClass.getDeclaredField("mFileDescriptor").apply { isAccessible = true }
          val fd = field.get(sharedMemory) as java.io.FileDescriptor
          ParcelFileDescriptor.dup(fd)
        }
      }

      CryptoLogger.hardware(
        "SHARED_MEM",
        "Allocated ${bytes.size} bytes in SharedMemory with seekable ParcelFileDescriptor (Zero disk footprint)"
      )
      return SharedMemoryPdfSource(sharedMemory, pfd, bytes.size)
    }
  }
}
