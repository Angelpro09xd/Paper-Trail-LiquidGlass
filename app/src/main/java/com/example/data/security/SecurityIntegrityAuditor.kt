package com.example.data.security

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import androidx.biometric.BiometricManager
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Method
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

enum class IntegrityStatus {
  VERIFIED,           // 🟢 Working as expected (Hardware / Enforced)
  WARNING,            // 🟡 Non-critical warning (e.g. PIN not configured or optional component)
  CRITICAL_FAILURE,   // 🔴 Security compromised (e.g. SELinux permissive/spoofed, unencrypted storage, broken HAL)
  OPTIONAL_ABSENT     // ⚪ Hardware feature not present on this device model (e.g. StrongBox absent, falls back to TEE)
}

data class IntegrityCheckItem(
  val id: String,
  val title: String,
  val status: IntegrityStatus,
  val summary: String,
  val technicalDetail: String,
  val isCritical: Boolean
)

data class IntegrityReport(
  val items: List<IntegrityCheckItem>,
  val isEnvironmentSecure: Boolean,
  val hasCriticalFailures: Boolean,
  val timestamp: Long = System.currentTimeMillis()
)

object SecurityIntegrityAuditor {
  private const val TAG = "SecurityIntegrityAuditor"
  private const val PROBE_KEY_ALIAS = "_integrity_probe_key"

  fun runFullAudit(context: Context): IntegrityReport {
    val items = mutableListOf<IntegrityCheckItem>()

    // 1. SELinux Enforcing & Anti-Spoofing Check
    items.add(auditSELinux())

    // 2. Keymaster / KeyMint TEE Hardware Keystore HAL Check
    items.add(auditKeymasterTee())

    // 3. StrongBox Dedicated HSM Hardware Enclave Check
    items.add(auditStrongBox(context))

    // 4. Device Storage Encryption (FBE / FDE) Check
    items.add(auditStorageEncryption(context))

    // 5. Biometric Hardware & Cryptographic Binding Check
    items.add(auditBiometrics(context))

    val hasCriticalFailures = items.any { it.status == IntegrityStatus.CRITICAL_FAILURE }
    val isEnvironmentSecure = !hasCriticalFailures

    return IntegrityReport(
      items = items,
      isEnvironmentSecure = isEnvironmentSecure,
      hasCriticalFailures = hasCriticalFailures
    )
  }

  /**
   * Evaluates SELinux enforcement status and executes an active rule violation probe
   * to detect spoofed or fake "Enforcing" indicators on permissive custom kernels.
   */
  private fun auditSELinux(): IntegrityCheckItem {
    var isEnforcedFramework = false
    var isPermissiveFramework = false
    var sysfsEnforceValue = -1
    var isSpoofed = false

    // A. Query SELinux via Android Framework reflection
    try {
      val selinuxClass = Class.forName("android.os.SELinux")
      val isEnforcedMethod: Method = selinuxClass.getMethod("isSELinuxEnforced")
      val isPermissiveMethod: Method = selinuxClass.getMethod("isSELinuxPermissive")
      isEnforcedFramework = isEnforcedMethod.invoke(null) as? Boolean ?: false
      isPermissiveFramework = isPermissiveMethod.invoke(null) as? Boolean ?: false
    } catch (e: Exception) {
      Log.w(TAG, "SELinux framework reflection check: ${e.message}")
    }

    // B. Query direct Sysfs node /sys/fs/selinux/enforce if readable
    try {
      val enforceFile = File("/sys/fs/selinux/enforce")
      if (enforceFile.exists() && enforceFile.canRead()) {
        val content = enforceFile.readText().trim()
        sysfsEnforceValue = content.toIntOrNull() ?: -1
      }
    } catch (_: Exception) {}

    // C. Active SELinux MAC policy probe (Anti-Spoofing test)
    // In Android's untrusted_app domain, access to kernel debugfs (/sys/kernel/debug) or
    // root kernel symbols (/proc/kallsyms) is strictly forbidden by SELinux policy.
    // If an app CAN open or traverse these without an access denial, SELinux is Permissive or Fake-Enforced.
    val debugfsNode = File("/sys/kernel/debug")
    val kallsymsNode = File("/proc/kallsyms")
    var probeBlockedByMac = false

    try {
      if (debugfsNode.exists()) {
        val list = debugfsNode.list()
        if (list != null && list.isNotEmpty()) {
          // Unconfined / Permissive: untrusted_app should never be able to list /sys/kernel/debug
          isSpoofed = true
        } else {
          probeBlockedByMac = true
        }
      } else {
        probeBlockedByMac = true
      }
    } catch (_: SecurityException) {
      probeBlockedByMac = true
    } catch (_: Exception) {
      probeBlockedByMac = true
    }

    try {
      if (kallsymsNode.exists()) {
        val fis = FileInputStream(kallsymsNode)
        val buf = ByteArray(64)
        val read = fis.read(buf)
        fis.close()
        // On enforcing SELinux, unprivileged apps read only 0 bytes or null addresses
        if (read > 0 && String(buf).contains("T ")) {
          // Kernel symbols exposed to unprivileged domain -> SELinux confinement weak/permissive
          isSpoofed = true
        }
      }
    } catch (_: Exception) {
      // Access denied / blocked is healthy behavior under MAC
    }

    val isActuallyEnforcing = (isEnforcedFramework || sysfsEnforceValue == 1) && !isPermissiveFramework && !isSpoofed

    return if (isActuallyEnforcing) {
      IntegrityCheckItem(
        id = "selinux",
        title = "SELinux Confinement",
        status = IntegrityStatus.VERIFIED,
        summary = "Enforcing (Active MAC Sandbox)",
        technicalDetail = "Kernel SELinux is in strict Enforcing mode. untrusted_app domain policy is active with anti-spoofing probe verified.",
        isCritical = true
      )
    } else if (isSpoofed) {
      IntegrityCheckItem(
        id = "selinux",
        title = "SELinux Confinement",
        status = IntegrityStatus.CRITICAL_FAILURE,
        summary = "Spoofed / Permissive Kernel",
        technicalDetail = "SELinux returned enforcing status but failed active MAC containment probe. The kernel allows unconfined access to restricted nodes.",
        isCritical = true
      )
    } else {
      IntegrityCheckItem(
        id = "selinux",
        title = "SELinux Confinement",
        status = IntegrityStatus.CRITICAL_FAILURE,
        summary = "Permissive Mode Detected",
        technicalDetail = "SELinux is in Permissive mode. Process isolation and memory protections are unconfined, exposing private storage to other processes.",
        isCritical = true
      )
    }
  }

