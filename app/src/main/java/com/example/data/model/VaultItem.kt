package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SubscriptionCycle(val label: String, val days: Int, val monthlyMultiplier: Double) {
  WEEKLY("Weekly", 7, 4.333),
  MONTHLY("Monthly", 30, 1.0),
  QUARTERLY("Quarterly", 90, 0.333),
  YEARLY("Yearly", 365, 1.0 / 12.0)
}

@Entity(tableName = "vault_items")
data class VaultItem(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val storeName: String,
  val amount: Double,
  val currency: String = "$",
  val category: String = "General",
  val purchaseDate: Long = System.currentTimeMillis(),
  val imagePath: String? = null,
  val ocrRawText: String? = null,
  val notes: String? = null,
  
  // Warranty Tracking
  val isWarranty: Boolean = false,
  val warrantyExpirationDate: Long? = null,
  
  // Subscription Tracking
  val isSubscription: Boolean = false,
  val subscriptionCycle: String? = null, // "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY"
  val subscriptionNextRenewalDate: Long? = null,
  val subscriptionActive: Boolean = true,
  
  // Reminder alert offset
  val reminderDaysBefore: Int = 7,
  
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
) {
  val cycleEnum: SubscriptionCycle?
    get() = subscriptionCycle?.let {
      try {
        SubscriptionCycle.valueOf(it)
      } catch (e: Exception) {
        SubscriptionCycle.MONTHLY
      }
    }

  val monthlyEquivalentCost: Double
    get() {
      if (!isSubscription || !subscriptionActive) return 0.0
      return when (cycleEnum) {
        SubscriptionCycle.WEEKLY -> amount * 4.333
        SubscriptionCycle.MONTHLY -> amount
        SubscriptionCycle.QUARTERLY -> amount / 3.0
        SubscriptionCycle.YEARLY -> amount / 12.0
        null -> amount
      }
    }

  fun daysUntilWarrantyExpires(now: Long = System.currentTimeMillis()): Long? {
    if (!isWarranty || warrantyExpirationDate == null) return null
    val diff = warrantyExpirationDate - now
    return (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(-9999)
  }

  fun daysUntilSubscriptionRenews(now: Long = System.currentTimeMillis()): Long? {
    if (!isSubscription || subscriptionNextRenewalDate == null) return null
    val diff = subscriptionNextRenewalDate - now
    return (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(-9999)
  }
}
