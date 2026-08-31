package com.example.ui.screens.settings

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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.BiometricAuthManager
import com.example.ui.components.DashedDivider
import com.example.ui.components.ReceiptPerforatedHeader
import com.example.ui.screens.vault.VaultViewModel
import com.example.ui.theme.ForestContainer
import com.example.ui.theme.ForestOnContainer
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.MonospaceLedgerStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: VaultViewModel,
  onLockVault: () -> Unit
) {
  val context = LocalContext.current
  val authManager = viewModel.authManager
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  var isLockEnabled by remember { mutableStateOf(authManager.isLockConfigured) }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Text("Security & Privacy", fontWeight = FontWeight.Bold)
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = MaterialTheme.colorScheme.background
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Zero Cloud Commitment Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, ForestPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
          .testTag("privacy_commitment_card"),
        colors = CardDefaults.cardColors(containerColor = ForestContainer)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Shield,
              contentDescription = null,
              tint = ForestPrimary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "100% Offline Vault Guarantee",
              style = MaterialTheme.typography.titleMedium,
              color = ForestOnContainer,
              fontWeight = FontWeight.Bold
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = "• No Cloud Synchronization\n• No Third-Party Analytics or Crash Reporters\n• No Bank Account Linking\n• 256-bit AES SQLCipher On-Device Encryption\n• On-Device ML Kit OCR Processing",
            style = MaterialTheme.typography.bodyMedium,
            color = ForestOnContainer.copy(alpha = 0.9f)
          )
        }
      }

      // 2. Biometric Security Settings
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Vault Lock",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Biometric / PIN Screen Lock", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
              Text("Require biometric authentication or device PIN to access receipts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Switch(
              checked = isLockEnabled,
              onCheckedChange = {
                isLockEnabled = it
                authManager.setLockConfigured(it)
              },
              modifier = Modifier.testTag("toggle_biometric_lock")
            )
          }

          DashedDivider()

          Button(
            onClick = onLockVault,
            modifier = Modifier.fillMaxWidth().testTag("lock_now_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
          ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Lock Vault Now")
          }
        }
      }

      // 3. Background Reminders
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Notifications & Reminders",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )

          Text(
            text = "Reminders run via Android WorkManager with OEM battery-friendly flexible scheduling.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          OutlinedButton(
            onClick = {
              viewModel.triggerReminderCheck()
              scope.launch {
                snackbarHostState.showSnackbar("Background reminder check triggered.")
              }
            },
            modifier = Modifier.fillMaxWidth().testTag("test_reminders_button")
          ) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Test Reminder Check Now")
          }
        }
      }

      // 4. App Info
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text("Paper Trail v1.0", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Spacer(modifier = Modifier.height(4.dp))
          Text("Local-First Receipt, Warranty & Subscription Vault", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}
