package com.qbapps.claudeusage.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.domain.model.UsageMetric

/**
 * Pushes [ClaudeUsage] data into every [UsageWidget] instance's Glance
 * Preferences state and then triggers a UI update.
 *
 * Call this after every successful fetch so the widget shows fresh data.
 */
suspend fun pushDataToWidgets(context: Context, usage: ClaudeUsage) {
    val manager = GlanceAppWidgetManager(context)
    val glanceIds = manager.getGlanceIds(UsageWidget::class.java)

    for (glanceId in glanceIds) {
        updateAppWidgetState(context, UsageWidgetStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[UsageWidget.HAS_DATA] = "true"
                this[UsageWidget.FETCHED_AT] = usage.fetchedAt.toEpochMilli()

                writeMetric(this, usage.fiveHour, UsageWidget.FIVE_HOUR_UTIL, UsageWidget.FIVE_HOUR_RESET)
                writeMetric(this, usage.sevenDay, UsageWidget.SEVEN_DAY_UTIL, UsageWidget.SEVEN_DAY_RESET)
                writeMetric(this, usage.sevenDayOpus, UsageWidget.OPUS_UTIL, UsageWidget.OPUS_RESET)
                writeMetric(this, usage.sevenDaySonnet, UsageWidget.SONNET_UTIL, UsageWidget.SONNET_RESET)
            }
        }
    }

    // Trigger UI refresh for all widget instances
    UsageWidget().updateAll(context)
}

private fun writeMetric(
    prefs: MutablePreferences,
    metric: UsageMetric?,
    utilKey: androidx.datastore.preferences.core.Preferences.Key<Double>,
    resetKey: androidx.datastore.preferences.core.Preferences.Key<String>
) {
    if (metric != null) {
        prefs[utilKey] = metric.utilization
        if (metric.resetsAt != null) {
            prefs[resetKey] = metric.resetsAt.toString()
        } else {
            prefs.remove(resetKey)
        }
    } else {
        prefs.remove(utilKey)
        prefs.remove(resetKey)
    }
}
