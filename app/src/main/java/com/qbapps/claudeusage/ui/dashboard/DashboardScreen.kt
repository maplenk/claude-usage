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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qbapps.claudeusage.R
import com.qbapps.claudeusage.domain.model.UsageError
import com.qbapps.claudeusage.ui.dashboard.components.CodexWeeklyCard
import com.qbapps.claudeusage.ui.dashboard.components.DashboardMetaCard
import com.qbapps.claudeusage.ui.dashboard.components.GrokWeeklyCard
import com.qbapps.claudeusage.ui.dashboard.components.SessionGuardrailCard
import com.qbapps.claudeusage.ui.dashboard.components.SessionHeroCard
import com.qbapps.claudeusage.ui.theme.OpenUsageShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    focusProvider: String? = null,
    focusRequest: Int = 0,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.recheckConfiguration() }
    LaunchedEffect(focusRequest, state.isLoading) {
        if (focusRequest > 0 && !state.isLoading && focusProvider != null) {
            providerItemIndex(state, focusProvider)?.let { index ->
                listState.animateScrollToItem(index)
            }
        }
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
                            enabled = !state.isLoading && !state.syncState.isOffline,
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
                isRefreshing = state.isLoading && !state.syncState.isOffline,
                onRefresh = {
                    if (!state.syncState.isOffline) viewModel.refresh()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                val allDataMissing = state.usage == null &&
                    state.codexUsage == null &&
                    state.grokUsage == null
                if (allDataMissing && state.isLoading) {
                    DashboardSkeleton(state = state)
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.syncState !is SyncState.Fresh) {
                            item {
                                SyncStateBanner(
                                    syncState = state.syncState,
                                    onRetry = viewModel::refresh,
                                )
                            }
                        }

                        if (state.isClaudeConfigured) {
                            state.error?.let { error ->
                                item {
                                    ProviderErrorBanner(
                                        provider = "Claude",
                                        message = error.toUiMessage(),
                                        onRetry = viewModel::refresh,
                                        onOpenSettings = onNavigateToSettings,
                                    )
                                }
                            }
                            item {
                                SessionHeroCard(
                                    metric = state.usage?.fiveHour,
                                    weeklyMetric = state.usage?.sevenDay,
                                    useRelativeTime = state.useRelativeTime,
                                    isStale = state.syncState.isStale,
                                )
                            }
                            if (!state.syncState.isStale) item {
                                SessionGuardrailCard(
                                    metric = state.usage?.fiveHour,
                                    history = state.history,
                                )
                            }
                        }

                        if (state.isCodexConfigured) {
                            state.codexError?.let { error ->
                                item {
                                    ProviderErrorBanner(
                                        provider = "Codex",
                                        message = error,
                                        onRetry = viewModel::refresh,
                                        onOpenSettings = onNavigateToSettings,
                                    )
                                }
                            }
                            item {
                                CodexWeeklyCard(
                                    metric = state.codexUsage?.weekly,
                                    useRelativeTime = state.useRelativeTime,
                                    isStale = state.syncState.isStale,
                                )
                            }
                        }

                        if (state.isGrokConfigured) {
                            state.grokError?.let { error ->
                                item {
                                    ProviderErrorBanner(
                                        provider = "Grok",
                                        message = error,
                                        onRetry = viewModel::refresh,
                                        onOpenSettings = onNavigateToSettings,
                                    )
                                }
                            }
                            item {
                                GrokWeeklyCard(
                                    metric = state.grokUsage?.weekly,
                                    useRelativeTime = state.useRelativeTime,
                                    isStale = state.syncState.isStale,
                                )
                            }
                        }

                        item {
                            DashboardMetaCard(
                                syncState = state.syncState,
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
private fun DashboardSkeleton(state: DashboardUiState) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.isClaudeConfigured) {
            item { SkeletonUsageCard(height = 236) }
        }
        if (state.isCodexConfigured) {
            item { SkeletonUsageCard(height = 176) }
        }
        if (state.isGrokConfigured) {
            item { SkeletonUsageCard(height = 176) }
        }
    }
}

@Composable
private fun SkeletonUsageCard(height: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp),
        shape = OpenUsageShape.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SkeletonBlock(Modifier.width(126.dp).height(16.dp))
                SkeletonBlock(Modifier.width(76.dp).height(26.dp))
            }
            SkeletonBlock(Modifier.width(148.dp).height(58.dp))
            SkeletonBlock(Modifier.fillMaxWidth().height(10.dp))
            SkeletonBlock(Modifier.fillMaxWidth().height(1.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SkeletonBlock(Modifier.width(68.dp).height(14.dp))
                SkeletonBlock(Modifier.width(92.dp).height(18.dp))
            }
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
private fun SyncStateBanner(
    syncState: SyncState,
    onRetry: () -> Unit,
) {
    if (syncState is SyncState.Fresh) return
    val title: String
    val message: String
    when (syncState) {
        is SyncState.Ageing -> {
            title = "Sync is taking longer"
            message = "Last successful update was ${syncState.ageMinutes.ageText()}."
        }
        is SyncState.Stale -> {
            title = "Usage data may be out of date"
            message = "Showing cached values from ${syncState.ageMinutes.ageText()}."
        }
        is SyncState.Offline -> {
            title = "You're offline"
            message = if (syncState.fetchedAt == null) {
                "Connect to the internet to load usage."
            } else {
                "Showing the last data seen ${syncState.ageMinutes.ageText()}."
            }
        }
        is SyncState.Fresh -> return
    }

    Card(
        shape = OpenUsageShape.tile,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_guardrail_unknown),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onRetry,
                enabled = syncState !is SyncState.Offline,
            ) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun ProviderErrorBanner(
    provider: String,
    message: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
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
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onOpenSettings) {
                    Text(if (provider == "Claude") "Update key" else "Sign in")
                }
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

private fun Long?.ageText(): String = when {
    this == null -> "an unknown time ago"
    this < 1L -> "just now"
    this < 60L -> "${this}m ago"
    this < 1_440L -> "${this / 60L}h ago"
    else -> "${this / 1_440L}d ago"
}

private fun providerItemIndex(
    state: DashboardUiState,
    provider: String,
): Int? {
    var index = if (state.syncState is SyncState.Fresh) 0 else 1

    if (state.isClaudeConfigured) {
        if (state.error != null) index += 1
        val claudeIndex = index
        index += 1
        if (!state.syncState.isStale) index += 1
        if (provider.equals("Claude", ignoreCase = true)) return claudeIndex
    }

    if (state.isCodexConfigured) {
        if (state.codexError != null) index += 1
        val codexIndex = index
        index += 1
        if (provider.equals("Codex", ignoreCase = true)) return codexIndex
    }

    if (state.isGrokConfigured) {
        if (state.grokError != null) index += 1
        val grokIndex = index
        if (provider.equals("Grok", ignoreCase = true)) return grokIndex
    }

    return null
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
