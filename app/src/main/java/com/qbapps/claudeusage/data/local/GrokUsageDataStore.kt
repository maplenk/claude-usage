package com.qbapps.claudeusage.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qbapps.claudeusage.domain.model.GrokUsage
import com.qbapps.claudeusage.domain.model.UsageMetric
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.grokUsageDataStore by preferencesDataStore(name = "grok_usage_cache")

@Singleton
class GrokUsageDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val weeklyUtilization = doublePreferencesKey("weekly_utilization")
    private val weeklyResetAtMs = longPreferencesKey("weekly_reset_at_ms")
    private val fetchedAtMs = longPreferencesKey("fetched_at_ms")

    val cachedUsage: Flow<GrokUsage?> = context.grokUsageDataStore.data.map { preferences ->
        val utilization = preferences[weeklyUtilization] ?: return@map null
        val fetchedAt = preferences[fetchedAtMs] ?: return@map null
        GrokUsage(
            weekly = UsageMetric(
                utilization = utilization,
                resetsAt = preferences[weeklyResetAtMs]?.let(Instant::ofEpochMilli),
            ),
            fetchedAt = Instant.ofEpochMilli(fetchedAt),
        )
    }

    suspend fun save(usage: GrokUsage) {
        context.grokUsageDataStore.edit { preferences ->
            preferences[weeklyUtilization] = usage.weekly.utilization
            preferences[fetchedAtMs] = usage.fetchedAt.toEpochMilli()
            usage.weekly.resetsAt?.let {
                preferences[weeklyResetAtMs] = it.toEpochMilli()
            } ?: preferences.remove(weeklyResetAtMs)
        }
    }

    suspend fun clear() {
        context.grokUsageDataStore.edit { it.clear() }
    }
}
