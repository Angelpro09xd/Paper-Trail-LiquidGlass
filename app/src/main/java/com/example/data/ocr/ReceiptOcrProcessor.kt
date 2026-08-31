package com.example.data.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume

data class OcrExtractedResult(
  val storeName: String,
  val amount: Double,
  val purchaseDate: Long,
  val suggestedCategory: String,
  val rawText: String,
  val detectedLineItems: List<String> = emptyList(),
  val isSuccessful: Boolean = true
)

object ReceiptOcrProcessor {
  private const val TAG = "ReceiptOcrProcessor"

  private val recognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  suspend fun processImage(bitmap: Bitmap): OcrExtractedResult = withContext(Dispatchers.Default) {
    try {
      val inputImage = InputImage.fromBitmap(bitmap, 0)
      val visionText = recognizeText(inputImage)

      if (visionText == null || visionText.text.isBlank()) {
        Log.w(TAG, "No text recognized by ML Kit.")
        OcrExtractedResult(
          storeName = "",
          amount = 0.0,
          purchaseDate = System.currentTimeMillis(),
          suggestedCategory = "General",
          rawText = "",
          detectedLineItems = emptyList(),
          isSuccessful = false
        )
      } else {
        parseReceiptText(visionText)
      }
    } catch (e: Throwable) {
      Log.e(TAG, "Exception during OCR image processing: ${e.message}", e)
      OcrExtractedResult(
        storeName = "",
        amount = 0.0,
        purchaseDate = System.currentTimeMillis(),
        suggestedCategory = "General",
        rawText = "",
        detectedLineItems = emptyList(),
        isSuccessful = false
      )
    }
  }

  private suspend fun recognizeText(image: InputImage): Text? = suspendCancellableCoroutine { cont ->
    recognizer.process(image)
      .addOnSuccessListener { text ->
        if (cont.isActive) {
          cont.resume(text)
        }
      }
      .addOnFailureListener { exception ->
        Log.w(TAG, "ML Kit text recognition failed: ${exception.message}")
        if (cont.isActive) {
          // Resume cleanly with null instead of calling .result on an incomplete task
          cont.resume(null)
        }
      }
  }

  fun parseReceiptText(visionText: Text): OcrExtractedResult {
    val fullText = visionText.text
    val lines = visionText.textBlocks.flatMap { it.lines.map { line -> line.text.trim() } }
      .filter { it.isNotBlank() }

    val storeName = extractStoreName(lines)
    val totalAmount = extractTotalAmount(lines, fullText)
    val purchaseDate = extractDate(lines, fullText)
    val suggestedCategory = guessCategory(storeName, fullText)

    val lineItems = lines.filter { line ->
      // Filter lines that look like item descriptions with prices
      line.contains(Regex("""\$\s*\d+[\.,]\d{2}|\b\d+[\.,]\d{2}\b"""))
    }.take(15)

    return OcrExtractedResult(
      storeName = storeName,
      amount = totalAmount,
      purchaseDate = purchaseDate,
      suggestedCategory = suggestedCategory,
      rawText = fullText,
      detectedLineItems = lineItems,
      isSuccessful = fullText.isNotBlank()
    )
  }

  private fun extractStoreName(lines: List<String>): String {
    val skipKeywords = setOf(
      "receipt", "tax invoice", "sales receipt", "customer copy", "merchant copy",
      "welcome to", "thank you", "order", "invoice", "terminal", "register",
      "cashier", "store #", "tel", "phone", "date", "time", "www", "http", ".com"
    )

    for (line in lines.take(6)) {
      val lower = line.lowercase(Locale.ROOT)
      val shouldSkip = skipKeywords.any { lower.contains(it) } ||
        line.all { it.isDigit() || it == '-' || it == '/' || it == ':' } ||
        line.length < 2

      if (!shouldSkip) {
        // Clean up symbols
        val cleaned = line.replace(Regex("""^[#*\-~–\s]+|[#*\-~–\s]+$"""), "")
        if (cleaned.length in 2..40) {
          return cleaned
        }
      }
    }
    return ""
  }

