package com.qbapps.claudeusage.widget

import androidx.annotation.DrawableRes
import com.qbapps.claudeusage.R
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.domain.model.CodexUsage
import com.qbapps.claudeusage.domain.model.GrokUsage
import com.qbapps.claudeusage.domain.model.UsageMetric
import java.time.Instant

internal data class HeadroomWidgetData(
    val claude: ClaudeUsage?,
    val codex: CodexUsage?,
    val grok: GrokUsage?,
) {
    val metrics: List<WidgetMetric>
        get() = listOf(
            WidgetMetric("Claude", "Session", "5H", R.drawable.ic_provider_claude, claude?.fiveHour),
            WidgetMetric("Claude", "Weekly", "7D", R.drawable.ic_provider_claude, claude?.sevenDay),
            WidgetMetric("Codex", "Weekly", "7D", R.drawable.ic_provider_codex, codex?.weekly),
            WidgetMetric("Grok", "Weekly", "7D", R.drawable.ic_provider_grok, grok?.weekly),
        )

    val oldestFetchedAt: Instant?
        get() = listOfNotNull(claude?.fetchedAt, codex?.fetchedAt, grok?.fetchedAt).minOrNull()
}

internal data class WidgetMetric(
    val provider: String,
    val label: String,
    val window: String,
    @DrawableRes val iconRes: Int,
    val metric: UsageMetric?,
) {
    val usedPercent: Int?
        get() = metric?.effectiveUtilization()?.coerceIn(0.0, 100.0)?.toInt()

    val remainingPercent: Int?
        get() = usedPercent?.let { (100 - it).coerceIn(0, 100) }
}
