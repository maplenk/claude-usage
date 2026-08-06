package com.qbapps.claudeusage.domain.repository

import com.qbapps.claudeusage.domain.model.GrokDeviceCode
import com.qbapps.claudeusage.domain.model.GrokUsage
import kotlinx.coroutines.flow.Flow

interface GrokUsageRepository {
    val cachedUsage: Flow<GrokUsage?>

    fun isAuthenticated(): Boolean
    suspend fun startDeviceLogin(): Result<GrokDeviceCode>
    suspend fun completeDeviceLogin(deviceCode: GrokDeviceCode): Result<GrokUsage>
    suspend fun fetchWeekly(urgent: Boolean = false): Result<GrokUsage>
    suspend fun disconnect()
    suspend fun clearCachedData()
}
