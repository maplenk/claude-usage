package com.qbapps.claudeusage.domain.repository

import com.qbapps.claudeusage.domain.model.CodexDeviceCode
import com.qbapps.claudeusage.domain.model.CodexUsage
import kotlinx.coroutines.flow.Flow

interface CodexUsageRepository {
    val cachedUsage: Flow<CodexUsage?>

    fun isAuthenticated(): Boolean
    suspend fun startDeviceLogin(): Result<CodexDeviceCode>
    suspend fun completeDeviceLogin(deviceCode: CodexDeviceCode): Result<CodexUsage>
    suspend fun fetchWeekly(urgent: Boolean = false): Result<CodexUsage>
    suspend fun disconnect()
    suspend fun clearCachedData()
}
