package com.qbapps.claudeusage.notification

import android.content.Context
import com.qbapps.claudeusage.data.local.UserPreferencesStore
import com.qbapps.claudeusage.data.local.WeeklyThresholdState
import com.qbapps.claudeusage.domain.model.UsageMetric
import com.qbapps.claudeusage.worker.SyncLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Raises threshold alerts for a single weekly limit, deduped per weekly window.
 * Shared by the Claude, Codex, and Grok repositories.
 */
@Singleton
class WeeklyThresholdNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesStore: UserPreferencesStore,
    private val notificationHelper: UsageNotificationHelper,
) {

    suspend fun evaluate(
        limit: WeeklyLimit,
        metric: UsageMetric?,
        now: Instant = Instant.now(),
    ) {
        if (metric == null) return

        val utilization = metric.effectiveUtilization(now)
        val stored = userPreferencesStore.getWeeklyThresholdState(limit.preferenceKey)
        val decision = WeeklyThresholdEvaluator.evaluate(
            utilization = utilization,
            windowResetsAtMs = metric.resetsAt?.toEpochMilli(),
            lastNotifiedThreshold = stored.lastNotifiedThreshold,
            lastWindowResetsAtMs = stored.windowResetsAtMs,
        )

        val thresholdToNotify = decision.thresholdToNotify
        if (thresholdToNotify != null && userPreferencesStore.notifyOnWeeklyLimits.first()) {
            val currentPercent = utilization.coerceIn(0.0, 100.0).roundToInt()
            notificationHelper.notifyWeeklyLimit(
                limit = limit,
                currentPercent = currentPercent,
                crossedThreshold = thresholdToNotify,
                resetsAt = metric.resetsAt,
                now = now,
            )
            SyncLog.d(
                context,
                "weekly limit alert ${limit.preferenceKey} threshold=${thresholdToNotify}% " +
                    "current=${currentPercent}%"
            )
        }

        // Advance the baseline even while notifications are disabled so a
        // re-enabled toggle does not replay alerts for this window.
        val updated = WeeklyThresholdState(
            lastNotifiedThreshold = decision.lastNotifiedThreshold,
            windowResetsAtMs = decision.windowResetsAtMs,
        )
        if (updated != stored) {
            userPreferencesStore.saveWeeklyThresholdState(limit.preferenceKey, updated)
        }
    }
}
