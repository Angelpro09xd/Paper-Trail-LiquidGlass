package com.example.securevault.ui

import android.app.Application
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.security.SecurityAuditPreferences
import com.example.data.security.SecurityIntegrityAuditor
import com.example.securevault.data.SecureVaultDatabase
import com.example.securevault.data.SecureVaultKeyManager
import com.example.securevault.data.SecureVaultRepository
import com.example.securevault.model.SecureFileItem
import com.example.securevault.security.SecureVaultAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

data class SecureVaultPreview(
  val item: SecureFileItem,
  val decryptedBytes: ByteArray?,
  val isTooLargeToPreview: Boolean = false
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as SecureVaultPreview
    if (item != other.item) return false
    if (isTooLargeToPreview != other.isTooLargeToPreview) return false
    return (decryptedBytes == null && other.decryptedBytes == null) ||
        (decryptedBytes != null && other.decryptedBytes != null && decryptedBytes.contentEquals(other.decryptedBytes))
  }

  override fun hashCode(): Int {
    var result = item.hashCode()
    result = 31 * result + (decryptedBytes?.contentHashCode() ?: 0)
    result = 31 * result + isTooLargeToPreview.hashCode()
    return result
  }
}

class SecureVaultViewModel(application: Application) : AndroidViewModel(application) {
  private val TAG = "SecureVaultViewModel"

  private val database by lazy {
    SecureVaultDatabase.getInstance(application)
  }

  val repository: SecureVaultRepository by lazy {
    SecureVaultRepository(database.secureVaultDao(), application)
  }

  val authManager: SecureVaultAuthManager by lazy {
    SecureVaultAuthManager(application)
  }

  val secureFiles: StateFlow<List<SecureFileItem>> = repository.allFiles
    .flowOn(Dispatchers.Default)
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _activePreview = MutableStateFlow<SecureVaultPreview?>(null)
  val activePreview: StateFlow<SecureVaultPreview?> = _activePreview.asStateFlow()

  fun clearError() {
    _errorMessage.value = null
  }

  fun lockVault() {
    authManager.lock()
    closePreview()
  }

  fun closePreview() {
    _activePreview.value = null
  }

  fun unlockVault(
    activity: FragmentActivity,
    onSuccess: () -> Unit = {}
  ) {
    _isLoading.value = true
    viewModelScope.launch {
      if (SecurityAuditPreferences.isStrictGateEnabled(activity)) {
        val report = withContext(Dispatchers.Default) {
          SecurityIntegrityAuditor.runFullAudit(activity)
        }
        if (report.hasCriticalFailures) {
          _isLoading.value = false
          _errorMessage.value = "Hardware security check failed: SELinux or storage encryption compromised."
          return@launch
        }
      }

      try {
        val cipher = try {
          SecureVaultKeyManager.initEncryptCipher()
        } catch (e: Exception) {
          Log.w(TAG, "Cipher init without prompt: ${e.message}")
          null
        }

        authManager.promptBiometric(
          activity = activity,
          cipher = cipher,
          title = "Unlock SecureVault",
          subtitle = "Biometric confirmation required to open hardware-encrypted storage",
          onSuccess = {
            _isLoading.value = false
            onSuccess()
          },
          onError = { err ->
            _isLoading.value = false
            _errorMessage.value = err
          }
        )
      } catch (e: Exception) {
        _isLoading.value = false
        _errorMessage.value = "Failed to start biometric authentication: ${e.message}"
      }
    }
  }

  fun importFile(
    uri: Uri,
    activity: FragmentActivity,
    onComplete: () -> Unit = {}
  ) {
    _isLoading.value = true
    try {
      val cipher = SecureVaultKeyManager.initEncryptCipher()
      authManager.promptBiometric(
        activity = activity,
        cipher = cipher,
        title = "Encrypt & Store File",
        subtitle = "Confirm biometrics to authorize hardware AES-256 encryption",
        onSuccess = { authenticatedCipher ->
          val validCipher = authenticatedCipher ?: cipher
          viewModelScope.launch {
            val result = repository.importFile(uri, validCipher)
            _isLoading.value = false
            result.onSuccess {
              onComplete()
            }.onFailure { err ->
              _errorMessage.value = "Failed to encrypt file: ${err.message}"
            }
          }
        },
        onError = { err ->
          _isLoading.value = false
          _errorMessage.value = "Biometric authorization required to encrypt: $err"
        }
      )
    } catch (e: Exception) {
      _isLoading.value = false
      _errorMessage.value = "Encryption initialization error: ${e.message}"
    }
  }

  fun previewFile(
    item: SecureFileItem,
    activity: FragmentActivity
  ) {
    _isLoading.value = true
    try {
      val ivBytes = Base64.decode(item.iv, Base64.NO_WRAP)
      val cipher = SecureVaultKeyManager.initDecryptCipher(ivBytes)

      authManager.promptBiometric(
        activity = activity,
        cipher = cipher,
        title = "Decrypt for Preview",
        subtitle = "Confirm biometrics to authorize in-memory hardware decryption",
        onSuccess = { authenticatedCipher ->
          val validCipher = authenticatedCipher ?: cipher
          viewModelScope.launch {
            val result = repository.decryptFile(item, validCipher)
            _isLoading.value = false
            result.onSuccess { decResult ->
              when (decResult) {
                is com.example.securevault.data.DecryptionResult.Success -> {
                  _activePreview.value = SecureVaultPreview(item, decResult.bytes, isTooLargeToPreview = false)
                }
                is com.example.securevault.data.DecryptionResult.TooLargeToPreview -> {
                  _activePreview.value = SecureVaultPreview(item, null, isTooLargeToPreview = true)
                }
              }
            }.onFailure { err ->
              _errorMessage.value = "Decryption failed: ${err.message}"
            }
          }
        },
        onError = { err ->
          _isLoading.value = false
          _errorMessage.value = "Biometric authorization required to decrypt: $err"
        }
      )
    } catch (e: Exception) {
      _isLoading.value = false
      _errorMessage.value = "Decryption initialization error: ${e.message}"
    }
  }

  fun deleteFile(item: SecureFileItem) {
    viewModelScope.launch {
      if (_activePreview.value?.item?.id == item.id) {
        closePreview()
      }
      repository.deleteFile(item)
    }
  }
}
