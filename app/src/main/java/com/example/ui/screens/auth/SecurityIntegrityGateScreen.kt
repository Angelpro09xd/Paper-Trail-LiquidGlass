package com.example.ui.screens.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.IntegrityCheckItem
import com.example.data.security.IntegrityReport
import com.example.data.security.IntegrityStatus
import com.example.data.security.SecurityIntegrityAuditor
import com.example.ui.components.DashedDivider
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.StampRed
import com.example.ui.theme.StampRedContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

@Composable
fun SecurityIntegrityGateScreen(
  initialReport: IntegrityReport,
  onResolved: (IntegrityReport) -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var currentReport by remember { mutableStateOf(initialReport) }
  var isRetesting by remember { mutableStateOf(false) }

  fun retestEnvironment() {
    isRetesting = true
    scope.launch {
      delay(400)
      val newReport = withContext(Dispatchers.Default) {
        SecurityIntegrityAuditor.runFullAudit(context)
      }
      currentReport = newReport
      isRetesting = false
      if (!newReport.hasCriticalFailures) {
        onResolved(newReport)
      }
    }
  }

  fun exitApp() {
    val activity = context as? Activity
    activity?.finishAffinity() ?: exitProcess(0)
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      Spacer(modifier = Modifier.height(16.dp))

      // Warning Icon
      Box(
        modifier = Modifier
          .size(72.dp)
          .clip(CircleShape)
          .background(StampRedContainer)
          .border(2.dp, StampRed, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = "Critical Security Alert",
          tint = StampRed,
          modifier = Modifier.size(38.dp)
        )
      }

      // Title & Subtitle
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "Security Isolation Warning",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Hardware HAL or SELinux confinement failure detected on this device or ROM build.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }

      // Diagnostic Items Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, StampRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
          .testTag("security_gate_diagnostic_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "Hardware & Environment Diagnostic",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )

          currentReport.items.forEach { item ->
            GateItemRow(item = item)
          }
        }
      }

      // Explanation Box
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          .padding(14.dp)
      ) {
        Text(
          text = "Why this is blocked: When SELinux is in Permissive/Spoofed mode or device storage is unencrypted, the Linux kernel does not enforce mandatory process sandboxing. Other apps or background processes could extract decrypted cryptographic memory or intercept hardware keystore keys.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 18.sp
        )
      }

      Spacer(modifier = Modifier.weight(1f, fill = false))

      // Action Buttons
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = { retestEnvironment() },
          enabled = !isRetesting,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("retest_environment_button"),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          if (isRetesting) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Re-testing Environment...")
          } else {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Re-test Hardware & SELinux")
          }
        }

        OutlinedButton(
          onClick = { exitApp() },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("exit_application_button"),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = StampRed)
        ) {
          Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Exit Application")
        }
      }
    }
  }
}

@Composable
private fun GateItemRow(item: IntegrityCheckItem) {
  val (color, icon) = when (item.status) {
    IntegrityStatus.VERIFIED -> Pair(ForestPrimary, Icons.Default.CheckCircle)
    IntegrityStatus.WARNING -> Pair(Color(0xFFE76F51), Icons.Default.WarningAmber)
    IntegrityStatus.CRITICAL_FAILURE -> Pair(StampRed, Icons.Default.Warning)
    IntegrityStatus.OPTIONAL_ABSENT -> Pair(Color.Gray, Icons.Default.Info)
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
      .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = item.status.name,
        tint = color,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = item.title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )
    }

    Text(
      text = item.summary,
      style = MaterialTheme.typography.bodySmall,
      color = color,
      fontWeight = FontWeight.Bold
    )
  }
}
