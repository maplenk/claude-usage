package com.qbapps.claudeusage.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qbapps.claudeusage.data.local.SecureCredentialStore
import com.qbapps.claudeusage.data.local.UserPreferencesStore
import com.qbapps.claudeusage.domain.model.Organization
import com.qbapps.claudeusage.domain.model.UsageError
import com.qbapps.claudeusage.domain.repository.UsageRepository
import com.qbapps.claudeusage.domain.repository.CodexUsageRepository
import com.qbapps.claudeusage.domain.model.CodexDeviceCode
import com.qbapps.claudeusage.domain.model.GrokDeviceCode
import com.qbapps.claudeusage.domain.repository.GrokUsageRepository
import com.qbapps.claudeusage.data.repository.UsageApiException
import com.qbapps.claudeusage.notification.UsageNotificationHelper
import com.qbapps.claudeusage.worker.WorkManagerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
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
    val showPersistentNotification: Boolean = false,
    val useRelativeTime: Boolean = true,
    val isValidating: Boolean = false,
    val validationError: String? = null,
    val isKeyValidated: Boolean = false,
    val isCodexConnected: Boolean = false,
    val isCodexConnecting: Boolean = false,
    val codexDeviceCode: CodexDeviceCode? = null,
    val codexSignInError: String? = null,
    val isGrokConnected: Boolean = false,
    val isGrokConnecting: Boolean = false,
    val grokDeviceCode: GrokDeviceCode? = null,
    val grokSignInError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialStore: SecureCredentialStore,
    private val preferencesStore: UserPreferencesStore,
    private val repository: UsageRepository,
    private val codexRepository: CodexUsageRepository,
    private val grokRepository: GrokUsageRepository,
    private val workManagerScheduler: WorkManagerScheduler,
    private val notificationHelper: UsageNotificationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var codexLoginJob: Job? = null
    private var grokLoginJob: Job? = null

    init {
        loadExistingSettings()
    }

    fun updateSessionKeyInput(key: String) {
        _uiState.update { it.copy(sessionKeyInput = key, validationError = null) }
    }

    fun connectCodex() {
        codexLoginJob?.cancel()
        codexLoginJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCodexConnecting = true,
                    codexDeviceCode = null,
                    codexSignInError = null,
                )
            }
            val deviceCode = codexRepository.startDeviceLogin().getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isCodexConnecting = false,
                        codexSignInError = error.message ?: "Codex sign-in could not start.",
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(codexDeviceCode = deviceCode) }

            codexRepository.completeDeviceLogin(deviceCode).fold(
                onSuccess = {
                    val interval = preferencesStore.refreshIntervalSeconds.first()
                    workManagerScheduler.scheduleSync(interval)
                    workManagerScheduler.schedulePeriodicFallback()
                    _uiState.update {
                        it.copy(
                            isCodexConnected = true,
                            isCodexConnecting = false,
                            codexDeviceCode = null,
                            codexSignInError = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCodexConnecting = false,
                            codexDeviceCode = null,
                            codexSignInError = error.message ?: "Codex sign-in failed.",
                        )
                    }
                },
            )
        }
    }

    fun cancelCodexConnection() {
        codexLoginJob?.cancel()
        codexLoginJob = null
        _uiState.update {
            it.copy(
                isCodexConnecting = false,
                codexDeviceCode = null,
                codexSignInError = null,
            )
        }
    }

    fun disconnectCodex() {
        codexLoginJob?.cancel()
        viewModelScope.launch {
            codexRepository.disconnect()
            _uiState.update {
                it.copy(
                    isCodexConnected = false,
                    isCodexConnecting = false,
                    codexDeviceCode = null,
                    codexSignInError = null,
                )
            }
        }
    }

    fun connectGrok() {
        grokLoginJob?.cancel()
        grokLoginJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGrokConnecting = true,
                    grokDeviceCode = null,
                    grokSignInError = null,
                )
            }
            val deviceCode = grokRepository.startDeviceLogin().getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isGrokConnecting = false,
                        grokSignInError = error.message ?: "Grok sign-in could not start.",
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(grokDeviceCode = deviceCode) }

            grokRepository.completeDeviceLogin(deviceCode).fold(
                onSuccess = {
                    val interval = preferencesStore.refreshIntervalSeconds.first()
                    workManagerScheduler.scheduleSync(interval)
                    workManagerScheduler.schedulePeriodicFallback()
                    _uiState.update {
                        it.copy(
                            isGrokConnected = true,
                            isGrokConnecting = false,
                            grokDeviceCode = null,
                            grokSignInError = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGrokConnecting = false,
                            grokDeviceCode = null,
                            grokSignInError = error.message ?: "Grok sign-in failed.",
                        )
                    }
                },
            )
        }
    }

    fun cancelGrokConnection() {
        grokLoginJob?.cancel()
        grokLoginJob = null
        _uiState.update {
            it.copy(
                isGrokConnecting = false,
                grokDeviceCode = null,
                grokSignInError = null,
            )
        }
    }

    fun disconnectGrok() {
        grokLoginJob?.cancel()
        viewModelScope.launch {
            grokRepository.disconnect()
            _uiState.update {
                it.copy(
                    isGrokConnected = false,
                    isGrokConnecting = false,
                    grokDeviceCode = null,
                    grokSignInError = null,
                )
            }
        }
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

    fun togglePersistentNotification(enabled: Boolean) {
        _uiState.update { it.copy(showPersistentNotification = enabled) }
        viewModelScope.launch {
            preferencesStore.saveShowPersistentNotification(enabled)
            if (!enabled) {
                notificationHelper.cancelPersistentNotification()
            }
        }
    }

    fun toggleUseRelativeTime(enabled: Boolean) {
        _uiState.update { it.copy(useRelativeTime = enabled) }
        viewModelScope.launch {
            preferencesStore.saveUseRelativeTime(enabled)
        }
    }

    fun clearData() {
        credentialStore.clear()
        workManagerScheduler.cancelAll()
        notificationHelper.cancelPersistentNotification()
        viewModelScope.launch {
            preferencesStore.saveSelectedOrgId(null)
            repository.clearCachedData()
            codexRepository.disconnect()
            grokRepository.disconnect()
        }
        _uiState.update {
            SettingsUiState(
                refreshInterval = it.refreshInterval,
                notifyOnReset = it.notifyOnReset,
                notifyOnUsageThresholds = it.notifyOnUsageThresholds,
            )
        }
    }

    fun clearUsageHistory() {
        viewModelScope.launch {
            repository.clearUsageHistory()
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
                isCodexConnected = codexRepository.isAuthenticated(),
                isGrokConnected = grokRepository.isAuthenticated(),
            )
        }

        viewModelScope.launch {
            val interval = preferencesStore.refreshIntervalSeconds.first()
            val notifyOnReset = preferencesStore.notifyOnSessionReset.first()
            val notifyOnUsageThresholds = preferencesStore.notifyOnUsageThresholds.first()
            val showPersistent = preferencesStore.showPersistentNotification.first()
            val useRelativeTime = preferencesStore.useRelativeTime.first()
            _uiState.update {
                it.copy(
                    refreshInterval = interval,
                    notifyOnReset = notifyOnReset,
                    notifyOnUsageThresholds = notifyOnUsageThresholds,
                    showPersistentNotification = showPersistent,
                    useRelativeTime = useRelativeTime,
                )
            }
        }
    }

    private fun maskKey(key: String): String {
        if (key.length <= 8) return "****"
        return key.take(7) + "*".repeat(key.length - 11) + key.takeLast(4)
    }
}
