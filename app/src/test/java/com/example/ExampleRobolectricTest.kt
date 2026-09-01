package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SubscriptionCycle
import com.example.data.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Paper Trail", appName)
  }

  @Test
  fun `test subscription monthly equivalent cost calculation`() {
    val weeklySub = VaultItem(
      storeName = "Weekly News",
      amount = 5.0,
      category = "Subscriptions",
      purchaseDate = System.currentTimeMillis(),
      isSubscription = true,
      subscriptionCycle = SubscriptionCycle.WEEKLY.name
    )
    assertEquals(21.65, weeklySub.monthlyEquivalentCost, 0.1)

    val yearlySub = VaultItem(
      storeName = "Cloud Storage",
      amount = 120.0,
      category = "Subscriptions",
      purchaseDate = System.currentTimeMillis(),
      isSubscription = true,
      subscriptionCycle = SubscriptionCycle.YEARLY.name
    )
    assertEquals(10.0, yearlySub.monthlyEquivalentCost, 0.01)
  }

  @Test
  fun `test warranty days left calculation`() {
    val now = System.currentTimeMillis()
    val tenDaysAhead = now + (10L * 24 * 60 * 60 * 1000)

    val item = VaultItem(
      storeName = "Espresso Machine",
      amount = 499.0,
      category = "Home & Tools",
      purchaseDate = now,
      isWarranty = true,
      warrantyExpirationDate = tenDaysAhead
    )

    val daysLeft = item.daysUntilWarrantyExpires(now)
    assertEquals(10L, daysLeft)
  }

  @Test
  fun `test tutorial preferences default and mutation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Should initially be false
    val initial = com.example.data.TutorialPreferences.hasSeenTutorial(context)
    assertEquals(false, initial)

    // Mark as seen
    com.example.data.TutorialPreferences.setTutorialSeen(context, true)
    val after = com.example.data.TutorialPreferences.hasSeenTutorial(context)
    assertEquals(true, after)
  }

  @Test
  fun `test secure vault passphrase generation and persistence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val key1 = com.example.securevault.data.SecureVaultPassphraseManager.getOrCreatePassphrase(context)
    val key2 = com.example.securevault.data.SecureVaultPassphraseManager.getOrCreatePassphrase(context)
    assertEquals(32, key1.size)
    org.junit.Assert.assertArrayEquals(key1, key2)
  }

  @Test
  fun `test secure vault auth manager independent lock state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val authManager = com.example.securevault.security.SecureVaultAuthManager(context)
    // Starts locked
    assertEquals(false, authManager.isUnlocked.value)
    authManager.lock()
    assertEquals(false, authManager.isUnlocked.value)
  }

  @Test
  fun `test secure file item model properties`() {
    val item = com.example.securevault.model.SecureFileItem(
      id = 1L,
      originalFileName = "tax_return_2025.pdf",
      mimeType = "application/pdf",
      fileSizeBytes = 204800L,
      encryptedBlobPath = "1234-uuid-blob",
      dateAdded = 1700000000000L,
      iv = "dGVzdF9pdg=="
    )
    assertEquals(1L, item.id)
    assertEquals("tax_return_2025.pdf", item.originalFileName)
    assertEquals("application/pdf", item.mimeType)
    assertEquals(204800L, item.fileSizeBytes)
    assertEquals("1234-uuid-blob", item.encryptedBlobPath)
    assertEquals("dGVzdF9pdg==", item.iv)
  }

  @Test
  fun `test security integrity audit returns valid non-null report`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val report = com.example.data.security.SecurityIntegrityAuditor.runFullAudit(context)
    org.junit.Assert.assertNotNull(report)
    org.junit.Assert.assertTrue(report.items.isNotEmpty())
    
    // SELinux check must be present and not null
    val selinuxItem = report.items.find { it.id == "selinux" }
    org.junit.Assert.assertNotNull(selinuxItem)
    assertEquals(com.example.data.security.IntegrityStatus.VERIFIED, selinuxItem?.status)

    // Storage encryption check must be present
    val storageItem = report.items.find { it.id == "storage_encryption" }
    org.junit.Assert.assertNotNull(storageItem)
  }
}
