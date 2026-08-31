package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.BlueSubscription
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.MintLedger
import com.example.ui.theme.PurpleWarranty
import com.example.ui.theme.StampRed
import java.text.NumberFormat
import java.util.Locale

data class ChartSlice(
  val label: String,
  val value: Double,
  val color: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDonutChart(
  slices: List<ChartSlice>,
  centerLabel: String,
  centerValue: String,
  modifier: Modifier = Modifier
) {
  val animationProgress = remember { Animatable(0f) }

  LaunchedEffect(slices) {
    animationProgress.snapTo(0f)
    animationProgress.animateTo(1f, animationSpec = tween(durationMillis = 800))
  }

  val totalValue = slices.sumOf { it.value }.coerceAtLeast(0.01)

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier.size(160.dp),
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.size(140.dp)) {
        val strokeWidth = 24.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val arcSize = Size(diameter, diameter)
        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

        if (slices.isEmpty() || slices.all { it.value <= 0.0 }) {
          drawArc(
            color = Color.LightGray.copy(alpha = 0.3f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
          )
        } else {
          var currentAngle = -90f
          for (slice in slices) {
            val sweep = ((slice.value / totalValue) * 360f * animationProgress.value).toFloat()
            if (sweep > 0.5f) {
              drawArc(
                color = slice.color,
                startAngle = currentAngle,
                sweepAngle = sweep - 2f, // subtle segment gap
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
              )
              currentAngle += sweep
            }
          }
        }
      }

      // Center text
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = centerLabel,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = centerValue,
          style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.Bold
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Legend
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
      slices.filter { it.value > 0 }.forEach { slice ->
        val pct = ((slice.value / totalValue) * 100).toInt()
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(slice.color)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "${slice.label} ($pct%)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
fun WarrantyStatusBar(
  activeCount: Int,
  expiringCount: Int,
  expiredCount: Int,
  modifier: Modifier = Modifier
) {
  val total = (activeCount + expiringCount + expiredCount).coerceAtLeast(1)

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "Warranty Health",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = "$total tracked",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Multi-segment progress bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(10.dp)
        .clip(RoundedCornerShape(5.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
      if (activeCount > 0) {
        Box(
          modifier = Modifier
            .weight(activeCount.toFloat())
            .height(10.dp)
            .background(ForestPrimary)
        )
      }
      if (expiringCount > 0) {
        Box(
          modifier = Modifier
            .weight(expiringCount.toFloat())
            .height(10.dp)
            .background(AmberAlert)
        )
      }
      if (expiredCount > 0) {
        Box(
          modifier = Modifier
            .weight(expiredCount.toFloat())
            .height(10.dp)
            .background(StampRed)
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      LegendItem(color = ForestPrimary, text = "$activeCount Active")
      LegendItem(color = AmberAlert, text = "$expiringCount Soon")
      LegendItem(color = StampRed, text = "$expiredCount Expired")
    }
  }
}

@Composable
private fun LegendItem(color: Color, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(color)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

val DefaultChartPalette = listOf(
  ForestPrimary,
  BlueSubscription,
  PurpleWarranty,
  AmberAlert,
  MintLedger,
  StampRed,
  Color(0xFF457B9D),
  Color(0xFFE9C46A)
)
