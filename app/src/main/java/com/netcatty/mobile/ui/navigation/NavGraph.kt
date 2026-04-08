package com.netcatty.mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.netcatty.mobile.ui.screens.aichat.AiChatScreen
import com.netcatty.mobile.ui.screens.settings.SettingsScreen
import com.netcatty.mobile.ui.screens.sftp.SftpScreen
import com.netcatty.mobile.ui.screens.terminal.TerminalScreen
import com.netcatty.mobile.ui.screens.unlock.UnlockScreen
import com.netcatty.mobile.ui.screens.vault.VaultScreen

object Routes {
    const val UNLOCK = "unlock"
    const val VAULT = "vault"
    const val TERMINAL = "terminal"
    const val TERMINAL_HOST = "terminal/{hostId}"
    const val SFTP = "sftp"
    const val SFTP_HOST = "sftp/{hostId}"
    const val AI_CHAT = "ai_chat"
    const val SETTINGS = "settings"
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.VAULT, "Hosts", Icons.AutoMirrored.Filled.ViewList),
    BottomNavItem(Routes.TERMINAL, "Term", Icons.Filled.Terminal),
    BottomNavItem(Routes.SFTP, "SFTP", Icons.Filled.Folder),
    BottomNavItem(Routes.AI_CHAT, "AI", Icons.AutoMirrored.Filled.Send),
    BottomNavItem(Routes.SETTINGS, "Set", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetcattyNavHost(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var isUnlocked by rememberSaveable { mutableStateOf(false) }

    // Hide bottom bar on detail screens (host-specific terminal/sftp)
    val currentRoute = currentDestination?.route
    val showBottomBar = isUnlocked && currentRoute !in setOf(
        Routes.TERMINAL_HOST, Routes.SFTP_HOST, Routes.UNLOCK
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isUnlocked) Routes.VAULT else Routes.UNLOCK,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.UNLOCK) {
                UnlockScreen(
                    onUnlocked = {
                        isUnlocked = true
                        navController.navigate(Routes.VAULT) {
                            popUpTo(Routes.UNLOCK) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.VAULT) {
                VaultScreen(
                    onConnectSsh = { hostId ->
                        navController.navigate("terminal/$hostId")
                    },
                    onConnectSftp = { hostId ->
                        navController.navigate("sftp/$hostId")
                    }
                )
            }

            // Empty terminal tab (no host selected)
            composable(Routes.TERMINAL) {
                TerminalScreen()
            }

            // Terminal with specific host — auto-connect
            composable(
                route = Routes.TERMINAL_HOST,
                arguments = listOf(navArgument("hostId") { type = NavType.StringType })
            ) { backStackEntry ->
                val hostId = backStackEntry.arguments?.getString("hostId") ?: return@composable TerminalScreen()
                TerminalScreen(connectHostId = hostId)
            }

            // Empty SFTP tab
            composable(Routes.SFTP) {
                SftpScreen()
            }

            // SFTP with specific host — auto-connect
            composable(
                route = Routes.SFTP_HOST,
                arguments = listOf(navArgument("hostId") { type = NavType.StringType })
            ) { backStackEntry ->
                val hostId = backStackEntry.arguments?.getString("hostId") ?: return@composable SftpScreen()
                SftpScreen(connectHostId = hostId)
            }

            composable(Routes.AI_CHAT) {
                AiChatScreen()
            }

            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
