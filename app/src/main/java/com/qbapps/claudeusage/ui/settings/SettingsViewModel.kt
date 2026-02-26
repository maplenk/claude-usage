package com.qbapps.claudeusage.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qbapps.claudeusage.data.local.SecureCredentialStore
import com.qbapps.claudeusage.data.local.UserPreferencesStore
import com.qbapps.claudeusage.domain.model.Organization
import com.qbapps.claudeusage.domain.model.UsageError
import com.qbapps.claudeusage.domain.repository.UsageRepository
import com.qbapps.claudeusage.data.repository.UsageApiException
import com.qbapps.claudeusage.worker.WorkManagerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val sessionKeyInput: String = "",
    val maskedSessionKey: String? = null,
    val organizations: List<Organization> = emptyList(),
    val selectedOrgId: String? = null,
    val refreshInterval: Int = UserPreferencesStore.DEFAULT_REFRESH_INTERVAL_SECONDS,
    val notifyOnReset: Boolean = true,
    val notifyOnUsageThresholds: Boolean = true,
    val isValidating: Boolean = false,
    val validationError: String? = null,
    val isKeyValidated: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialStore: SecureCredentialStore,
    private val preferencesStore: UserPreferencesStore,
    private val repository: UsageRepository,
    private val workManagerScheduler: WorkManagerScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadExistingSettings()
    }

    fun updateSessionKeyInput(key: String) {
        _uiState.update { it.copy(sessionKeyInput = key, validationError = null) }
    }

    fun validateAndSaveKey() {
        val key = _uiState.value.sessionKeyInput.trim()
        if (key.isBlank()) return

        if (!key.startsWith("sk-ant-")) {
            _uiState.update { it.copy(validationError = "Key must start with sk-ant-") }
            return
        }
        if (key.length < 40) {
            _uiState.update { it.copy(validationError = "Key appears too short") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isValidating = true, validationError = null) }

            val result = repository.validateSessionKey(key)
            result.fold(
                onSuccess = { orgs ->
                    credentialStore.saveSessionKey(key)
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            maskedSessionKey = maskKey(key),
                            organizations = orgs,
                            isKeyValidated = true,
                            validationError = null,
                            // Auto-select first org if only one
                            selectedOrgId = if (orgs.size == 1) orgs.first().uuid else it.selectedOrgId,
                        )
                    }
                    // Auto-save if single org
                    if (orgs.size == 1) {
                        selectOrganization(orgs.first())
                    }
                },
                onFailure = { throwable ->
                    val message = when (throwable) {
                        is UsageApiException -> when (throwable.error) {
                            is UsageError.Unauthorized -> "Invalid session key."
                            is UsageError.NetworkError -> "Network error. Check your connection."
                            is UsageError.RateLimited -> "Rate limited. Try again later."
                            is UsageError.ServerError -> "Server error. Try again later."
                            else -> "Validation failed."
                        }
                        else -> "Validation failed: ${throwable.message ?: "Unknown error"}"
                    }
                    _uiState.update {
                        it.copy(isValidating = false, validationError = message)
                    }
                },
            )
        }
    }

    fun selectOrganization(org: Organization) {
        credentialStore.saveOrgId(org.uuid)
        viewModelScope.launch {
            preferencesStore.saveSelectedOrgId(org.uuid)
            // Start background sync now that configuration is complete
            val interval = preferencesStore.refreshIntervalSeconds.first()
            workManagerScheduler.scheduleSync(interval)
            workManagerScheduler.schedulePeriodicFallback()
        }
        _uiState.update { it.copy(selectedOrgId = org.uuid) }
    }

    fun updateRefreshInterval(seconds: Int) {
        _uiState.update { it.copy(refreshInterval = seconds) }
        viewModelScope.launch {
            preferencesStore.saveRefreshInterval(seconds)
            // Reschedule background sync with new interval
            workManagerScheduler.scheduleSync(seconds)
        }
    }

    fun toggleNotifyOnReset(enabled: Boolean) {
        _uiState.update { it.copy(notifyOnReset = enabled) }
        viewModelScope.launch {
            preferencesStore.saveNotifyOnSessionReset(enabled)
        }
    }

    fun toggleNotifyOnUsageThresholds(enabled: Boolean) {
        _uiState.update { it.copy(notifyOnUsageThresholds = enabled) }
        viewModelScope.launch {
            preferencesStore.saveNotifyOnUsageThresholds(enabled)
        }
    }

    fun clearData() {
        credentialStore.clear()
        workManagerScheduler.cancelAll()
        viewModelScope.launch {
            preferencesStore.saveSelectedOrgId(null)
        }
        _uiState.update {
            SettingsUiState(
                refreshInterval = it.refreshInterval,
                notifyOnReset = it.notifyOnReset,
                notifyOnUsageThresholds = it.notifyOnUsageThresholds,
            )
        }
    }

    // ---- internal ----

    private fun loadExistingSettings() {
        val existingKey = credentialStore.getSessionKey()
        val existingOrg = credentialStore.getOrgId()

        _uiState.update {
            it.copy(
                maskedSessionKey = existingKey?.let(::maskKey),
                selectedOrgId = existingOrg,
                isKeyValidated = existingKey != null,
            )
        }

        viewModelScope.launch {
            val interval = preferencesStore.refreshIntervalSeconds.first()
            val notifyOnReset = preferencesStore.notifyOnSessionReset.first()
            val notifyOnUsageThresholds = preferencesStore.notifyOnUsageThresholds.first()
            _uiState.update {
                it.copy(
                    refreshInterval = interval,
                    notifyOnReset = notifyOnReset,
                    notifyOnUsageThresholds = notifyOnUsageThresholds,
                )
            }
        }
    }

    private fun maskKey(key: String): String {
        if (key.length <= 8) return "****"
        return key.take(7) + "*".repeat(key.length - 11) + key.takeLast(4)
    }
}
