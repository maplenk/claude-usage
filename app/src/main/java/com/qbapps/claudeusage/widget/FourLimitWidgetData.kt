package com.qbapps.claudeusage.widget

import androidx.annotation.DrawableRes
import com.qbapps.claudeusage.R
import com.qbapps.claudeusage.domain.model.UsageMetric

internal val HeadroomWidgetData.fourLimitRows: List<FourLimitWidgetMetric>
    get() = listOf(
        FourLimitWidgetMetric(
            provider = "Claude",
            label = "Session",
            iconRes = R.drawable.ic_provider_claude,
            metric = claude?.fiveHour,
            isSession = true,
        ),
        FourLimitWidgetMetric(
            provider = "Claude",
            label = "Claude wk",
            iconRes = R.drawable.ic_provider_claude,
            metric = claude?.sevenDay,
            mutedMark = true,
        ),
        FourLimitWidgetMetric(
            provider = "Codex",
            label = "Codex wk",
            iconRes = R.drawable.ic_provider_codex,
            metric = codex?.weekly,
        ),
        FourLimitWidgetMetric(
            provider = "Grok",
            label = "Grok wk",
            iconRes = R.drawable.ic_provider_grok,
            metric = grok?.weekly,
        ),
    )

internal data class FourLimitWidgetMetric(
    val provider: String,
    val label: String,
    @DrawableRes val iconRes: Int,
    val metric: UsageMetric?,
    val isSession: Boolean = false,
    val mutedMark: Boolean = false,
) {
    val usedPercent: Int?
        get() = metric?.effectiveUtilization()?.coerceIn(0.0, 100.0)?.toInt()
}
