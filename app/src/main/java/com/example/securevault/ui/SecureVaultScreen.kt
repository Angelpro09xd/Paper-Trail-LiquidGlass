package com.example.securevault.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.security.IntegrityReport
import com.example.data.security.SecurityAuditPreferences
import com.example.data.security.SecurityIntegrityAuditor
import com.example.securevault.model.SecureFileItem
import com.example.ui.screens.auth.SecurityIntegrityGateScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Custom accent color for SecureVault isolation branding
private val SecureVaultAmber = Color(0xFFD97706)
private val SecureVaultAmberContainer = Color(0xFFFEF3C7)
private val SecureVaultOnAmberContainer = Color(0xFF92400E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureVaultScreen(
  viewModel: SecureVaultViewModel,
  onNavigateBack: () -> Unit = {}
) {
  val context = LocalContext.current
  val activity = context as? FragmentActivity
  val lifecycleOwner = LocalLifecycleOwner.current

  val isUnlocked by viewModel.authManager.isUnlocked.collectAsStateWithLifecycle()
  val secureFiles by viewModel.secureFiles.collectAsStateWithLifecycle()
  val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
  val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
  val activePreview by viewModel.activePreview.collectAsStateWithLifecycle()

  val isStrictGateEnabled = remember { SecurityAuditPreferences.isStrictGateEnabled(context) }
  var integrityReport by remember { mutableStateOf<IntegrityReport?>(null) }
  var isAuditing by remember { mutableStateOf(isStrictGateEnabled) }

  LaunchedEffect(isStrictGateEnabled) {
    if (isStrictGateEnabled) {
      isAuditing = true
      val report = withContext(Dispatchers.Default) {
        SecurityIntegrityAuditor.runFullAudit(context)
      }
      integrityReport = report
      isAuditing = false
    } else {
      integrityReport = null
      isAuditing = false
    }
  }

  val snackbarHostState = remember { SnackbarHostState() }
  var itemToDelete by remember { mutableStateOf<SecureFileItem?>(null) }

  // Re-lock whenever the screen leaves composition or app is backgrounded
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
        viewModel.lockVault()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.lockVault()
    }
  }

  // SAF Document Picker for any file type
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null && activity != null) {
      viewModel.importFile(uri, activity)
    }
  }

  LaunchedEffect(errorMessage) {
    errorMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearError()
    }
  }

  if (isStrictGateEnabled && integrityReport != null && integrityReport!!.hasCriticalFailures) {
    // Isolated hardware security failure: Gate SecureVault without impacting the rest of Paper Trail
    SecurityIntegrityGateScreen(
      initialReport = integrityReport!!,
      onResolved = { newReport ->
        integrityReport = newReport
      },
      onReturnToApp = onNavigateBack
    )
  } else if (!isUnlocked) {
    // Dedicated Biometric Lock Screen for SecureVault
    SecureVaultLockGate(
      onUnlock = {
        activity?.let {
          viewModel.unlockVault(it)
        }
      },
      isLoading = isLoading || isAuditing
    )
  } else {
    // Unlocked SecureVault UI
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.EnhancedEncryption,
                contentDescription = null,
                tint = SecureVaultAmber,
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "SecureVault",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Isolated Hardware AES-256 Storage",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          },
          actions = {
            IconButton(
              onClick = { viewModel.lockVault() },
              modifier = Modifier.testTag("securevault_lock_button")
            ) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Lock SecureVault",
                tint = MaterialTheme.colorScheme.primary
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        )
      },
      floatingActionButton = {
        ExtendedFloatingActionButton(
          onClick = {
            filePickerLauncher.launch(arrayOf("*/*"))
          },
          icon = { Icon(Icons.Default.Add, contentDescription = null) },
          text = { Text("Add File", fontWeight = FontWeight.Bold) },
          containerColor = SecureVaultAmber,
          contentColor = Color.White,
          modifier = Modifier.testTag("securevault_add_file_fab")
        )
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
      containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        // Security Status Banner
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SecureVaultAmberContainer)
            .border(1.dp, SecureVaultAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = SecureVaultOnAmberContainer,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "ENCRYPTED AT REST (AES-GCM)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SecureVaultOnAmberContainer,
                letterSpacing = 0.5.sp
              )
            }
            Text(
              text = "${secureFiles.size} ${if (secureFiles.size == 1) "file" else "files"}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = SecureVaultOnAmberContainer
            )
          }
        }

        if (isLoading) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(32.dp),
              color = SecureVaultAmber
            )
          }
        }

        if (secureFiles.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(32.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Box(
                modifier = Modifier
                  .size(80.dp)
                  .clip(CircleShape)
                  .background(SecureVaultAmberContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.LockOpen,
                  contentDescription = null,
                  modifier = Modifier.size(40.dp),
                  tint = SecureVaultOnAmberContainer
                )
              }
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = "SecureVault is Empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Store sensitive documents, contracts, photos, and files. Files are saved as opaque ciphertext blobs and never cached unencrypted to disk.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
              )
              Spacer(modifier = Modifier.height(20.dp))
              Button(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = SecureVaultAmber),
                modifier = Modifier.testTag("securevault_empty_import_button")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import File", fontWeight = FontWeight.Bold)
              }
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .testTag("securevault_files_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(secureFiles, key = { it.id }) { item ->
              SecureFileCard(
                item = item,
                onPreview = {
                  activity?.let { act ->
                    viewModel.previewFile(item, act)
                  }
                },
                onDelete = { itemToDelete = item }
              )
            }
          }
        }
      }
    }
  }

  // Delete Confirmation Dialog
  if (itemToDelete != null) {
    val file = itemToDelete!!
    AlertDialog(
      onDismissRequest = { itemToDelete = null },
      title = { Text("Delete Encrypted File?") },
      text = {
        Text("Are you sure you want to permanently delete \"${file.originalFileName}\"? The encrypted blob will be wiped from storage.")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteFile(file)
            itemToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_delete_secure_file")
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { itemToDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // In-Memory Decryption Preview Dialog
  if (activePreview != null) {
    SecureFilePreviewDialog(
      preview = activePreview!!,
      onDismiss = { viewModel.closePreview() }
    )
  }
}

@Composable
private fun SecureVaultLockGate(
  onUnlock: () -> Unit,
  isLoading: Boolean
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(32.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(108.dp)
          .clip(CircleShape)
          .background(SecureVaultAmberContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.EnhancedEncryption,
          contentDescription = null,
          modifier = Modifier.size(56.dp),
          tint = SecureVaultOnAmberContainer
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "SecureVault Protected",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "This isolated vault requires hardware biometric authentication to access and decrypt your private files.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 22.sp
      )

      Spacer(modifier = Modifier.height(32.dp))

      Button(
        onClick = onUnlock,
        enabled = !isLoading,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("securevault_unlock_button"),
        colors = ButtonDefaults.buttonColors(containerColor = SecureVaultAmber)
      ) {
        if (isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Color.White,
            strokeWidth = 2.dp
          )
        } else {
          Icon(Icons.Default.Fingerprint, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Unlock SecureVault", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      }
    }
  }
}

@Composable
private fun SecureFileCard(
  item: SecureFileItem,
  onPreview: () -> Unit,
  onDelete: () -> Unit
) {
  val dateFormatted = remember(item.dateAdded) {
    SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(item.dateAdded))
  }
  val sizeFormatted = remember(item.fileSizeBytes) {
    formatFileSize(item.fileSizeBytes)
  }
  val fileIcon = remember(item.mimeType) {
    getFileTypeIcon(item.mimeType)
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
      .clickable { onPreview() }
      .testTag("secure_file_card_${item.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // File Icon Box
      Box(
        modifier = Modifier
          .size(46.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(SecureVaultAmberContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = fileIcon,
          contentDescription = null,
          tint = SecureVaultOnAmberContainer,
          modifier = Modifier.size(24.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      // File Details
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.originalFileName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = sizeFormatted,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = " • $dateFormatted",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Actions
      IconButton(
        onClick = onPreview,
        modifier = Modifier.size(36.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Visibility,
          contentDescription = "Preview file",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(36.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "Delete file",
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

@Composable
private fun SecureFilePreviewDialog(
  preview: SecureVaultPreview,
  onDismiss: () -> Unit
) {
  val item = preview.item
  val isImage = item.mimeType.startsWith("image/")
  val isText = item.mimeType.startsWith("text/") ||
      item.mimeType.contains("json") ||
      item.mimeType.contains("csv") ||
      item.mimeType.contains("xml") ||
      item.mimeType.contains("markdown")

  val imageBitmap = remember(preview.decryptedBytes) {
    if (isImage) {
      try {
        BitmapFactory.decodeByteArray(preview.decryptedBytes, 0, preview.decryptedBytes.size)?.asImageBitmap()
      } catch (e: Throwable) {
        null
      }
    } else null
  }

  val textContent = remember(preview.decryptedBytes) {
    if (isText) {
      try {
        String(preview.decryptedBytes, Charsets.UTF_8)
      } catch (e: Throwable) {
        "Error decoding text content"
      }
    } else null
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .border(1.dp, SecureVaultAmber.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.EnhancedEncryption,
              contentDescription = null,
              tint = SecureVaultAmber,
              modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = item.originalFileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "${formatFileSize(item.fileSizeBytes)} • Decrypted in RAM only",
                style = MaterialTheme.typography.labelSmall,
                color = SecureVaultAmber
              )
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close preview")
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content Area
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp, max = 380.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp),
          contentAlignment = Alignment.Center
        ) {
          if (imageBitmap != null) {
            Image(
              bitmap = imageBitmap,
              contentDescription = item.originalFileName,
              modifier = Modifier.fillMaxSize()
            )
          } else if (textContent != null) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
            ) {
              Text(
                text = textContent,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          } else {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(16.dp)
            ) {
              Icon(
                imageVector = getFileTypeIcon(item.mimeType),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = SecureVaultAmber
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "Binary / Document File",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "MIME: ${item.mimeType}\nSize: ${formatFileSize(item.fileSizeBytes)}\nVerified intact in volatile memory.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
          onClick = onDismiss,
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          Text("Close & Wipe from RAM")
        }
      }
    }
  }
}

private fun formatFileSize(bytes: Long): String {
  if (bytes <= 0) return "0 B"
  val kb = bytes / 1024.0
  val mb = kb / 1024.0
  val gb = mb / 1024.0
  return when {
    gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
    mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
    kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
    else -> "$bytes B"
  }
}

private fun getFileTypeIcon(mimeType: String): ImageVector {
  return when {
    mimeType.startsWith("image/") -> Icons.Default.Image
    mimeType.startsWith("video/") -> Icons.Default.VideoFile
    mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
    mimeType.startsWith("text/") || mimeType.contains("document") -> Icons.Default.Description
    mimeType.contains("zip") || mimeType.contains("tar") || mimeType.contains("compressed") -> Icons.Default.FolderZip
    else -> Icons.Default.InsertDriveFile
  }
}
