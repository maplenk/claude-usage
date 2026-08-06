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
    /**
     * The widget treats providers as peers. The long window is always the primary
     * value; a short window is rendered as a subordinate hairline when available.
     */
    val providers: List<WidgetProvider>
        get() = listOf(
            WidgetProvider(
                provider = "Claude",
                iconRes = R.drawable.ic_provider_claude,
                longWindow = claude?.sevenDay,
                liveWindow = claude?.fiveHour,
            ),
            WidgetProvider(
                provider = "Codex",
                iconRes = R.drawable.ic_provider_codex,
                longWindow = codex?.weekly,
            ),
            WidgetProvider(
                provider = "Grok",
                iconRes = R.drawable.ic_provider_grok,
                longWindow = grok?.weekly,
            ),
        )

    val oldestFetchedAt: Instant?
        get() = listOfNotNull(claude?.fetchedAt, codex?.fetchedAt, grok?.fetchedAt).minOrNull()
}

internal data class WidgetProvider(
    val provider: String,
    @DrawableRes val iconRes: Int,
    val longWindow: UsageMetric?,
    val liveWindow: UsageMetric? = null,
) {
    val usedPercent: Int?
        get() = longWindow?.effectiveUtilization()?.coerceIn(0.0, 100.0)?.toInt()

    val liveUsedPercent: Int?
        get() = liveWindow?.effectiveUtilization()?.coerceIn(0.0, 100.0)?.toInt()

    val remainingPercent: Int?
        get() = usedPercent?.let { (100 - it).coerceIn(0, 100) }
}
