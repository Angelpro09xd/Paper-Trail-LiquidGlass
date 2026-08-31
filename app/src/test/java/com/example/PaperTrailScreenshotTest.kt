package com.example

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.model.SubscriptionCycle
import com.example.data.model.VaultItem
import com.example.ui.components.PerforatedReceiptCard
import com.example.ui.theme.PaperTrailTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class PaperTrailScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun receipt_card_screenshot() {
    val sampleItem = VaultItem(
      id = 1L,
      storeName = "Best Buy",
      amount = 899.99,
      category = "Electronics",
      purchaseDate = 1716300000000L,
      isWarranty = true,
      warrantyExpirationDate = System.currentTimeMillis() + 864000000L,
      isSubscription = false
    )

    composeTestRule.setContent {
      PaperTrailTheme {
        PerforatedReceiptCard(
          item = sampleItem,
          onClick = {},
          modifier = Modifier.padding(16.dp)
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/receipt_card.png")
  }
}