  private fun extractTotalAmount(lines: List<String>, fullText: String): Double {
    // 1. Look for explicit total lines from bottom to top
    val totalKeywords = listOf(
      "total", "grand total", "amount due", "balance due", "total due",
      "final amount", "charged", "payment amount", "sum", "subtotal"
    )

    val moneyRegex = Regex("""(?:\$|\b)([0-9]{1,4}(?:[,\.][0-9]{2}))\b""")

    for (line in lines.reversed()) {
      val lower = line.lowercase(Locale.ROOT)
      if (totalKeywords.any { lower.contains(it) }) {
        val matches = moneyRegex.findAll(line).toList()
        if (matches.isNotEmpty()) {
          val lastMatch = matches.last().groupValues[1].replace(",", ".")
          val parsed = lastMatch.toDoubleOrNull()
          if (parsed != null && parsed > 0.0) {
            return parsed
          }
        }
      }
    }

    // 2. Look for any monetary pattern in lines
    val allPrices = mutableListOf<Double>()
    for (line in lines) {
      val matches = moneyRegex.findAll(line)
      for (match in matches) {
        val numStr = match.groupValues[1].replace(",", ".")
        val num = numStr.toDoubleOrNull()
        if (num != null && num in 0.01..50000.0) {
          allPrices.add(num)
        }
      }
    }

    return allPrices.maxOrNull() ?: 0.0
  }

  private fun extractDate(lines: List<String>, fullText: String): Long {
    val datePatterns = listOf(
      SimpleDateFormat("MM/dd/yyyy", Locale.US),
      SimpleDateFormat("MM-dd-yyyy", Locale.US),
      SimpleDateFormat("yyyy-MM-dd", Locale.US),
      SimpleDateFormat("yyyy/MM/dd", Locale.US),
      SimpleDateFormat("dd/MM/yyyy", Locale.US),
      SimpleDateFormat("dd-MM-yyyy", Locale.US),
      SimpleDateFormat("MMM dd, yyyy", Locale.US),
      SimpleDateFormat("dd MMM yyyy", Locale.US),
      SimpleDateFormat("MM/dd/yy", Locale.US),
      SimpleDateFormat("dd/MM/yy", Locale.US)
    )

    val dateRegex = Regex("""\b(\d{1,4}[/\-.]\d{1,2}[/\-.]\d{2,4}|\b[A-Za-z]{3}\s+\d{1,2},?\s+\d{4})\b""")

    for (line in lines) {
      val match = dateRegex.find(line)
      if (match != null) {
        val rawDateStr = match.value.trim()
        for (format in datePatterns) {
          try {
            format.isLenient = false
            val parsed = format.parse(rawDateStr)
            if (parsed != null && parsed.time <= System.currentTimeMillis() + 86400000L) {
              return parsed.time
            }
          } catch (ignored: Exception) {
          }
        }
      }
    }

    return System.currentTimeMillis()
  }

  private fun guessCategory(storeName: String, fullText: String): String {
    val combined = (storeName + " " + fullText).lowercase(Locale.ROOT)
    return when {
      combined.containsAny("apple", "best buy", "samsung", "dell", "sony", "lenovo", "asus", "intel", "electronics", "gadget", "headphone", "laptop", "tv", "camera") -> "Electronics"
      combined.containsAny("walmart", "target", "costco", "trader joe", "kroger", "whole foods", "supermarket", "grocery", "produce", "safeway", "aldi", "food market") -> "Groceries"
      combined.containsAny("netflix", "spotify", "hulu", "disney", "adobe", "google one", "icloud", "chatgpt", "youtube", "subscription", "monthly membership", "patreon") -> "Subscriptions"
      combined.containsAny("home depot", "lowes", "ikea", "hardware", "furniture", "appliance", "plumbing", "mattress", "kitchen") -> "Home & Tools"
      combined.containsAny("starbucks", "mcdonald", "restaurant", "cafe", "coffee", "pizza", "burger", "diner", "bistro", "bakery", "taco") -> "Dining"
      combined.containsAny("cvs", "walgreens", "pharmacy", "clinic", "hospital", "dental", "medicine", "health", "rx") -> "Healthcare"
      combined.containsAny("autozone", "shell", "chevron", "gas", "fuel", "oil change", "toyota", "honda", "ford", "tire") -> "Automotive"
      combined.containsAny("delta", "airline", "hotel", "airbnb", "uber", "lyft", "flight", "booking", "resort") -> "Travel"
      else -> "General"
    }
  }

  private fun String.containsAny(vararg words: String): Boolean {
    return words.any { this.contains(it) }
  }
}