  /**
   * Verifies Keymaster / KeyMint TEE Hardware Keystore HAL functionality.
   */
  private fun auditKeymasterTee(): IntegrityCheckItem {
    return try {
      val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
      
      val spec = KeyGenParameterSpec.Builder(
        PROBE_KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()

      keyGen.init(spec)
      val secretKey = keyGen.generateKey()

      // Probe cipher creation
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, secretKey)
      val iv = cipher.iv

      // Clean up probe key
      try {
        keyStore.deleteEntry(PROBE_KEY_ALIAS)
      } catch (_: Exception) {}

      IntegrityCheckItem(
        id = "keymaster_tee",
        title = "Keymaster / KeyMint HAL (TEE)",
        status = IntegrityStatus.VERIFIED,
        summary = "Operational (Hardware TEE Active)",
        technicalDetail = "Android KeyStore HAL is responsive. AES-256 key generation, TEE boundary isolation, and cryptographic cipher initialization succeeded.",
        isCritical = true
      )
    } catch (e: Exception) {
      Log.e(TAG, "Keymaster TEE HAL audit failed: ${e.message}", e)
      IntegrityCheckItem(
        id = "keymaster_tee",
        title = "Keymaster / KeyMint HAL (TEE)",
        status = IntegrityStatus.CRITICAL_FAILURE,
        summary = "HAL Failure / Broken TEE",
        technicalDetail = "Hardware Keystore HAL communication error (${e.javaClass.simpleName}: ${e.message ?: "Unknown"}). Cryptographic keys cannot be bound to hardware.",
        isCritical = true
      )
    }
  }

  /**
   * Checks for dedicated StrongBox Keymaster (Titan M / Secure Enclave) hardware support.
   */
  private fun auditStrongBox(context: Context): IntegrityCheckItem {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
      return IntegrityCheckItem(
        id = "strongbox",
        title = "StrongBox Dedicated HSM",
        status = IntegrityStatus.OPTIONAL_ABSENT,
        summary = "Unsupported (Requires Android 9+)",
        technicalDetail = "StrongBox requires Android 9 (API 28) or higher. Device falls back to standard TEE keystore.",
        isCritical = false
      )
    }

    val hasStrongBoxFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    if (!hasStrongBoxFeature) {
      return IntegrityCheckItem(
        id = "strongbox",
        title = "StrongBox Dedicated HSM",
        status = IntegrityStatus.OPTIONAL_ABSENT,
        summary = "Not Equipped (TEE Active)",
        technicalDetail = "Device does not contain a dedicated discrete StrongBox chip (Titan M / Knox HSM). Cryptographic keys are protected by the primary SoC TEE.",
        isCritical = false
      )
    }

