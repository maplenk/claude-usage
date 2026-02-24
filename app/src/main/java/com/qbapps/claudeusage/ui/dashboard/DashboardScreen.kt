package com.qbapps.claudeusage.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qbapps.claudeusage.domain.model.UsageError
import com.qbapps.claudeusage.ui.dashboard.components.UsageCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Re-check credentials each time the screen is composed (e.g. returning from Settings)
    LaunchedEffect(Unit) {
        viewModel.recheckConfiguration()
    }

    // Show snackbar on error
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            val message = when (error) {
                is UsageError.Unauthorized -> "Session expired. Please update your key in Settings."
                is UsageError.RateLimited -> "Rate limited. Retrying shortly."
                is UsageError.NetworkError -> "Network error. Check your connection."
                is UsageError.ServerError -> "Server error (${error.code})."
                is UsageError.NoCredentials -> "No credentials configured."
                is UsageError.Unknown -> "Something went wrong."
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Claude Usage") },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (state.isConfigured && !state.isLoading) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (!state.isConfigured) {
            NotConfiguredContent(
                onGoToSettings = onNavigateToSettings,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                val usage = state.usage
                if (usage == null && state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item {
                            UsageCard(
                                label = "Session (5h)",
                                metric = usage?.fiveHour,
                            )
                        }
                        item {
                            UsageCard(
                                label = "Weekly (7d)",
                                metric = usage?.sevenDay,
                            )
                        }
                        item {
                            UsageCard(
                                label = "Opus (7d)",
                                metric = usage?.sevenDayOpus,
                            )
                        }
                        item {
                            UsageCard(
                                label = "Sonnet (7d)",
                                metric = usage?.sevenDaySonnet,
                            )
                        }

                        // Last-updated footer
                        if (usage != null) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                val formatter = remember {
                                    DateTimeFormatter.ofPattern("MMM d, h:mm:ss a")
                                        .withZone(ZoneId.systemDefault())
                                }
                                Text(
                                    text = "Last updated: ${formatter.format(usage.fetchedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotConfiguredContent(
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Welcome to Claude Usage",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "To get started, add your Claude session key and select an organization in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGoToSettings) {
            Text("Go to Settings")
        }
    }
}
