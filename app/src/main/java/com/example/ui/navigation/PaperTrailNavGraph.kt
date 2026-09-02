package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.securevault.ui.SecureVaultScreen
import com.example.securevault.ui.SecureVaultViewModel
import com.example.ui.glass.GlassNavigationBar
import com.example.ui.glass.glassBackdropSource
import com.example.ui.glass.rememberGlassBackdrop
import com.example.ui.screens.auth.BiometricLockScreen
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

  if (!isUnlocked && viewModel.authManager.isLockConfigured) {
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

    val backdrop = rememberGlassBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
      NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier
          .fillMaxSize()
          .glassBackdropSource(backdrop),
        enterTransition = { androidx.compose.animation.fadeIn(com.example.ui.theme.PaperTrailMotion.fadeIn) },
        exitTransition = { androidx.compose.animation.fadeOut(com.example.ui.theme.PaperTrailMotion.fadeOut) },
        popEnterTransition = { androidx.compose.animation.fadeIn(com.example.ui.theme.PaperTrailMotion.fadeIn) },
        popExitTransition = { androidx.compose.animation.fadeOut(com.example.ui.theme.PaperTrailMotion.fadeOut) }
      ) {
        composable(
          route = Screen.Tutorial.route,
          enterTransition = { androidx.compose.animation.slideInHorizontally(animationSpec = com.example.ui.theme.PaperTrailMotion.screenEnter()) { it } + androidx.compose.animation.fadeIn(com.example.ui.theme.PaperTrailMotion.fadeIn) },
          exitTransition = { androidx.compose.animation.slideOutHorizontally(animationSpec = com.example.ui.theme.PaperTrailMotion.screenExit()) { -it / 4 } + androidx.compose.animation.fadeOut(com.example.ui.theme.PaperTrailMotion.fadeOut) },
          popEnterTransition = { androidx.compose.animation.slideInHorizontally(animationSpec = com.example.ui.theme.PaperTrailMotion.screenEnter()) { -it / 4 } + androidx.compose.animation.fadeIn(com.example.ui.theme.PaperTrailMotion.fadeIn) },
          popExitTransition = { androidx.compose.animation.slideOutHorizontally(animationSpec = com.example.ui.theme.PaperTrailMotion.screenExit()) { it } + androidx.compose.animation.fadeOut(com.example.ui.theme.PaperTrailMotion.fadeOut) }
        ) {
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
            viewModel = secureVaultViewModel,
            onNavigateBack = {
              if (!navController.popBackStack()) {
                navController.navigateToTopLevelDestination(Screen.Dashboard.route)
              }
            }
          )
        }

        composable(Screen.Settings.route) {
          SettingsScreen(
            viewModel = viewModel,
            onLockVault = { viewModel.authManager.lock() },
            onNavigateToTutorial = { navController.navigate(Screen.Tutorial.route) }
          )
        }

        composable(
          route = Screen.Capture.route,
          enterTransition = { androidx.compose.animation.slideInHorizontally(animationSpec = com.example.ui.theme.PaperTrailMotion.screenEnter()) { it } + androidx.compose.animation.fadeIn(com.example.ui.theme.PaperTrailMotion.fadeIn) },
          exitTransition = { androidx.compose.animation.slideOutHorizontally(animationSpec = com.example.ui.theme.PaperTrailMotion.screenExit()) { -it / 4 } + androidx.compose.animation.fadeOut(com.example.ui.theme.PaperTrailMotion.fadeOut) },
          popEnterTransition = { androidx.compose.animation.slideInHorizontally(animationSpec = com.example.ui.theme.PaperTrailMotion.screenEnter()) { -it / 4 } + androidx.compose.animation.fadeIn(com.example.ui.theme.PaperTrailMotion.fadeIn) },
          popExitTransition = { androidx.compose.animation.slideOutHorizontally(animationSpec = com.example.ui.theme.PaperTrailMotion.screenExit()) { it } + androidx.compose.animation.fadeOut(com.example.ui.theme.PaperTrailMotion.fadeOut) }
        ) {
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
          arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
          enterTransition = { androidx.compose.animation.slideInHorizontally(animationSpec = com.example.ui.theme.PaperTrailMotion.screenEnter()) { it } + androidx.compose.animation.fadeIn(com.example.ui.theme.PaperTrailMotion.fadeIn) },
          exitTransition = { androidx.compose.animation.slideOutHorizontally(animationSpec = com.example.ui.theme.PaperTrailMotion.screenExit()) { -it / 4 } + androidx.compose.animation.fadeOut(com.example.ui.theme.PaperTrailMotion.fadeOut) },
          popEnterTransition = { androidx.compose.animation.slideInHorizontally(animationSpec = com.example.ui.theme.PaperTrailMotion.screenEnter()) { -it / 4 } + androidx.compose.animation.fadeIn(com.example.ui.theme.PaperTrailMotion.fadeIn) },
          popExitTransition = { androidx.compose.animation.slideOutHorizontally(animationSpec = com.example.ui.theme.PaperTrailMotion.screenExit()) { it } + androidx.compose.animation.fadeOut(com.example.ui.theme.PaperTrailMotion.fadeOut) }
        ) { backStackEntry ->
          val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
          ItemDetailEditScreen(
            itemId = itemId,
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() }
          )
        }
      }

      AnimatedVisibility(
        visible = showBottomBar,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(com.example.ui.theme.PaperTrailMotion.fadeIn),
        exit = fadeOut(com.example.ui.theme.PaperTrailMotion.fadeOut)
      ) {
        GlassNavigationBar(
          backdrop = backdrop,
          items = bottomNavItems,
          currentRoute = currentRoute,
          onItemSelected = { route ->
            if (currentRoute != route) {
              if (currentRoute == Screen.SecureVault.route) {
                secureVaultViewModel.lockVault()
              }
              navController.navigateToTopLevelDestination(route)
            }
          }
        )
      }
    }
  }
}
