package com.qbapps.claudeusage.notification

import androidx.annotation.StringRes
import com.qbapps.claudeusage.R

/** Weekly usage limits that raise their own threshold alerts, tracked independently. */
enum class WeeklyLimit(
    val preferenceKey: String,
    @StringRes val labelRes: Int
) {
    CLAUDE_WEEKLY("claude_weekly", R.string.weekly_limit_claude),
    CLAUDE_WEEKLY_OPUS("claude_weekly_opus", R.string.weekly_limit_claude_opus),
    CODEX_WEEKLY("codex_weekly", R.string.weekly_limit_codex),
    GROK_WEEKLY("grok_weekly", R.string.weekly_limit_grok),
}
