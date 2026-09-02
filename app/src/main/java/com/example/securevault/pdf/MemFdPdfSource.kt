package com.example.securevault.pdf

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.example.securevault.logging.CryptoLogger
import java.io.Closeable
import java.io.FileDescriptor

/**
 * Encapsulates an anonymous, seekable, RAM-backed FileDescriptor created via POSIX memfd_create.
 *
 * This provides PdfRenderer with a seekable ParcelFileDescriptor without ever touching
 * persistent storage, requiring zero reflection and relying purely on public Android APIs (API 30+).
 */
class MemFdPdfSource private constructor(
  private val fileDescriptor: FileDescriptor,
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
        Os.close(fileDescriptor)
      } catch (_: Exception) {}
      CryptoLogger.hardware("MEMFD", "Anonymous memfd PDF region closed and RAM released.")
    }
  }

  companion object {
    /**
     * Creates an anonymous memory-backed file descriptor, writes the decrypted PDF bytes
     * directly into RAM, rewinds the seek position to 0, and returns a seekable ParcelFileDescriptor.
     */
    fun create(bytes: ByteArray): MemFdPdfSource {
      require(bytes.isNotEmpty()) { "Cannot create memfd from empty byte array" }

      val fd: FileDescriptor = Os.memfd_create("securevault_pdf_preview", 0)

      // Write the decrypted bytes into the anonymous memory-backed file
      ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(fd)).use { out ->
        out.write(bytes)
        out.flush()
      }

      // Seek back to the start so PdfRenderer reads from byte 0
      Os.lseek(fd, 0, OsConstants.SEEK_SET)

      val pfd = ParcelFileDescriptor.dup(fd)

      CryptoLogger.hardware(
        "MEMFD",
        "Allocated ${bytes.size} bytes via memfd_create with seekable descriptor (zero disk footprint)"
      )
      return MemFdPdfSource(fd, pfd, bytes.size)
    }
  }
}
