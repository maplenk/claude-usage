package com.qbapps.claudeusage.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import com.qbapps.claudeusage.domain.model.UsageMetric
import java.time.Instant

/**
 * Glance widget that displays Claude API usage metrics.
 *
 * Uses [SizeMode.Responsive] with three breakpoints so Android can pick
 * the best layout for the current widget placement.
 *
 * State is managed via [UsageWidgetStateDefinition], which uses Glance's own
 * DataStore (separate from the app's DataStore) to avoid the "multiple
 * DataStores active for the same file" crash. Data is pushed into the widget
 * state when [UsageSyncWorker] calls [pushDataToWidgets].
 */
class UsageWidget : GlanceAppWidget() {

    companion object {
        // Responsive size breakpoints
        private val SMALL = DpSize(57.dp, 57.dp)    // 1x1
        private val MEDIUM = DpSize(120.dp, 57.dp)  // 2x1
        private val LARGE = DpSize(120.dp, 120.dp)  // 2x2+

        // These keys must mirror those in UsageDataStore exactly.
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
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL, MEDIUM, LARGE)
    )

    override val stateDefinition = UsageWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val usage = prefs.toClaudeUsage()
            val size = LocalSize.current
            val widgetSize = size.toWidgetSize()
            UsageWidgetContent(usage = usage, widgetSize = widgetSize)
        }
    }
}

// ---------------------------------------------------------------------------
// Preferences -> domain model helpers
// ---------------------------------------------------------------------------

/**
 * Reconstructs a [ClaudeUsage] from the raw DataStore [Preferences].
 * Returns `null` when no data has been cached yet.
 */
internal fun Preferences.toClaudeUsage(): ClaudeUsage? {
    if (this[UsageWidget.HAS_DATA] == null) return null

    return ClaudeUsage(
        fiveHour = readMetric(UsageWidget.FIVE_HOUR_UTIL, UsageWidget.FIVE_HOUR_RESET),
        sevenDay = readMetric(UsageWidget.SEVEN_DAY_UTIL, UsageWidget.SEVEN_DAY_RESET),
        sevenDayOpus = readMetric(UsageWidget.OPUS_UTIL, UsageWidget.OPUS_RESET),
        sevenDaySonnet = readMetric(UsageWidget.SONNET_UTIL, UsageWidget.SONNET_RESET),
        fetchedAt = this[UsageWidget.FETCHED_AT]?.let { Instant.ofEpochMilli(it) }
            ?: Instant.now()
    )
}

private fun Preferences.readMetric(
    utilizationKey: Preferences.Key<Double>,
    resetsAtKey: Preferences.Key<String>
): UsageMetric? {
    val utilization = this[utilizationKey] ?: return null
    val resetsAt = this[resetsAtKey]?.let { runCatching { Instant.parse(it) }.getOrNull() }
    return UsageMetric(utilization = utilization, resetsAt = resetsAt)
}

// ---------------------------------------------------------------------------
// DpSize -> WidgetSize mapping
// ---------------------------------------------------------------------------

private fun DpSize.toWidgetSize(): WidgetSize = when {
    width >= 120.dp && height >= 120.dp -> WidgetSize.LARGE  // 2x2+
    width >= 120.dp -> WidgetSize.MEDIUM                     // 2x1
    else -> WidgetSize.SMALL                                 // 1x1
}
