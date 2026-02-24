package com.qbapps.claudeusage.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.qbapps.claudeusage.ui.dashboard.DashboardScreen
import com.qbapps.claudeusage.ui.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier,
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
