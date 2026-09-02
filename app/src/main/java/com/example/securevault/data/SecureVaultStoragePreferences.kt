package com.example.securevault.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VaultStorageLocation(
  val id: String,
  val title: String,
  val badge: String,
  val description: String,
  val pros: List<String>,
  val cons: List<String>
) {
  INTERNAL_SANDBOX(
    id = "internal",
    title = "Internal App Sandbox (Default)",
    badge = "Maximum Security & Anti-Tamper",
    description = "Stores encrypted cipher blobs in private app storage (/data/user/0/.../securevault).",
    pros = listOf(
      "Highest security isolation — other apps and file managers cannot browse or delete files.",
      "Zero risk of accidental deletion by third-party cleaning tools or gallery file managers.",
      "Requires no extra storage permissions."
    ),
    cons = listOf(
      "Wiped if user selects 'Clear Data / Storage' in Android Settings.",
      "Removed when the application is uninstalled."
    )
  ),
  EXTERNAL_APP(
    id = "external_app",
    title = "App-Specific External Storage",
    badge = "Saves Device Partition",
    description = "Stores encrypted cipher blobs in /sdcard/Android/data/<package>/files/securevault with .nomedia protection.",
    pros = listOf(
      "Offloads multi-gigabyte movies and game packages to external flash memory, saving core internal storage.",
      "Hidden from media gallery scanners with .nomedia file.",
      "All files remain encrypted AES-256-GCM ciphertexts requiring the app key to decrypt."
    ),
    cons = listOf(
      "Android OS still wipes this folder if user uninstalls the app or taps 'Clear Data'."
    )
  ),
  PERSISTENT_CUSTOM_FOLDER(
    id = "custom_folder",
    title = "Persistent Custom Folder (SAF)",
    badge = "Survives Clear Data & Reinstalls",
    description = "Stores encrypted cipher blobs in a custom user-chosen folder (e.g. Documents/.secure_vault/ or SD Card).",
    pros = listOf(
      "Survives 'Clear Data' and app reinstallation completely without losing files.",
      "Can be easily copied or synced to SD card, USB drive, or PC as safe encrypted blobs.",
      "Files are opaque AES-256-GCM ciphertexts that can only be decrypted by SecureVault."
    ),
    cons = listOf(
      "Android cannot lock public folders — a user could accidentally delete or rename blob files in an external file manager."
    )
  );

  companion object {
    fun fromId(id: String): VaultStorageLocation {
      return entries.find { it.id == id } ?: INTERNAL_SANDBOX
    }
  }
}

class SecureVaultStoragePreferences(private val context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences("securevault_storage_prefs", Context.MODE_PRIVATE)

  private val _currentLocation = MutableStateFlow(getStorageLocation())
  val currentLocation: StateFlow<VaultStorageLocation> = _currentLocation.asStateFlow()

  private val _customFolderUriString = MutableStateFlow(getCustomFolderUriString())
  val customFolderUriString: StateFlow<String?> = _customFolderUriString.asStateFlow()

  fun getStorageLocation(): VaultStorageLocation {
    val id = prefs.getString("storage_location_id", VaultStorageLocation.INTERNAL_SANDBOX.id)
      ?: VaultStorageLocation.INTERNAL_SANDBOX.id
    return VaultStorageLocation.fromId(id)
  }

  fun setStorageLocation(location: VaultStorageLocation) {
    prefs.edit { putString("storage_location_id", location.id) }
    _currentLocation.value = location
  }

  fun getCustomFolderUriString(): String? {
    return prefs.getString("custom_folder_uri", null)
  }

  fun setCustomFolderUri(uri: Uri?) {
    prefs.edit {
      if (uri != null) {
        putString("custom_folder_uri", uri.toString())
      } else {
        remove("custom_folder_uri")
      }
    }
    _customFolderUriString.value = uri?.toString()
  }

  fun getCustomFolderDisplayName(ctx: Context = context): String? {
    val uriStr = getCustomFolderUriString() ?: return null
    return try {
      val treeUri = Uri.parse(uriStr)
      val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, treeUri)
      doc?.name ?: treeUri.lastPathSegment ?: uriStr
    } catch (e: Exception) {
      uriStr
    }
  }
}
