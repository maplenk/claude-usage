package com.qbapps.claudeusage.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qbapps.claudeusage.data.local.SecureCredentialStore
import com.qbapps.claudeusage.data.local.UserPreferencesStore
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.domain.model.CodexUsage
import com.qbapps.claudeusage.domain.model.GrokUsage
import com.qbapps.claudeusage.data.repository.UsageApiException
import com.qbapps.claudeusage.domain.model.UsageError
import com.qbapps.claudeusage.domain.model.UsageHistoryPoint
import com.qbapps.claudeusage.domain.repository.UsageRepository
import com.qbapps.claudeusage.domain.repository.CodexUsageRepository
import com.qbapps.claudeusage.domain.repository.GrokUsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val usage: ClaudeUsage? = null,
    val codexUsage: CodexUsage? = null,
    val grokUsage: GrokUsage? = null,
    val history: List<UsageHistoryPoint> = emptyList(),
    val isLoading: Boolean = false,
    val error: UsageError? = null,
    val isConfigured: Boolean = false,
    val isClaudeConfigured: Boolean = false,
    val isCodexConfigured: Boolean = false,
    val isGrokConfigured: Boolean = false,
    val codexError: String? = null,
    val grokError: String? = null,
    val selectedOrgId: String? = null,
    val refreshIntervalSeconds: Int = UserPreferencesStore.DEFAULT_REFRESH_INTERVAL_SECONDS,
    val useRelativeTime: Boolean = true,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: UsageRepository,
    private val codexRepository: CodexUsageRepository,
    private val grokRepository: GrokUsageRepository,
    private val credentialStore: SecureCredentialStore,
    private val preferencesStore: UserPreferencesStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        checkConfiguration()
        observeCachedUsage()
        observeCachedCodexUsage()
        observeCachedGrokUsage()
        observeUsageHistory()
        observeUserPreferences()
        startRefreshLoop()
    }

    /** Public pull-to-refresh trigger. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchAndUpdate(urgent = true)
        }
    }

    /** Re-evaluates whether credentials are configured (e.g. after returning from Settings). */
    fun recheckConfiguration() {
        checkConfiguration()
        if (_uiState.value.isConfigured) {
            restartRefreshLoop()
        }
    }

    // ---- internal ----

    private fun checkConfiguration() {
        val hasKey = credentialStore.getSessionKey() != null
        val hasOrg = credentialStore.getOrgId() != null
        val hasClaude = hasKey && hasOrg
        val hasCodex = codexRepository.isAuthenticated()
        val hasGrok = grokRepository.isAuthenticated()
        _uiState.update {
            it.copy(
                isConfigured = hasClaude || hasCodex || hasGrok,
                isClaudeConfigured = hasClaude,
                isCodexConfigured = hasCodex,
                isGrokConfigured = hasGrok,
            )
        }
    }

    private fun observeCachedUsage() {
        viewModelScope.launch {
            repository.cachedUsage.collectLatest { cached ->
                _uiState.update { it.copy(usage = cached) }
            }
        }
    }

    private fun observeCachedCodexUsage() {
        viewModelScope.launch {
            codexRepository.cachedUsage.collectLatest { cached ->
                _uiState.update { it.copy(codexUsage = cached) }
            }
        }
    }

    private fun observeCachedGrokUsage() {
        viewModelScope.launch {
            grokRepository.cachedUsage.collectLatest { cached ->
                _uiState.update { it.copy(grokUsage = cached) }
            }
        }
    }

    private fun observeUsageHistory() {
        viewModelScope.launch {
            repository.usageHistory.collectLatest { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    private fun observeUserPreferences() {
        viewModelScope.launch {
            preferencesStore.selectedOrgId.collectLatest { orgId ->
                _uiState.update { it.copy(selectedOrgId = orgId) }
            }
        }
        viewModelScope.launch {
            preferencesStore.refreshIntervalSeconds.collectLatest { refreshSeconds ->
                _uiState.update { it.copy(refreshIntervalSeconds = refreshSeconds) }
            }
        }
        viewModelScope.launch {
            preferencesStore.useRelativeTime.collectLatest { useRelative ->
                _uiState.update { it.copy(useRelativeTime = useRelative) }
            }
        }
    }

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // Reactively restart when interval changes
            preferencesStore.refreshIntervalSeconds.collectLatest { intervalSeconds ->
                while (true) {
                    if (_uiState.value.isConfigured) {
                        fetchAndUpdate()
                    }
                    delay(intervalSeconds * 1_000L)
                }
            }
        }
    }

    private fun restartRefreshLoop() {
        startRefreshLoop()
    }

    private suspend fun fetchAndUpdate(urgent: Boolean = false) {
        val configuration = _uiState.value
        if (!configuration.isConfigured) return
        _uiState.update { it.copy(isLoading = true, error = null, codexError = null, grokError = null) }

        coroutineScope {
            val claudeDeferred = configuration.isClaudeConfigured.takeIf { it }?.let {
                async { repository.fetchUsage(urgent = urgent) }
            }
            val codexDeferred = configuration.isCodexConfigured.takeIf { it }?.let {
                async { codexRepository.fetchWeekly(urgent = urgent) }
            }
            val grokDeferred = configuration.isGrokConfigured.takeIf { it }?.let {
                async { grokRepository.fetchWeekly(urgent = urgent) }
            }
            val claudeResult = claudeDeferred?.await()
            val codexResult = codexDeferred?.await()
            val grokResult = grokDeferred?.await()

            val claudeError = claudeResult?.exceptionOrNull()?.let { throwable ->
                when (throwable) {
                    is UsageApiException -> throwable.error
                    else -> UsageError.Unknown(throwable)
                }
            }
            _uiState.update {
                it.copy(
                    usage = claudeResult?.getOrNull() ?: it.usage,
                    codexUsage = codexResult?.getOrNull() ?: it.codexUsage,
                    grokUsage = grokResult?.getOrNull() ?: it.grokUsage,
                    isLoading = false,
                    error = claudeError,
                    codexError = codexResult?.exceptionOrNull()?.message,
                    grokError = grokResult?.exceptionOrNull()?.message,
                )
            }
        }
    }
}
