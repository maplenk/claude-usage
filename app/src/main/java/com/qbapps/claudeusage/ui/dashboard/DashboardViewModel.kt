package com.qbapps.claudeusage.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qbapps.claudeusage.data.local.SecureCredentialStore
import com.qbapps.claudeusage.data.local.UserPreferencesStore
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.data.repository.UsageApiException
import com.qbapps.claudeusage.domain.model.UsageError
import com.qbapps.claudeusage.domain.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val isLoading: Boolean = false,
    val error: UsageError? = null,
    val isConfigured: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: UsageRepository,
    private val credentialStore: SecureCredentialStore,
    private val preferencesStore: UserPreferencesStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        checkConfiguration()
        observeCachedUsage()
        startRefreshLoop()
    }

    /** Public pull-to-refresh trigger. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchAndUpdate()
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
        _uiState.update { it.copy(isConfigured = hasKey && hasOrg) }
    }

    private fun observeCachedUsage() {
        viewModelScope.launch {
            repository.cachedUsage.collectLatest { cached ->
                _uiState.update { it.copy(usage = cached) }
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

    private suspend fun fetchAndUpdate() {
        _uiState.update { it.copy(isLoading = true) }
        val result = repository.fetchUsage()
        result.fold(
            onSuccess = { usage ->
                _uiState.update {
                    it.copy(
                        usage = usage,
                        isLoading = false,
                        error = null,
                    )
                }
            },
            onFailure = { throwable ->
                val usageError = when (throwable) {
                    is UsageApiException -> throwable.error
                    else -> UsageError.Unknown(throwable)
                }
                _uiState.update {
                    it.copy(isLoading = false, error = usageError)
                }
            },
        )
    }
}

