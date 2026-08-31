package com.example.data.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max

object ImageFileManager {
  private const val RECEIPT_DIR = "receipts"
  private const val CACHE_DIR = "receipts_cache"
  private const val MAX_OCR_DIMENSION = 1280 // Optimal resolution for ML Kit OCR without high RAM usage

  suspend fun decodeSampledBitmap(
    context: Context,
    uri: Uri,
    maxDimension: Int = MAX_OCR_DIMENSION
  ): Bitmap? = withContext(Dispatchers.IO) {
    try {
      // 1. Decode bounds first without allocating full bitmap in memory
      val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
      }
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
      }

      val rawWidth = options.outWidth
      val rawHeight = options.outHeight
      if (rawWidth <= 0 || rawHeight <= 0) return@withContext null

      // 2. Compute inSampleSize
      options.inSampleSize = calculateInSampleSize(rawWidth, rawHeight, maxDimension, maxDimension)
      options.inJustDecodeBounds = false
      options.inPreferredConfig = Bitmap.Config.RGB_565 // Half memory footprint of ARGB_8888 for mid-range SoCs

      // 3. Decode downsampled bitmap
      var bitmap: Bitmap? = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
      } ?: return@withContext null

      // 4. Handle EXIF rotation if applicable
      val orientation = getExifOrientation(context, uri)
      if (orientation != 0) {
        val matrix = Matrix().apply { postRotate(orientation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
          bitmap.recycle()
          bitmap = rotated
        }
      }

      bitmap
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  suspend fun saveBitmapToVault(
    context: Context,
    bitmap: Bitmap
  ): String = withContext(Dispatchers.IO) {
    val dir = File(context.filesDir, RECEIPT_DIR).apply { if (!exists()) mkdirs() }
    val filename = "receipt_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
    val destFile = File(dir, filename)

    FileOutputStream(destFile).use { fos ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
      fos.flush()
    }
    destFile.absolutePath
  }

  suspend fun saveSampledUriToVault(
    context: Context,
    uri: Uri
  ): Pair<String?, Bitmap?> = withContext(Dispatchers.IO) {
    val bitmap = decodeSampledBitmap(context, uri) ?: return@withContext Pair(null, null)
    val savedPath = saveBitmapToVault(context, bitmap)
    Pair(savedPath, bitmap)
  }

  fun createTempImageUri(context: Context): Uri {
    val cacheDir = File(context.cacheDir, CACHE_DIR).apply { if (!exists()) mkdirs() }
    val tempFile = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      tempFile
    )
  }

  fun deleteVaultImage(path: String?) {
    if (path.isNullOrEmpty()) return
    try {
      val file = File(path)
      if (file.exists()) {
        file.delete()
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun calculateInSampleSize(
    rawWidth: Int,
    rawHeight: Int,
    reqWidth: Int,
    reqHeight: Int
  ): Int {
    var inSampleSize = 1
    val largestDimension = max(rawWidth, rawHeight)
    val targetDimension = max(reqWidth, reqHeight)

    if (largestDimension > targetDimension) {
      val halfDimension = largestDimension / 2
      while ((halfDimension / inSampleSize) >= targetDimension) {
        inSampleSize *= 2
      }
    }
    return inSampleSize.coerceAtLeast(1)
  }

  private fun getExifOrientation(context: Context, uri: Uri): Int {
    return try {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val exif = ExifInterface(stream)
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
          ExifInterface.ORIENTATION_ROTATE_90 -> 90
          ExifInterface.ORIENTATION_ROTATE_180 -> 180
          ExifInterface.ORIENTATION_ROTATE_270 -> 270
          else -> 0
        }
      } ?: 0
    } catch (e: Exception) {
      0
    }
  }
}
