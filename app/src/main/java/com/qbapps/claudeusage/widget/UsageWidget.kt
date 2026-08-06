package com.qbapps.claudeusage.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.domain.model.CodexUsage
import com.qbapps.claudeusage.domain.model.GrokUsage
import com.qbapps.claudeusage.domain.model.UsageMetric
import java.time.Instant

class UsageWidget : GlanceAppWidget() {
    companion object {
        private val COMPACT = DpSize(160.dp, 172.dp)
        private val HORIZONTAL = DpSize(340.dp, 72.dp)
        private val WIDE = DpSize(340.dp, 172.dp)
        private val TALL = DpSize(340.dp, 250.dp)

        internal val FIVE_HOUR_UTIL = doublePreferencesKey("five_hour_utilization")
        internal val FIVE_HOUR_RESET = stringPreferencesKey("five_hour_resets_at")
        internal val SEVEN_DAY_UTIL = doublePreferencesKey("seven_day_utilization")
        internal val SEVEN_DAY_RESET = stringPreferencesKey("seven_day_resets_at")
        internal val OPUS_UTIL = doublePreferencesKey("seven_day_opus_utilization")
        internal val OPUS_RESET = stringPreferencesKey("seven_day_opus_resets_at")
        internal val SONNET_UTIL = doublePreferencesKey("seven_day_sonnet_utilization")
        internal val SONNET_RESET = stringPreferencesKey("seven_day_sonnet_resets_at")
        internal val FETCHED_AT = longPreferencesKey("fetched_at_epoch_millis")
        internal val HAS_DATA = stringPreferencesKey("has_data")

        internal val CODEX_WEEKLY_UTIL = doublePreferencesKey("codex_weekly_utilization")
        internal val CODEX_WEEKLY_RESET = stringPreferencesKey("codex_weekly_resets_at")
        internal val CODEX_FETCHED_AT = longPreferencesKey("codex_fetched_at_epoch_millis")

        internal val GROK_WEEKLY_UTIL = doublePreferencesKey("grok_weekly_utilization")
        internal val GROK_WEEKLY_RESET = stringPreferencesKey("grok_weekly_resets_at")
        internal val GROK_FETCHED_AT = longPreferencesKey("grok_fetched_at_epoch_millis")
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT, HORIZONTAL, WIDE, TALL)
    )
    override val stateDefinition = UsageWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val data = currentState<Preferences>().toHeadroomWidgetData()
            UsageWidgetContent(data = data, widgetSize = LocalSize.current.toWidgetSize())
        }
    }
}

internal fun Preferences.toHeadroomWidgetData(): HeadroomWidgetData? {
    val claude = if (this[UsageWidget.HAS_DATA] != null) {
        ClaudeUsage(
            fiveHour = readMetric(UsageWidget.FIVE_HOUR_UTIL, UsageWidget.FIVE_HOUR_RESET),
            sevenDay = readMetric(UsageWidget.SEVEN_DAY_UTIL, UsageWidget.SEVEN_DAY_RESET),
            sevenDayOpus = readMetric(UsageWidget.OPUS_UTIL, UsageWidget.OPUS_RESET),
            sevenDaySonnet = readMetric(UsageWidget.SONNET_UTIL, UsageWidget.SONNET_RESET),
            fetchedAt = this[UsageWidget.FETCHED_AT]?.let(Instant::ofEpochMilli) ?: Instant.now(),
        )
    } else null

    val codex = readMetric(UsageWidget.CODEX_WEEKLY_UTIL, UsageWidget.CODEX_WEEKLY_RESET)?.let {
        CodexUsage(
            weekly = it,
            fetchedAt = this[UsageWidget.CODEX_FETCHED_AT]?.let(Instant::ofEpochMilli) ?: Instant.now(),
        )
    }
    val grok = readMetric(UsageWidget.GROK_WEEKLY_UTIL, UsageWidget.GROK_WEEKLY_RESET)?.let {
        GrokUsage(
            weekly = it,
            fetchedAt = this[UsageWidget.GROK_FETCHED_AT]?.let(Instant::ofEpochMilli) ?: Instant.now(),
        )
    }
    if (claude == null && codex == null && grok == null) return null
    return HeadroomWidgetData(claude = claude, codex = codex, grok = grok)
}

private fun Preferences.readMetric(
    utilizationKey: Preferences.Key<Double>,
    resetsAtKey: Preferences.Key<String>,
): UsageMetric? {
    val utilization = this[utilizationKey] ?: return null
    val resetsAt = this[resetsAtKey]?.let { runCatching { Instant.parse(it) }.getOrNull() }
    return UsageMetric(utilization = utilization, resetsAt = resetsAt)
}

private fun DpSize.toWidgetSize(): WidgetSize = when {
    width >= 300.dp && height >= 220.dp -> WidgetSize.TALL
    width >= 300.dp && height >= 140.dp -> WidgetSize.WIDE
    width >= 300.dp -> WidgetSize.HORIZONTAL
    else -> WidgetSize.COMPACT
}
