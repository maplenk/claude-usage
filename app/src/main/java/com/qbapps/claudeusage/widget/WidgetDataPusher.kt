package com.qbapps.claudeusage.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.domain.model.CodexUsage
import com.qbapps.claudeusage.domain.model.GrokUsage
import com.qbapps.claudeusage.domain.model.UsageMetric

suspend fun pushDataToWidgets(context: Context, usage: ClaudeUsage) {
    updateWidgetStates(context) {
        this[UsageWidget.HAS_DATA] = "true"
        this[UsageWidget.FETCHED_AT] = usage.fetchedAt.toEpochMilli()
        writeMetric(usage.fiveHour, UsageWidget.FIVE_HOUR_UTIL, UsageWidget.FIVE_HOUR_RESET)
        writeMetric(usage.sevenDay, UsageWidget.SEVEN_DAY_UTIL, UsageWidget.SEVEN_DAY_RESET)
        writeMetric(usage.sevenDayOpus, UsageWidget.OPUS_UTIL, UsageWidget.OPUS_RESET)
        writeMetric(usage.sevenDaySonnet, UsageWidget.SONNET_UTIL, UsageWidget.SONNET_RESET)
    }
}

suspend fun pushCodexDataToWidgets(context: Context, usage: CodexUsage) {
    updateWidgetStates(context) {
        this[UsageWidget.CODEX_FETCHED_AT] = usage.fetchedAt.toEpochMilli()
        writeMetric(usage.weekly, UsageWidget.CODEX_WEEKLY_UTIL, UsageWidget.CODEX_WEEKLY_RESET)
    }
}

suspend fun pushGrokDataToWidgets(context: Context, usage: GrokUsage) {
    updateWidgetStates(context) {
        this[UsageWidget.GROK_FETCHED_AT] = usage.fetchedAt.toEpochMilli()
        writeMetric(usage.weekly, UsageWidget.GROK_WEEKLY_UTIL, UsageWidget.GROK_WEEKLY_RESET)
    }
}

suspend fun clearCodexWidgetData(context: Context) {
    updateWidgetStates(context) {
        remove(UsageWidget.CODEX_FETCHED_AT)
        remove(UsageWidget.CODEX_WEEKLY_UTIL)
        remove(UsageWidget.CODEX_WEEKLY_RESET)
    }
}

suspend fun clearGrokWidgetData(context: Context) {
    updateWidgetStates(context) {
        remove(UsageWidget.GROK_FETCHED_AT)
        remove(UsageWidget.GROK_WEEKLY_UTIL)
        remove(UsageWidget.GROK_WEEKLY_RESET)
    }
}

private suspend fun updateWidgetStates(
    context: Context,
    update: MutablePreferences.() -> Unit,
) {
    val manager = GlanceAppWidgetManager(context)
    manager.getGlanceIds(UsageWidget::class.java).forEach { glanceId ->
        updateAppWidgetState(context, UsageWidgetStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply(update)
        }
    }
    UsageWidget().updateAll(context)
}

private fun MutablePreferences.writeMetric(
    metric: UsageMetric?,
    utilKey: androidx.datastore.preferences.core.Preferences.Key<Double>,
    resetKey: androidx.datastore.preferences.core.Preferences.Key<String>,
) {
    if (metric == null) {
        remove(utilKey)
        remove(resetKey)
        return
    }
    this[utilKey] = metric.utilization
    metric.resetsAt?.let { this[resetKey] = it.toString() } ?: remove(resetKey)
}
