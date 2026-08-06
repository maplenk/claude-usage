package com.qbapps.claudeusage

import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.qbapps.claudeusage.ui.navigation.AppNavHost
import com.qbapps.claudeusage.ui.theme.ClaudeUsageTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var settingsRequest by mutableIntStateOf(0)
    private var providerRequest by mutableIntStateOf(0)
    private var focusProvider by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — helper checks areNotificationsEnabled() before posting */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeWidgetDestination(intent)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        setContent {
            ClaudeUsageTheme {
                val navController = rememberNavController()
                AppNavHost(
                    navController = navController,
                    settingsRequest = settingsRequest,
                    providerRequest = providerRequest,
                    focusProvider = focusProvider,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeWidgetDestination(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    private fun consumeWidgetDestination(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true) {
            settingsRequest += 1
            intent.removeExtra(EXTRA_OPEN_SETTINGS)
        }
        intent?.getStringExtra(EXTRA_FOCUS_PROVIDER)?.let { provider ->
            focusProvider = provider
            providerRequest += 1
            intent.removeExtra(EXTRA_FOCUS_PROVIDER)
        }
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "com.qbapps.claudeusage.extra.OPEN_SETTINGS"
        const val EXTRA_FOCUS_PROVIDER = "com.qbapps.claudeusage.extra.FOCUS_PROVIDER"
    }
}