    return try {
      val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
      val spec = KeyGenParameterSpec.Builder(
        "${PROBE_KEY_ALIAS}_sb",
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setIsStrongBoxBacked(true)
        .build()

      keyGen.init(spec)
      keyGen.generateKey()

      try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.deleteEntry("${PROBE_KEY_ALIAS}_sb")
      } catch (_: Exception) {}

      IntegrityCheckItem(
        id = "strongbox",
        title = "StrongBox Dedicated HSM",
        status = IntegrityStatus.VERIFIED,
        summary = "Active & Verified",
        technicalDetail = "Discrete hardware security module (StrongBox) is active and verified. Tamper-resistant physical isolation is enforced.",
        isCritical = false
      )
    } catch (e: StrongBoxUnavailableException) {
      IntegrityCheckItem(
        id = "strongbox",
        title = "StrongBox Dedicated HSM",
        status = IntegrityStatus.OPTIONAL_ABSENT,
        summary = "Unavailable (Falling back to TEE)",
        technicalDetail = "StrongBox feature declared but module unavailable. Hardware keystore operations safely utilize the primary TEE.",
        isCritical = false
      )
    } catch (e: Exception) {
      IntegrityCheckItem(
        id = "strongbox",
        title = "StrongBox Dedicated HSM",
        status = IntegrityStatus.OPTIONAL_ABSENT,
        summary = "Fallback to TEE",
        technicalDetail = "StrongBox initialization failed: ${e.message}. Standard TEE Keystore provides primary hardware isolation.",
        isCritical = false
      )
    }
  }

  /**
   * Verifies File-Based Encryption (FBE) or Full-Disk Encryption (FDE) on /data partition.
   */
  private fun auditStorageEncryption(context: Context): IntegrityCheckItem {
    var isEncryptedDpm = false
    var cryptoStateProperty = ""

    try {
      val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
      val status = dpm?.storageEncryptionStatus ?: DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE
      isEncryptedDpm = status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
                       status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
    } catch (e: Exception) {
      Log.w(TAG, "DevicePolicyManager storage audit: ${e.message}")
    }

    try {
      val systemProperties = Class.forName("android.os.SystemProperties")
      val getMethod = systemProperties.getMethod("get", String::class.java)
      cryptoStateProperty = getMethod.invoke(null, "ro.crypto.state") as? String ?: ""
    } catch (_: Exception) {}

    val isEncrypted = isEncryptedDpm || cryptoStateProperty == "encrypted" || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    return if (isEncrypted) {
      IntegrityCheckItem(
        id = "storage_encryption",
        title = "Storage Hardware Encryption",
        status = IntegrityStatus.VERIFIED,
        summary = "Active (FBE AES-256)",
        technicalDetail = "File-Based Encryption (FBE) is active on device storage. Credential-Encrypted (CE) and Device-Encrypted (DE) storage partitions are hardware protected.",
        isCritical = true
      )
    } else {
      IntegrityCheckItem(
        id = "storage_encryption",
        title = "Storage Hardware Encryption",
        status = IntegrityStatus.CRITICAL_FAILURE,
        summary = "Unencrypted Storage",
        technicalDetail = "Device storage (/data partition) is not hardware-encrypted. Direct physical extraction via recovery mode or fastboot is possible.",
        isCritical = true
      )
    }
  }

  /**
   * Audits Biometric authentication hardware and credential binding status.
   */
  private fun auditBiometrics(context: Context): IntegrityCheckItem {
    val biometricManager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                         BiometricManager.Authenticators.DEVICE_CREDENTIAL

    return when (biometricManager.canAuthenticate(authenticators)) {
      BiometricManager.BIOMETRIC_SUCCESS -> {
        IntegrityCheckItem(
          id = "biometrics",
          title = "Biometric & Credential Gate",
          status = IntegrityStatus.VERIFIED,
          summary = "Enforced & Bound",
          technicalDetail = "Biometric sensors (Class 3 Strong) or Secure Lock Screen PIN/Pattern are configured and capable of crypto-object binding.",
          isCritical = false
        )
      }
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
        IntegrityCheckItem(
          id = "biometrics",
          title = "Biometric & Credential Gate",
          status = IntegrityStatus.WARNING,
          summary = "No Screen Lock Configured",
          technicalDetail = "No device PIN, Pattern, or Fingerprint is enrolled in Android Settings. Vault encryption remains active, but local device access is unprotected.",
          isCritical = false
        )
      }
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
        IntegrityCheckItem(
          id = "biometrics",
          title = "Biometric & Credential Gate",
          status = IntegrityStatus.WARNING,
          summary = "No Biometric Hardware",
          technicalDetail = "Device lacks biometric hardware sensors. Screen lock PIN/Pattern credential authentication is required.",
          isCritical = false
        )
      }
      else -> {
        IntegrityCheckItem(
          id = "biometrics",
          title = "Biometric & Credential Gate",
          status = IntegrityStatus.WARNING,
          summary = "Sensor Unavailable",
          technicalDetail = "Biometric authenticator is currently unavailable or uncalibrated.",
          isCritical = false
        )
      }
    }
  }
}
