package com.qbapps.claudeusage.data.mapper

import com.qbapps.claudeusage.data.remote.GrokApiContract
import com.qbapps.claudeusage.data.remote.GrokCreditsResponseDto
import com.qbapps.claudeusage.domain.model.GrokUsage
import com.qbapps.claudeusage.domain.model.UsageMetric
import java.time.Instant

internal fun GrokCreditsResponseDto.toGrokWeeklyDomain(
    fetchedAt: Instant = Instant.now(),
): GrokUsage {
    val credits = config ?: error("Grok billing response changed.")
    val period = credits.currentPeriod ?: error("Grok billing period was missing.")
    if (period.type != GrokApiContract.WEEKLY_PERIOD_TYPE) {
        error("This Grok account does not have a weekly unified-billing limit yet.")
    }
    val resetAt = period.end?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: error("Grok weekly reset time was invalid.")
    return GrokUsage(
        weekly = UsageMetric(
            utilization = (credits.creditUsagePercent ?: 0.0).coerceIn(0.0, 100.0),
            resetsAt = resetAt,
        ),
        fetchedAt = fetchedAt,
    )
}
