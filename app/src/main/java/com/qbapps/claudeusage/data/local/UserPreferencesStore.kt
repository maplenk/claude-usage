package com.qbapps.claudeusage.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

/**
 * Non-sensitive user preferences such as refresh interval and the
 * selected organization. Stored in a plain Preferences DataStore.
 */
@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val refreshIntervalKey = intPreferencesKey("refresh_interval_seconds")
    private val selectedOrgIdKey = stringPreferencesKey("selected_org_id")
    private val notifyOnResetKey = booleanPreferencesKey("notify_on_session_reset")
    private val notifyOnUsageThresholdsKey = booleanPreferencesKey("notify_on_usage_thresholds")
    private val lastNotifiedSessionThresholdKey = intPreferencesKey("last_notified_session_threshold")

    val refreshIntervalSeconds: Flow<Int> = context.userPreferencesDataStore.data
        .map { prefs -> prefs[refreshIntervalKey] ?: DEFAULT_REFRESH_INTERVAL_SECONDS }

    val selectedOrgId: Flow<String?> = context.userPreferencesDataStore.data
        .map { prefs -> prefs[selectedOrgIdKey] }

    val notifyOnSessionReset: Flow<Boolean> = context.userPreferencesDataStore.data
        .map { prefs -> prefs[notifyOnResetKey] ?: true }

    val notifyOnUsageThresholds: Flow<Boolean> = context.userPreferencesDataStore.data
        .map { prefs -> prefs[notifyOnUsageThresholdsKey] ?: true }

    val lastNotifiedSessionThreshold: Flow<Int?> = context.userPreferencesDataStore.data
        .map { prefs -> prefs[lastNotifiedSessionThresholdKey] }

    suspend fun saveRefreshInterval(seconds: Int) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[refreshIntervalKey] = seconds
        }
    }

    suspend fun saveNotifyOnSessionReset(enabled: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[notifyOnResetKey] = enabled
        }
    }

    suspend fun saveNotifyOnUsageThresholds(enabled: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[notifyOnUsageThresholdsKey] = enabled
        }
    }

    suspend fun saveLastNotifiedSessionThreshold(threshold: Int?) {
        context.userPreferencesDataStore.edit { prefs ->
            if (threshold != null) {
                prefs[lastNotifiedSessionThresholdKey] = threshold
            } else {
                prefs.remove(lastNotifiedSessionThresholdKey)
            }
        }
    }

    suspend fun saveSelectedOrgId(orgId: String?) {
        context.userPreferencesDataStore.edit { prefs ->
            if (orgId != null) {
                prefs[selectedOrgIdKey] = orgId
            } else {
                prefs.remove(selectedOrgIdKey)
            }
        }
    }

    companion object {
        const val DEFAULT_REFRESH_INTERVAL_SECONDS = 30
    }
}
