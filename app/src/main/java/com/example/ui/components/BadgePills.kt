package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VaultItem
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.AmberAlertContainer
import com.example.ui.theme.AmberAlertOnContainer
import com.example.ui.theme.BlueSubscription
import com.example.ui.theme.BlueSubscriptionContainer
import com.example.ui.theme.ForestContainer
import com.example.ui.theme.ForestOnContainer
import com.example.ui.theme.MintLedgerContainer
import com.example.ui.theme.PurpleWarranty
import com.example.ui.theme.PurpleWarrantyContainer
import com.example.ui.theme.StampRed
import com.example.ui.theme.StampRedContainer

@Composable
fun CategoryBadge(category: String, modifier: Modifier = Modifier) {
  val icon = getCategoryIcon(category)
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .padding(horizontal = 8.dp, vertical = 4.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(12.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = category,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
      )
    }
  }
}

@Composable
fun WarrantyBadge(item: VaultItem, modifier: Modifier = Modifier) {
  if (!item.isWarranty || item.warrantyExpirationDate == null) return

  val days = item.daysUntilWarrantyExpires() ?: return
  val (bgColor, textColor, label, icon) = when {
    days < 0 -> Quadruple(StampRedContainer, StampRed, "Warranty Expired", Icons.Default.Warning)
    days == 0L -> Quadruple(StampRedContainer, StampRed, "Expires Today", Icons.Default.Warning)
    days <= item.reminderDaysBefore -> Quadruple(
      AmberAlertContainer,
      AmberAlertOnContainer,
      "Expiring in ${days}d",
      Icons.Default.Warning
    )
    else -> Quadruple(
      PurpleWarrantyContainer,
      PurpleWarranty,
      "Warranty (${days}d)",
      Icons.Default.Shield
    )
  }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(bgColor)
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(12.dp),
        tint = textColor
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}

@Composable
fun SubscriptionBadge(item: VaultItem, modifier: Modifier = Modifier) {
  if (!item.isSubscription) return

  val cycleStr = item.cycleEnum?.label ?: "Sub"
  val days = item.daysUntilSubscriptionRenews()

  val (bgColor, textColor, label) = when {
    !item.subscriptionActive -> Triple(
      MaterialTheme.colorScheme.surfaceVariant,
      MaterialTheme.colorScheme.onSurfaceVariant,
      "Paused"
    )
    days != null && days <= 3 && days >= 0 -> Triple(
      AmberAlertContainer,
      AmberAlertOnContainer,
      "Renews in ${if (days == 0L) "today" else "${days}d"}"
    )
    else -> Triple(
      BlueSubscriptionContainer,
      BlueSubscription,
      cycleStr
    )
  }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(bgColor)
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Default.Autorenew,
        contentDescription = null,
        modifier = Modifier.size(12.dp),
        tint = textColor
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}

fun getCategoryIcon(category: String): ImageVector {
  return when (category.lowercase()) {
    "electronics" -> Icons.Default.Smartphone
    "groceries" -> Icons.Default.LocalGroceryStore
    "subscriptions" -> Icons.Default.Subscriptions
    "home & tools" -> Icons.Default.Home
    "dining" -> Icons.Default.LocalDining
    "healthcare" -> Icons.Default.MedicalServices
    "automotive" -> Icons.Default.DirectionsCar
    else -> Icons.Default.Receipt
  }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
