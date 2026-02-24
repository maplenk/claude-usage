package com.qbapps.claudeusage.data.mapper

import com.qbapps.claudeusage.data.remote.OrganizationDto
import com.qbapps.claudeusage.data.remote.UsageMetricDto
import com.qbapps.claudeusage.data.remote.UsageResponseDto
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.domain.model.Organization
import com.qbapps.claudeusage.domain.model.UsageMetric
import java.time.Instant

/**
 * Maps [UsageResponseDto] from the API into the domain [ClaudeUsage] model.
 */
fun UsageResponseDto.toDomain(): ClaudeUsage = ClaudeUsage(
    fiveHour = fiveHour?.toDomain(),
    sevenDay = sevenDay?.toDomain(),
    sevenDayOpus = sevenDayOpus?.toDomain(),
    sevenDaySonnet = sevenDaySonnet?.toDomain(),
    fetchedAt = Instant.now()
)

/**
 * Maps [UsageMetricDto] into the domain [UsageMetric], parsing the
 * ISO-8601 `resets_at` string into a [java.time.Instant].
 */
fun UsageMetricDto.toDomain(): UsageMetric = UsageMetric(
    utilization = utilization,
    resetsAt = resetsAt?.let { parseInstant(it) }
)

/**
 * Maps [OrganizationDto] into the domain [Organization].
 */
fun OrganizationDto.toDomain(): Organization = Organization(
    uuid = uuid,
    name = name
)

/**
 * Maps a list of [OrganizationDto] into a list of domain [Organization].
 */
fun List<OrganizationDto>.toDomain(): List<Organization> = map { it.toDomain() }

/**
 * Safely parses an ISO-8601 timestamp string to [Instant].
 * Returns null if parsing fails rather than crashing.
 */
private fun parseInstant(iso8601: String): Instant? = runCatching {
    Instant.parse(iso8601)
}.getOrNull()
