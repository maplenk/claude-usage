package com.qbapps.claudeusage.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FourLimitWidgetDataTest {

    @Test
    fun `maps session and weekly limits into four fixed rows`() {
        val preferences = mutablePreferencesOf(
            UsageWidget.HAS_DATA to "true",
            UsageWidget.FIVE_HOUR_UTIL to 56.0,
            UsageWidget.SEVEN_DAY_UTIL to 49.0,
            UsageWidget.CODEX_WEEKLY_UTIL to 47.0,
            UsageWidget.GROK_WEEKLY_UTIL to 99.0,
        )

        val data = preferences.toHeadroomWidgetData()
        assertNotNull(data)
        val rows = requireNotNull(data).fourLimitRows

        assertEquals(
            listOf("Session", "Claude wk", "Codex wk", "Grok wk"),
            rows.map { it.label },
        )
        assertEquals(listOf(56, 49, 47, 99), rows.map { it.usedPercent })
        assertEquals(listOf("Claude", "Claude", "Codex", "Grok"), rows.map { it.provider })
    }
}
