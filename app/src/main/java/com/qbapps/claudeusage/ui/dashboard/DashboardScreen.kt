package com.qbapps.claudeusage.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qbapps.claudeusage.domain.model.UsageError
import com.qbapps.claudeusage.ui.dashboard.components.CodexWeeklyCard
import com.qbapps.claudeusage.ui.dashboard.components.DashboardMetaCard
import com.qbapps.claudeusage.ui.dashboard.components.GrokWeeklyCard
import com.qbapps.claudeusage.ui.dashboard.components.SessionGuardrailCard
import com.qbapps.claudeusage.ui.dashboard.components.SessionHeroCard
import java.time.Instant

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

    LaunchedEffect(Unit) { viewModel.recheckConfiguration() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it.toUiMessage()) }
    }
    LaunchedEffect(state.codexError) {
        state.codexError?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(state.grokError) {
        state.grokError?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OpenUsage",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (state.isConfigured) {
                        IconButton(
                            onClick = viewModel::refresh,
                            enabled = !state.isLoading,
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
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
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                val allDataMissing = state.usage == null &&
                    state.codexUsage == null &&
                    state.grokUsage == null
                if (allDataMissing && state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.isClaudeConfigured) {
                            state.error?.let { error ->
                                item {
                                    ProviderErrorBanner(
                                        provider = "Claude",
                                        message = error.toUiMessage(),
                                        onRetry = viewModel::refresh,
                                    )
                                }
                            }
                            item {
                                SessionHeroCard(
                                    metric = state.usage?.fiveHour,
                                    weeklyMetric = state.usage?.sevenDay,
                                    useRelativeTime = state.useRelativeTime,
                                )
                            }
                            item {
                                SessionGuardrailCard(
                                    metric = state.usage?.fiveHour,
                                    history = state.history,
                                )
                            }
                        }

                        if (state.isCodexConfigured) {
                            state.codexError?.let { error ->
                                item {
                                    ProviderErrorBanner("Codex", error, viewModel::refresh)
                                }
                            }
                            item {
                                CodexWeeklyCard(
                                    metric = state.codexUsage?.weekly,
                                    useRelativeTime = state.useRelativeTime,
                                )
                            }
                        }

                        if (state.isGrokConfigured) {
                            state.grokError?.let { error ->
                                item {
                                    ProviderErrorBanner("Grok", error, viewModel::refresh)
                                }
                            }
                            item {
                                GrokWeeklyCard(
                                    metric = state.grokUsage?.weekly,
                                    useRelativeTime = state.useRelativeTime,
                                )
                            }
                        }

                        item {
                            DashboardMetaCard(
                                fetchedAt = oldestFetchedAt(
                                    claude = state.usage?.fetchedAt.takeIf { state.isClaudeConfigured },
                                    codex = state.codexUsage?.fetchedAt.takeIf { state.isCodexConfigured },
                                    grok = state.grokUsage?.fetchedAt.takeIf { state.isGrokConfigured },
                                ),
                                refreshIntervalSeconds = state.refreshIntervalSeconds,
                                isRefreshing = state.isLoading,
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderErrorBanner(
    provider: String,
    message: String,
    onRetry: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "$provider could not refresh",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2,
                )
            }
            TextButton(onClick = onRetry) { Text("Retry") }
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
            text = "Welcome to OpenUsage",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Connect Claude, Codex, Grok, or any combination in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGoToSettings) { Text("Connect a provider") }
    }
}

private fun UsageError.toUiMessage(): String = when (this) {
    is UsageError.Unauthorized -> "Session expired. Update it in Settings."
    is UsageError.RateLimited -> "Rate limited. Retrying shortly."
    is UsageError.NetworkError -> "Network error. Cached data is still shown."
    is UsageError.ServerError -> "Server error ($code)."
    is UsageError.NoCredentials -> "Not connected."
    is UsageError.Unknown -> "Something went wrong."
}

private fun oldestFetchedAt(
    claude: Instant?,
    codex: Instant?,
    grok: Instant?,
): Instant? = listOfNotNull(claude, codex, grok).minOrNull()
