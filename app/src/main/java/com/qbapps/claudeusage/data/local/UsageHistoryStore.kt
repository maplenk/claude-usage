package com.qbapps.claudeusage.data.local

import android.content.Context
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.domain.model.UsageHistoryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists usage history snapshots locally for trend visualizations.
 * Data is retained for [RETENTION_DAYS] and bounded to [MAX_POINTS] records.
 */
@Singleton
class UsageHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val historyFile: File by lazy { File(context.filesDir, HISTORY_FILE_NAME) }

    private val _history = MutableStateFlow<List<UsageHistoryPoint>>(emptyList())
    val history: StateFlow<List<UsageHistoryPoint>> = _history.asStateFlow()

    init {
        ioScope.launch {
            loadHistory()
        }
    }

    suspend fun append(usage: ClaudeUsage) = withContext(Dispatchers.IO) {
        val point = UsageHistoryPoint(
            timestamp = usage.fetchedAt,
            fiveHourUtilization = usage.fiveHour?.utilization,
            fiveHourResetsAt = usage.fiveHour?.resetsAt,
            sevenDayUtilization = usage.sevenDay?.utilization,
            sevenDayOpusUtilization = usage.sevenDayOpus?.utilization,
            sevenDaySonnetUtilization = usage.sevenDaySonnet?.utilization,
        )

        mutex.withLock {
            val updated = pruneAndTrim(_history.value + point)
            _history.value = updated
            writeHistory(updated)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _history.value = emptyList()
            if (historyFile.exists()) {
                historyFile.delete()
            }
        }
    }

    private suspend fun loadHistory() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!historyFile.exists()) {
                _history.value = emptyList()
                return@withLock
            }

            val parsed = historyFile.readLines()
                .mapNotNull(::parseLine)
                .sortedBy { it.timestamp }

            val pruned = pruneAndTrim(parsed)
            _history.value = pruned

            if (pruned.size != parsed.size) {
                writeHistory(pruned)
            }
        }
    }

    private fun writeHistory(points: List<UsageHistoryPoint>) {
        val content = buildString {
            points.forEach { point ->
                append(point.timestamp.toEpochMilli())
                append(',')
                append(point.fiveHourUtilization.orEmptyToken())
                append(',')
                append(point.fiveHourResetsAt.orEmptyEpochToken())
                append(',')
                append(point.sevenDayUtilization.orEmptyToken())
                append(',')
                append(point.sevenDayOpusUtilization.orEmptyToken())
                append(',')
                append(point.sevenDaySonnetUtilization.orEmptyToken())
                append('\n')
            }
        }
        historyFile.writeText(content)
    }

    private fun parseLine(line: String): UsageHistoryPoint? {
        val tokens = line.split(',', limit = 6)
        if (tokens.size !in 5..6) return null

        val timestampMs = tokens[0].toLongOrNull() ?: return null
        return if (tokens.size == 6) {
            UsageHistoryPoint(
                timestamp = Instant.ofEpochMilli(timestampMs),
                fiveHourUtilization = tokens[1].toDoubleOrNull(),
                fiveHourResetsAt = tokens[2].toLongOrNull()?.let { Instant.ofEpochMilli(it) },
                sevenDayUtilization = tokens[3].toDoubleOrNull(),
                sevenDayOpusUtilization = tokens[4].toDoubleOrNull(),
                sevenDaySonnetUtilization = tokens[5].toDoubleOrNull(),
            )
        } else {
            // Backward compatibility with older cache format (without resets_at).
            UsageHistoryPoint(
                timestamp = Instant.ofEpochMilli(timestampMs),
                fiveHourUtilization = tokens[1].toDoubleOrNull(),
                fiveHourResetsAt = null,
                sevenDayUtilization = tokens[2].toDoubleOrNull(),
                sevenDayOpusUtilization = tokens[3].toDoubleOrNull(),
                sevenDaySonnetUtilization = tokens[4].toDoubleOrNull(),
            )
        }
    }

    private fun pruneAndTrim(points: List<UsageHistoryPoint>): List<UsageHistoryPoint> {
        val cutoff = Instant.now().minus(Duration.ofDays(RETENTION_DAYS))
        val retained = points.filter { it.timestamp >= cutoff }
        return if (retained.size > MAX_POINTS) {
            retained.takeLast(MAX_POINTS)
        } else {
            retained
        }
    }

    private fun Double?.orEmptyToken(): String = this?.toString() ?: ""
    private fun Instant?.orEmptyEpochToken(): String = this?.toEpochMilli()?.toString() ?: ""

    companion object {
        private const val HISTORY_FILE_NAME = "usage_history.csv"
        private const val RETENTION_DAYS = 30L
        private const val MAX_POINTS = 10_000
    }
}
