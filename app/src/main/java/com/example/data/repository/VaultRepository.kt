package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.data.db.VaultDao
import com.example.data.model.SubscriptionCycle
import com.example.data.model.VaultItem
import com.example.data.ocr.OcrExtractedResult
import com.example.data.security.ImageFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar

class VaultRepository(
  private val vaultDao: VaultDao,
  private val context: Context
) {
  val allItems: Flow<List<VaultItem>> = vaultDao.getAllItems()
  val warrantyItems: Flow<List<VaultItem>> = vaultDao.getWarrantyItems()
  val subscriptionItems: Flow<List<VaultItem>> = vaultDao.getSubscriptionItems()

  fun getItemById(id: Long): Flow<VaultItem?> = vaultDao.getItemById(id)

  fun searchItems(query: String): Flow<List<VaultItem>> = vaultDao.searchItems(query)

  suspend fun insertItem(item: VaultItem): Long = withContext(Dispatchers.IO) {
    vaultDao.insertItem(item)
  }

  suspend fun updateItem(item: VaultItem) = withContext(Dispatchers.IO) {
    vaultDao.updateItem(item.copy(updatedAt = System.currentTimeMillis()))
  }

  suspend fun deleteItem(item: VaultItem) = withContext(Dispatchers.IO) {
    ImageFileManager.deleteVaultImage(item.imagePath)
    vaultDao.deleteItem(item)
  }

  suspend fun deleteItemById(id: Long) = withContext(Dispatchers.IO) {
    val existing = vaultDao.getItemByIdSync(id)
    if (existing != null) {
      ImageFileManager.deleteVaultImage(existing.imagePath)
      vaultDao.deleteItem(existing)
    }
  }

  suspend fun saveCapturedReceipt(
    bitmap: Bitmap?,
    storeName: String,
    amount: Double,
    currency: String = "$",
    category: String,
    purchaseDate: Long,
    rawText: String?,
    notes: String?,
    isWarranty: Boolean,
    warrantyExpirationDate: Long?,
    isSubscription: Boolean,
    subscriptionCycle: SubscriptionCycle?,
    subscriptionNextRenewalDate: Long?,
    reminderDays: Int
  ): Long = withContext(Dispatchers.IO) {
    val savedImagePath = if (bitmap != null) {
      ImageFileManager.saveBitmapToVault(context, bitmap)
    } else null

    val item = VaultItem(
      storeName = storeName.ifBlank { "Store Receipt" },
      amount = amount,
      currency = currency,
      category = category.ifBlank { "General" },
      purchaseDate = purchaseDate,
      imagePath = savedImagePath,
      ocrRawText = rawText,
      notes = notes,
      isWarranty = isWarranty,
      warrantyExpirationDate = warrantyExpirationDate,
      isSubscription = isSubscription,
      subscriptionCycle = subscriptionCycle?.name,
      subscriptionNextRenewalDate = subscriptionNextRenewalDate,
      subscriptionActive = true,
      reminderDaysBefore = reminderDays,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis()
    )

    vaultDao.insertItem(item)
  }
}
