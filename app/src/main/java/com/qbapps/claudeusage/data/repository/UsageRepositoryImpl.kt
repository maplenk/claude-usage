package com.qbapps.claudeusage.data.repository

import android.content.Context
import com.qbapps.claudeusage.data.local.SecureCredentialStore
import com.qbapps.claudeusage.data.local.UsageDataStore
import com.qbapps.claudeusage.data.local.UsageHistoryStore
import com.qbapps.claudeusage.data.local.UserPreferencesStore
import com.qbapps.claudeusage.data.mapper.toDomain
import com.qbapps.claudeusage.data.remote.ClaudeApiService
import com.qbapps.claudeusage.domain.guardrail.PaceTrack
import com.qbapps.claudeusage.domain.guardrail.SessionGuardrailEvaluator
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.domain.model.Organization
import com.qbapps.claudeusage.domain.model.UsageError
import com.qbapps.claudeusage.domain.model.UsageHistoryPoint
import com.qbapps.claudeusage.domain.repository.UsageRepository
import com.qbapps.claudeusage.notification.UsageNotificationHelper
import com.qbapps.claudeusage.notification.UsageThresholdEvaluator
import com.qbapps.claudeusage.widget.pushDataToWidgets
import com.qbapps.claudeusage.worker.SyncLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import retrofit2.Response
import java.io.IOException
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ClaudeApiService,
    private val credentialStore: SecureCredentialStore,
    private val usageDataStore: UsageDataStore,
    private val usageHistoryStore: UsageHistoryStore,
    private val userPreferencesStore: UserPreferencesStore,
    private val notificationHelper: UsageNotificationHelper,
) : UsageRepository {

    /** Minimum interval between fetches to avoid hammering the API. */
    private val minFetchIntervalMs = 5_000L
    @Volatile private var lastFetchTimeMs = 0L
    @Volatile private var guardrailSessionEpochMs: Long? = null
    @Volatile private var sentCapRiskForSession = false
    @Volatile private var sentResetSoonForSession = false
    @Volatile private var sentBelowPaceForSession = false

    override val cachedUsage: Flow<ClaudeUsage?> = usageDataStore.cachedUsage
    override val usageHistory: Flow<List<UsageHistoryPoint>> = usageHistoryStore.history

    override suspend fun fetchUsage(): Result<ClaudeUsage> {
        val now = System.currentTimeMillis()
        if (now - lastFetchTimeMs < minFetchIntervalMs) {
            return Result.failure(usageError(UsageError.RateLimited))
        }
        lastFetchTimeMs = now

        val previousSessionUtilization = usageDataStore.cachedUsage.first()?.fiveHour?.utilization

        val sessionKey = credentialStore.getSessionKey()
            ?: return Result.failure(usageError(UsageError.NoCredentials))

        val orgId = credentialStore.getOrgId()
            ?: return Result.failure(usageError(UsageError.NoCredentials))

        val cookie = ClaudeApiService.formatSessionCookie(sessionKey)

        return executeApiCall {
            apiService.getUsage(orgId, cookie)
        }.mapCatching { dto ->
            val usage = dto.toDomain()
            try {
                maybeNotifyUsageMilestone(previousSessionUtilization, usage.fiveHour?.utilization)
            } catch (error: Exception) {
                SyncLog.d(
                    context,
                    "usage milestone notification skipped: ${error.message ?: "unknown"}"
                )
            }
            usageDataStore.save(usage)
            runCatching { usageHistoryStore.append(usage) }
                .onFailure { error ->
                    SyncLog.d(
                        context,
                        "history append skipped: ${error.message ?: "unknown"}"
                    )
                }
            runCatching { maybeNotifyGuardrailSignals(usage) }
                .onFailure { error ->
                    SyncLog.d(
                        context,
                        "guardrail notifications skipped: ${error.message ?: "unknown"}"
                    )
                }
            pushDataToWidgets(context, usage)
            usage
        }
    }

    override suspend fun fetchOrganizations(
        sessionKey: String
    ): Result<List<Organization>> {
        val cookie = ClaudeApiService.formatSessionCookie(sessionKey)

        return executeApiCall {
            apiService.getOrganizations(cookie)
        }.map { dtos ->
            dtos.toDomain()
        }
    }

    override suspend fun validateSessionKey(
        sessionKey: String
    ): Result<List<Organization>> = fetchOrganizations(sessionKey)

    override suspend fun clearUsageHistory() {
        usageHistoryStore.clear()
    }

    override suspend fun clearCachedData() {
        usageDataStore.clear()
        usageHistoryStore.clear()
    }

    // ---- Private helpers ----

    /**
     * Executes a Retrofit call, mapping HTTP and network errors into the
     * appropriate [UsageError] wrapped in a [Result.failure].
     */
    private suspend fun <T> executeApiCall(
        call: suspend () -> Response<T>
    ): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(
                        usageError(UsageError.ServerError(response.code(), "Empty response body"))
                    )
                }
            } else {
                Result.failure(usageError(mapHttpError(response.code(), response.message())))
            }
        } catch (e: IOException) {
            Result.failure(usageError(UsageError.NetworkError))
        } catch (e: Exception) {
            Result.failure(usageError(UsageError.Unknown(e)))
        }
    }

    private fun mapHttpError(code: Int, message: String?): UsageError = when (code) {
        401, 403 -> UsageError.Unauthorized
        429 -> UsageError.RateLimited
        in 500..599 -> UsageError.ServerError(code, message)
        else -> UsageError.ServerError(code, message)
    }

    private suspend fun maybeNotifyUsageMilestone(
        previousUtilization: Double?,
        currentUtilization: Double?
    ) {
        val currentHighestReachedThreshold = UsageThresholdEvaluator.highestReachedThreshold(
            currentUtilization = currentUtilization
        )

        // Usage dropped below first threshold (likely a reset/new cycle),
        // so clear milestone state for the next climb.
        if (currentHighestReachedThreshold == null) {
            userPreferencesStore.saveLastNotifiedSessionThreshold(null)
            return
        }

        val lastNotifiedThreshold = userPreferencesStore.lastNotifiedSessionThreshold.first()
        val crossedThreshold = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization = previousUtilization,
            currentUtilization = currentUtilization
        )

        // Fallback for users upgrading while already above a threshold:
        // if nothing crossed but we never notified this cycle, send current highest once.
        val thresholdToNotify = crossedThreshold ?: if (lastNotifiedThreshold == null) {
            currentHighestReachedThreshold
        } else {
            null
        }

        val shouldNotify = userPreferencesStore.notifyOnUsageThresholds.first()
        if (thresholdToNotify != null &&
            (lastNotifiedThreshold == null || thresholdToNotify > lastNotifiedThreshold) &&
            shouldNotify
        ) {
            val currentPercent = currentUtilization?.coerceIn(0.0, 100.0)?.roundToInt() ?: return
            notificationHelper.notifyUsageMilestone(
                currentPercent = currentPercent,
                crossedThreshold = thresholdToNotify
            )
            SyncLog.d(
                context,
                "usage milestone detected threshold=${thresholdToNotify}% current=${currentPercent}%"
            )
        }

        // Advance baseline to prevent repeat alerts at the same level,
        // including while notifications are disabled.
        if (lastNotifiedThreshold == null || currentHighestReachedThreshold > lastNotifiedThreshold) {
            userPreferencesStore.saveLastNotifiedSessionThreshold(currentHighestReachedThreshold)
        }
    }

    private suspend fun maybeNotifyGuardrailSignals(usage: ClaudeUsage) {
        val shouldNotify = userPreferencesStore.notifyOnUsageThresholds.first()
        if (!shouldNotify) return

        val insights = SessionGuardrailEvaluator.evaluate(
            currentMetric = usage.fiveHour,
            history = usageHistoryStore.history.value,
        )

        val sessionEpoch = usage.fiveHour?.resetsAt?.toEpochMilli()
        if (sessionEpoch != guardrailSessionEpochMs) {
            guardrailSessionEpochMs = sessionEpoch
            sentCapRiskForSession = false
            sentResetSoonForSession = false
            sentBelowPaceForSession = false
        }

        if (insights.willHitCapBeforeReset && !sentCapRiskForSession) {
            val capIn = insights.predictedTimeToCapMinutes?.let(::formatMinutes) ?: "soon"
            val resetIn = insights.timeToResetMinutes?.let(::formatMinutes) ?: "unknown"
            notificationHelper.notifyCapRisk(
                predictedTimeToCapLabel = capIn,
                resetInLabel = resetIn,
            )
            sentCapRiskForSession = true
            SyncLog.d(context, "guardrail alert: cap-risk capIn=$capIn resetIn=$resetIn")
        }

        if ((insights.timeToResetMinutes ?: Long.MAX_VALUE) <= 15L && !sentResetSoonForSession) {
            val resetIn = insights.timeToResetMinutes?.let(::formatMinutes) ?: "soon"
            notificationHelper.notifyResetSoon(resetInLabel = resetIn)
            sentResetSoonForSession = true
            SyncLog.d(context, "guardrail alert: reset-soon resetIn=$resetIn")
        }

        if (insights.paceTrack == PaceTrack.BELOW_USUAL && !sentBelowPaceForSession) {
            notificationHelper.notifyBelowUsualPace()
            sentBelowPaceForSession = true
            SyncLog.d(context, "guardrail alert: below-pace")
        }
    }

    private fun formatMinutes(totalMinutes: Long): String {
        val minutes = totalMinutes.coerceAtLeast(0L)
        val hours = minutes / 60L
        val remaining = minutes % 60L
        return if (hours > 0L) "${hours}h ${remaining}m" else "${remaining}m"
    }
}

/**
 * Wraps a [UsageError] into a [UsageApiException] so it can be carried
 * inside [Result.failure] while preserving the typed error.
 */
class UsageApiException(val error: UsageError) : Exception(error.toString())

private fun usageError(error: UsageError): UsageApiException = UsageApiException(error)
