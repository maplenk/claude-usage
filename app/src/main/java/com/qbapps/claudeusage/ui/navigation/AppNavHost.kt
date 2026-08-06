package com.qbapps.claudeusage.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    settingsRequest: Int = 0,
    providerRequest: Int = 0,
    focusProvider: String? = null,
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
                focusProvider = focusProvider,
                focusRequest = providerRequest,
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

    LaunchedEffect(settingsRequest) {
        if (settingsRequest > 0 &&
            navController.currentDestination?.route != Screen.Settings.route
        ) {
            navController.navigate(Screen.Settings.route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(providerRequest) {
        if (providerRequest > 0 &&
            navController.currentDestination?.route != Screen.Dashboard.route
        ) {
            navController.navigate(Screen.Dashboard.route) {
                launchSingleTop = true
                popUpTo(Screen.Dashboard.route)
            }
        }
    }
}
