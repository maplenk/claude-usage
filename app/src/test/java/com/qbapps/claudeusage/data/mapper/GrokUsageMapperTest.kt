package com.qbapps.claudeusage.data.mapper

import com.google.gson.Gson
import com.qbapps.claudeusage.data.remote.GrokCreditsResponseDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GrokUsageMapperTest {
    private val gson = Gson()

    @Test
    fun `maps captured weekly credits response`() {
        val response = gson.fromJson(
            """
            {
              "config": {
                "creditUsagePercent": 67.5,
                "currentPeriod": {
                  "type": "USAGE_PERIOD_TYPE_WEEKLY",
                  "start": "2026-08-01T04:01:09.238389+00:00",
                  "end": "2026-08-08T04:01:09.238389+00:00"
                },
                "isUnifiedBillingUser": true
              }
            }
            """.trimIndent(),
            GrokCreditsResponseDto::class.java,
        )

        val usage = response.toGrokWeeklyDomain(Instant.parse("2026-08-06T00:00:00Z"))

        assertEquals(67.5, usage.weekly.utilization, 0.0)
        assertEquals(Instant.parse("2026-08-08T04:01:09.238389Z"), usage.weekly.resetsAt)
        assertEquals(Instant.parse("2026-08-06T00:00:00Z"), usage.fetchedAt)
    }

    @Test
    fun `missing proto percent maps to zero`() {
        val response = gson.fromJson(
            """
            {"config":{"currentPeriod":{"type":"USAGE_PERIOD_TYPE_WEEKLY","start":"2026-08-01T00:00:00Z","end":"2026-08-08T00:00:00Z"}}}
            """.trimIndent(),
            GrokCreditsResponseDto::class.java,
        )

        assertEquals(0.0, response.toGrokWeeklyDomain().weekly.utilization, 0.0)
    }

    @Test
    fun `monthly legacy period is not mislabeled weekly`() {
        val response = gson.fromJson(
            """
            {"config":{"creditUsagePercent":20,"currentPeriod":{"type":"USAGE_PERIOD_TYPE_MONTHLY","start":"2026-08-01T00:00:00Z","end":"2026-09-01T00:00:00Z"}}}
            """.trimIndent(),
            GrokCreditsResponseDto::class.java,
        )

        assertThrows(IllegalStateException::class.java) {
            response.toGrokWeeklyDomain()
        }
    }
}
