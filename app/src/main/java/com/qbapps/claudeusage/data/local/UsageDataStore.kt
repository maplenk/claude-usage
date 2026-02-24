package com.qbapps.claudeusage.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.domain.model.UsageMetric
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "usage_cache"
)

/**
 * Caches the most recent usage data locally using Preferences DataStore
 * so the UI can display data immediately while a fresh fetch is in progress.
 */
@Singleton
class UsageDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // --- Five-hour metric keys ---
    private val fiveHourUtilization = doublePreferencesKey("five_hour_utilization")
    private val fiveHourResetsAt = stringPreferencesKey("five_hour_resets_at")

    // --- Seven-day metric keys ---
    private val sevenDayUtilization = doublePreferencesKey("seven_day_utilization")
    private val sevenDayResetsAt = stringPreferencesKey("seven_day_resets_at")

    // --- Seven-day Opus metric keys ---
    private val sevenDayOpusUtilization = doublePreferencesKey("seven_day_opus_utilization")
    private val sevenDayOpusResetsAt = stringPreferencesKey("seven_day_opus_resets_at")

    // --- Seven-day Sonnet metric keys ---
    private val sevenDaySonnetUtilization = doublePreferencesKey("seven_day_sonnet_utilization")
    private val sevenDaySonnetResetsAt = stringPreferencesKey("seven_day_sonnet_resets_at")

    // --- Fetch timestamp ---
    private val fetchedAtEpochMillis = longPreferencesKey("fetched_at_epoch_millis")

    // --- Has data flag (to distinguish "never fetched" from "all nulls") ---
    private val hasData = stringPreferencesKey("has_data")

    val cachedUsage: Flow<ClaudeUsage?> = context.usageDataStore.data.map { prefs ->
        if (prefs[hasData] == null) return@map null

        ClaudeUsage(
            fiveHour = prefs.readMetric(fiveHourUtilization, fiveHourResetsAt),
            sevenDay = prefs.readMetric(sevenDayUtilization, sevenDayResetsAt),
            sevenDayOpus = prefs.readMetric(sevenDayOpusUtilization, sevenDayOpusResetsAt),
            sevenDaySonnet = prefs.readMetric(sevenDaySonnetUtilization, sevenDaySonnetResetsAt),
            fetchedAt = prefs[fetchedAtEpochMillis]?.let { Instant.ofEpochMilli(it) }
                ?: Instant.now()
        )
    }

    suspend fun save(usage: ClaudeUsage) {
        context.usageDataStore.edit { prefs ->
            prefs[hasData] = "true"
            prefs[fetchedAtEpochMillis] = usage.fetchedAt.toEpochMilli()

            prefs.writeMetric(
                usage.fiveHour,
                fiveHourUtilization,
                fiveHourResetsAt
            )
            prefs.writeMetric(
                usage.sevenDay,
                sevenDayUtilization,
                sevenDayResetsAt
            )
            prefs.writeMetric(
                usage.sevenDayOpus,
                sevenDayOpusUtilization,
                sevenDayOpusResetsAt
            )
            prefs.writeMetric(
                usage.sevenDaySonnet,
                sevenDaySonnetUtilization,
                sevenDaySonnetResetsAt
            )
        }
    }

    // ---- Private helpers ----

    private fun Preferences.readMetric(
        utilizationKey: Preferences.Key<Double>,
        resetsAtKey: Preferences.Key<String>
    ): UsageMetric? {
        val utilization = this[utilizationKey] ?: return null
        val resetsAt = this[resetsAtKey]?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return UsageMetric(utilization = utilization, resetsAt = resetsAt)
    }

    private fun MutablePreferences.writeMetric(
        metric: UsageMetric?,
        utilizationKey: Preferences.Key<Double>,
        resetsAtKey: Preferences.Key<String>
    ) {
        if (metric != null) {
            this[utilizationKey] = metric.utilization
            if (metric.resetsAt != null) {
                this[resetsAtKey] = metric.resetsAt.toString()
            } else {
                remove(resetsAtKey)
            }
        } else {
            remove(utilizationKey)
            remove(resetsAtKey)
        }
    }
}
