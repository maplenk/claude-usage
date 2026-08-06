package com.qbapps.claudeusage.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UsageWidgetPreferencesTest {

    @Test
    fun `maps all providers into three peer rows`() {
        val preferences = mutablePreferencesOf(
            UsageWidget.HAS_DATA to "true",
            UsageWidget.FIVE_HOUR_UTIL to 56.0,
            UsageWidget.SEVEN_DAY_UTIL to 49.0,
            UsageWidget.CODEX_WEEKLY_UTIL to 47.0,
            UsageWidget.GROK_WEEKLY_UTIL to 99.0,
        )

        val data = preferences.toHeadroomWidgetData()
        assertNotNull(data)
        val providers = requireNotNull(data).providers

        assertEquals(listOf("Claude", "Codex", "Grok"), providers.map { it.provider })
        assertEquals(listOf(49, 47, 99), providers.map { it.usedPercent })
        assertEquals(56, providers.first().liveUsedPercent)
        assertEquals(null, providers[1].liveUsedPercent)
        assertEquals(null, providers[2].liveUsedPercent)
    }
}
