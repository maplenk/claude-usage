package com.qbapps.claudeusage.ui.navigation

/**
 * Represents the app's navigation destinations.
 */
sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Settings : Screen("settings")
}
