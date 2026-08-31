package com.example.data.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.math.max

data class OcrExtractedResult(
  val storeName: String,
  val amount: Double,
  val purchaseDate: Long,
  val suggestedCategory: String,
  val rawText: String,
  val detectedLineItems: List<String> = emptyList()
)

object ReceiptOcrProcessor {
  private val recognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  suspend fun processImage(bitmap: Bitmap): OcrExtractedResult = withContext(Dispatchers.Default) {
    val inputImage = InputImage.fromBitmap(bitmap, 0)
    val visionText = recognizeText(inputImage)
    parseReceiptText(visionText)
  }

  private suspend fun recognizeText(image: InputImage): Text = suspendCancellableCoroutine { cont ->
    recognizer.process(image)
      .addOnSuccessListener { text ->
        if (cont.isActive) cont.resume(text)
      }
      .addOnFailureListener { exception ->
        if (cont.isActive) {
          // If ML Kit recognition encounters any issue, return empty text gracefully
          cont.resume(recognizer.process(image).result ?: createEmptyText())
        }
      }
  }

  private fun createEmptyText(): Text {
    // Return empty fallback
    return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      .process(InputImage.fromBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8), 0))
      .result
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
      detectedLineItems = lineItems
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
    return "Store / Merchant"
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

    // 2. Look for any largest monetary pattern in the bottom half of lines
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
