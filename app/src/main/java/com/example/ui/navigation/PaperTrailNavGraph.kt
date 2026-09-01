package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.TutorialPreferences
import com.example.data.security.SecurityAuditPreferences
import com.example.data.security.SecurityIntegrityAuditor
import com.example.securevault.ui.SecureVaultScreen
import com.example.securevault.ui.SecureVaultViewModel
import com.example.ui.screens.auth.BiometricLockScreen
import com.example.ui.screens.auth.SecurityIntegrityGateScreen
import com.example.ui.screens.capture.CaptureOcrScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.detail.ItemDetailEditScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.tutorial.TutorialScreen
import com.example.ui.screens.vault.VaultListScreen
import com.example.ui.screens.vault.VaultTab
import com.example.ui.screens.vault.VaultViewModel

sealed class Screen(val route: String, val title: String) {
  object Dashboard : Screen("dashboard", "Dashboard")
  object Vault : Screen("vault", "Vault Ledger")
  object SecureVault : Screen("secure_vault", "SecureVault")
  object Settings : Screen("settings", "Security")
  object Capture : Screen("capture", "Scan Receipt")
  object Tutorial : Screen("tutorial", "Walkthrough")
  object ItemDetail : Screen("item_detail/{itemId}", "Item Detail") {
    fun createRoute(itemId: Long) = "item_detail/$itemId"
  }
}

fun androidx.navigation.NavController.navigateToTopLevelDestination(route: String) {
  navigate(route) {
    popUpTo(graph.findStartDestination().id) {
      saveState = true
    }
    launchSingleTop = true
    restoreState = true
  }
}

@Composable
fun PaperTrailAppContent(
  viewModel: VaultViewModel,
  secureVaultViewModel: SecureVaultViewModel = viewModel()
) {
  val context = LocalContext.current
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route

  val startDestination = remember {
    if (TutorialPreferences.hasSeenTutorial(context)) Screen.Dashboard.route else Screen.Tutorial.route
  }

  val isUnlocked by viewModel.authManager.isUnlocked.collectAsStateWithLifecycle()

  val isStrictGateEnabled = remember { SecurityAuditPreferences.isStrictGateEnabled(context) }
  var integrityReport by remember {
    mutableStateOf(if (isStrictGateEnabled) SecurityIntegrityAuditor.runFullAudit(context) else null)
  }

  if (integrityReport != null && integrityReport!!.hasCriticalFailures && isStrictGateEnabled) {
    SecurityIntegrityGateScreen(
      initialReport = integrityReport!!,
      onResolved = { newReport ->
        integrityReport = newReport
      }
    )
  } else if (!isUnlocked && viewModel.authManager.isLockConfigured) {
    BiometricLockScreen(
      authManager = viewModel.authManager,
      onUnlocked = { /* unlocked state updated in StateFlow */ }
    )
  } else {
    val bottomNavItems = listOf(
      Triple(Screen.Dashboard.route, "Dashboard", Icons.Default.Dashboard),
      Triple(Screen.Vault.route, "Ledger", Icons.Default.ReceiptLong),
      Triple(Screen.SecureVault.route, "SecureVault", Icons.Default.EnhancedEncryption),
      Triple(Screen.Settings.route, "Security", Icons.Default.Shield)
    )

    val showBottomBar = currentRoute in listOf(
      Screen.Dashboard.route,
      Screen.Vault.route,
      Screen.SecureVault.route,
      Screen.Settings.route
    )

    Scaffold(
      bottomBar = {
        if (showBottomBar) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
          ) {
            bottomNavItems.forEach { (route, label, icon) ->
              val isSelected = currentRoute == route
              NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                selected = isSelected,
                onClick = {
                  if (currentRoute != route) {
                    if (currentRoute == Screen.SecureVault.route) {
                      secureVaultViewModel.lockVault()
                    }
                    navController.navigateToTopLevelDestination(route)
                  }
                },
                colors = NavigationBarItemDefaults.colors(
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  selectedTextColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("nav_item_$route")
              )
            }
          }
        }
      }
    ) { paddingValues ->
      NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.padding(paddingValues)
      ) {
        composable(Screen.Tutorial.route) {
          TutorialScreen(
            onFinishTutorial = {
              TutorialPreferences.setTutorialSeen(context, true)
              if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
              } else {
                navController.navigateToTopLevelDestination(Screen.Dashboard.route)
              }
            }
          )
        }

        composable(Screen.Dashboard.route) {
          DashboardScreen(
            viewModel = viewModel,
            onNavigateToCapture = { navController.navigate(Screen.Capture.route) },
            onNavigateToItemDetail = { id -> navController.navigate(Screen.ItemDetail.createRoute(id)) },
            onNavigateToVault = { tab ->
              viewModel.setTab(tab)
              navController.navigateToTopLevelDestination(Screen.Vault.route)
            },
            onLockVault = { viewModel.authManager.lock() }
          )
        }

        composable(Screen.Vault.route) {
          VaultListScreen(
            viewModel = viewModel,
            onNavigateToCapture = { navController.navigate(Screen.Capture.route) },
            onNavigateToItemDetail = { id -> navController.navigate(Screen.ItemDetail.createRoute(id)) }
          )
        }

        composable(Screen.SecureVault.route) {
          SecureVaultScreen(
            viewModel = secureVaultViewModel
          )
        }

        composable(Screen.Settings.route) {
          SettingsScreen(
            viewModel = viewModel,
            onLockVault = { viewModel.authManager.lock() },
            onNavigateToTutorial = { navController.navigate(Screen.Tutorial.route) }
          )
        }

        composable(Screen.Capture.route) {
          CaptureOcrScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { savedId ->
              navController.navigate(Screen.ItemDetail.createRoute(savedId)) {
                popUpTo(Screen.Dashboard.route)
              }
            }
          )
        }

        composable(
          route = Screen.ItemDetail.route,
          arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
          val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
          ItemDetailEditScreen(
            itemId = itemId,
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() }
          )
        }
      }
    }
  }
}
