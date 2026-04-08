package com.netcatty.mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.netcatty.mobile.ui.screens.sftp.SftpScreen
import com.netcatty.mobile.ui.screens.settings.SettingsScreen
import com.netcatty.mobile.ui.screens.terminal.TerminalScreen
import com.netcatty.mobile.ui.screens.vault.VaultScreen

// ─── Route constants ───
object Routes {
    const val VAULT = "vault"
    const val TERMINAL = "terminal"
    const val TERMINAL_HOST = "terminal/{hostId}"
    const val SFTP = "sftp"
    const val SETTINGS = "settings"
}

// ─── Bottom nav items ───
data class BottomNavItem(
    val route: String,
    val labelRes: Int,  // not used in code, placeholder
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.VAULT, 0, "Vault", Icons.Filled.ViewList),
    BottomNavItem(Routes.TERMINAL, 0, "Terminal", Icons.Filled.Terminal),
    BottomNavItem(Routes.SFTP, 0, "SFTP", Icons.Filled.Folder),
    BottomNavItem(Routes.SETTINGS, 0, "Settings", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetcattyNavHost(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.VAULT,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.VAULT) {
                VaultScreen(
                    onHostClick = { hostId ->
                        navController.navigate("terminal/$hostId")
                    }
                )
            }

            composable(Routes.TERMINAL) {
                TerminalScreen()
            }

            composable(Routes.SFTP) {
                SftpScreen()
            }

            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
