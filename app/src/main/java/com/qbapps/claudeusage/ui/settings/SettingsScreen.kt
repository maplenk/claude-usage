package com.qbapps.claudeusage.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qbapps.claudeusage.R
import com.qbapps.claudeusage.ui.settings.components.RefreshIntervalSlider
import com.qbapps.claudeusage.ui.settings.components.SessionKeyInput
import com.qbapps.claudeusage.worker.SyncLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsSectionCard(title = "Authentication") {
                    if (state.maskedSessionKey != null) {
                        Text(
                            text = "Current key: ${state.maskedSessionKey}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    SessionKeyInput(
                        value = state.sessionKeyInput,
                        onValueChange = viewModel::updateSessionKeyInput,
                        onValidate = viewModel::validateAndSaveKey,
                        isValidating = state.isValidating,
                        errorMessage = state.validationError,
                    )
                }
            }

            if (state.isKeyValidated && state.organizations.isNotEmpty()) {
                item {
                    SettingsSectionCard(title = "Organization") {
                        OrganizationDropdown(
                            organizations = state.organizations,
                            selectedOrgId = state.selectedOrgId,
                            onSelect = viewModel::selectOrganization,
                        )
                    }
                }
            }

            item {
                SettingsSectionCard(title = "Refresh & Notifications") {
                    RefreshIntervalSlider(
                        value = state.refreshInterval,
                        onValueChange = viewModel::updateRefreshInterval,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Notify when session resets",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Switch(
                            checked = state.notifyOnReset,
                            onCheckedChange = viewModel::toggleNotifyOnReset,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.notify_usage_milestones),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Switch(
                            checked = state.notifyOnUsageThresholds,
                            onCheckedChange = viewModel::toggleNotifyOnUsageThresholds,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Persistent status bar",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "Show ongoing notification with session usage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.showPersistentNotification,
                            onCheckedChange = viewModel::togglePersistentNotification,
                        )
                    }
                }
            }

            item {
                SettingsSectionCard(title = "Display") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Relative countdown",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = if (state.useRelativeTime) "\"Resets in 2h 34m\"" else "\"Resets Mon, 2:30 PM\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.useRelativeTime,
                            onCheckedChange = viewModel::toggleUseRelativeTime,
                        )
                    }
                }
            }

            item {
                SettingsSectionCard(title = "Widget Preferences") {
                    BatteryOptimizationSection()
                }
            }

            item {
                SettingsSectionCard(title = "Advanced") {
                    Text(
                        text = "Debug and maintenance tools are hidden by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (showAdvanced) "Hide Advanced Tools" else "Show Advanced Tools")
                    }
                    AnimatedVisibility(visible = showAdvanced) {
                        Column(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Usage history powers pace baselines and cap predictions (retained for up to 30 days).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = { showClearHistoryDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Clear Usage History")
                            }
                            ExportLogSection(showDescription = false)
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(title = "Danger Zone") {
                    Text(
                        text = "Clear all local data and reset app configuration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("Clear All Data")
                    }
                }
            }
        }
    }

    // --- Clear Data Confirmation Dialog ---
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all data?") },
            text = {
                Text("This will remove your session key, selected organization, cached usage data, and usage history. You will need to reconfigure the app.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearData()
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear usage history?") },
            text = {
                Text("This removes stored session baseline and prediction history. New history will start collecting on the next refresh.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearUsageHistory()
                    showClearHistoryDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                content()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizationDropdown(
    organizations: List<com.qbapps.claudeusage.domain.model.Organization>,
    selectedOrgId: String?,
    onSelect: (com.qbapps.claudeusage.domain.model.Organization) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOrg = organizations.find { it.uuid == selectedOrgId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedOrg?.name ?: "Select an organization",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            label = { Text("Organization") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            organizations.forEach { org ->
                DropdownMenuItem(
                    text = { Text(org.name) },
                    onClick = {
                        onSelect(org)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun BatteryOptimizationSection() {
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isOptimized by remember {
        mutableStateOf(!powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    Column {
        Text(
            text = if (isOptimized) {
                "Battery optimization is ON. Android may delay or skip background refreshes. Disable it for reliable widget updates."
            } else {
                "Battery optimization is disabled for this app. Background refreshes should work reliably."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isOptimized) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                    // Re-check after returning (approximate; lifecycle-aware would be better)
                    isOptimized = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Disable Battery Optimization")
            }
        }
    }
}

@Composable
private fun ExportLogSection(
    showDescription: Boolean = true,
) {
    val context = LocalContext.current

    Column {
        if (showDescription) {
            Text(
                text = "Export the background sync debug log for troubleshooting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = {
                val logText = SyncLog.getLog(context)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Claude Usage - Sync Debug Log")
                    putExtra(Intent.EXTRA_TEXT, logText)
                }
                context.startActivity(Intent.createChooser(intent, "Share debug log"))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Export Debug Log")
        }
    }
}
