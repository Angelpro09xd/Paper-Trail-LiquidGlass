package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SubscriptionCycle
import com.example.data.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

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
}
