package com.qbapps.claudeusage.domain.repository

import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.domain.model.Organization
import com.qbapps.claudeusage.domain.model.UsageHistoryPoint
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    val cachedUsage: Flow<ClaudeUsage?>
    val usageHistory: Flow<List<UsageHistoryPoint>>
    suspend fun fetchUsage(): Result<ClaudeUsage>
    suspend fun fetchOrganizations(sessionKey: String): Result<List<Organization>>
    suspend fun validateSessionKey(sessionKey: String): Result<List<Organization>>
    suspend fun clearUsageHistory()
    suspend fun clearCachedData()
}
