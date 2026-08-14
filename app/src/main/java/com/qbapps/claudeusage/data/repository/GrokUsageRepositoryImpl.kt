package com.qbapps.claudeusage.data.repository

import android.content.Context

import com.google.gson.JsonParser
import com.qbapps.claudeusage.BuildConfig
import com.qbapps.claudeusage.data.local.GrokAuthTokens
import com.qbapps.claudeusage.data.local.GrokUsageDataStore
import com.qbapps.claudeusage.data.local.SecureCredentialStore
import com.qbapps.claudeusage.data.mapper.toGrokWeeklyDomain
import com.qbapps.claudeusage.data.remote.GrokApiService
import com.qbapps.claudeusage.data.remote.GrokTokenResponseDto
import com.qbapps.claudeusage.domain.model.GrokDeviceCode
import com.qbapps.claudeusage.domain.model.GrokUsage
import com.qbapps.claudeusage.domain.repository.GrokUsageRepository
import com.qbapps.claudeusage.notification.WeeklyLimit
import com.qbapps.claudeusage.notification.WeeklyThresholdNotifier
import com.qbapps.claudeusage.widget.clearGrokWidgetData
import com.qbapps.claudeusage.widget.pushGrokDataToWidgets
import com.qbapps.claudeusage.worker.SyncLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class GrokUsageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: GrokApiService,
    private val credentialStore: SecureCredentialStore,
    private val dataStore: GrokUsageDataStore,
    private val weeklyThresholdNotifier: WeeklyThresholdNotifier,
) : GrokUsageRepository {
    override val cachedUsage: Flow<GrokUsage?> = dataStore.cachedUsage

    private val tokenMutex = Mutex()
    @Volatile private var lastFetchTimeMs = 0L

    override fun isAuthenticated(): Boolean = credentialStore.getGrokTokens() != null

    override suspend fun startDeviceLogin(): Result<GrokDeviceCode> = runCatching {
        val response = api.requestDeviceCode(clientVersion = BuildConfig.VERSION_NAME)
        if (!response.isSuccessful) {
            error("Grok sign-in could not start (${response.code()}).")
        }
        val body = response.body() ?: error("Grok sign-in returned an empty response.")
        val verificationUrl = body.verificationUriComplete ?: body.verificationUri
        if (!verificationUrl.startsWith("https://")) {
            error("Grok returned an unsafe verification URL.")
        }
        GrokDeviceCode(
            verificationUrl = body.verificationUri,
            verificationUrlComplete = body.verificationUriComplete,
            userCode = body.userCode,
            deviceCode = body.deviceCode,
            intervalSeconds = (body.interval ?: DEFAULT_POLL_INTERVAL_SECONDS).coerceAtLeast(1L),
            expiresAtMs = System.currentTimeMillis() + body.expiresIn.coerceAtLeast(1L) * 1_000L,
        )
    }

    override suspend fun completeDeviceLogin(deviceCode: GrokDeviceCode): Result<GrokUsage> = runCatching {
        var pollIntervalSeconds = deviceCode.intervalSeconds
        while (System.currentTimeMillis() < deviceCode.expiresAtMs) {
            delay(pollIntervalSeconds * 1_000L)
            val response = api.pollDeviceCode(
                deviceCode = deviceCode.deviceCode,
                clientVersion = BuildConfig.VERSION_NAME,
            )
            if (response.isSuccessful) {
                val tokens = response.body().requireCompleteTokens()
                credentialStore.saveGrokTokens(tokens)
                return@runCatching fetchWeekly(urgent = true).getOrThrow()
            }

            when (response.oauthError()) {
                "authorization_pending" -> Unit
                "slow_down" -> pollIntervalSeconds += SLOW_DOWN_INCREMENT_SECONDS
                "access_denied" -> error("Grok authorization was denied.")
                "expired_token" -> error("Grok sign-in code expired. Start again.")
                else -> error("Grok authorization failed (${response.code()}).")
            }
        }
        error("Grok sign-in code expired. Start again.")
    }

    override suspend fun fetchWeekly(urgent: Boolean): Result<GrokUsage> = runCatching {
        val nowMs = System.currentTimeMillis()
        val usage = if (!urgent && nowMs - lastFetchTimeMs < MIN_FETCH_INTERVAL_MS) {
            dataStore.cachedUsage.first() ?: error("Grok usage has not been fetched yet.")
        } else {
            var tokens = validTokens()
            var response = api.getCredits(authorization = "Bearer ${tokens.accessToken}")
            if (response.code() == 401 || response.code() == 403) {
                tokens = refreshTokens(force = true)
                response = api.getCredits(authorization = "Bearer ${tokens.accessToken}")
            }
            if (!response.isSuccessful) {
                error("Grok usage request failed (${response.code()}).")
            }
            val usage = response.body()?.toGrokWeeklyDomain()
                ?: error("Grok billing response was empty.")
            dataStore.save(usage)
            lastFetchTimeMs = nowMs
            runCatching { weeklyThresholdNotifier.evaluate(WeeklyLimit.GROK_WEEKLY, usage.weekly) }
                .onFailure { error ->
                    SyncLog.d(
                        context,
                        "weekly limit notification skipped: ${error.message ?: "unknown"}"
                    )
                }
            usage
        }
        runCatching { pushGrokDataToWidgets(context, usage) }
        usage
    }

    override suspend fun disconnect() {
        credentialStore.clearGrokTokens()
        dataStore.clear()
        lastFetchTimeMs = 0L
        runCatching { clearGrokWidgetData(context) }
    }

    override suspend fun clearCachedData() {
        dataStore.clear()
        lastFetchTimeMs = 0L
    }

    private suspend fun validTokens(): GrokAuthTokens {
        val current = credentialStore.getGrokTokens() ?: error("Connect Grok in Settings.")
        val expiresSoon = current.expiresAtMs <= System.currentTimeMillis() + REFRESH_WINDOW_MS
        return if (expiresSoon || JwtClaims.isExpiringSoon(current.accessToken)) {
            refreshTokens(force = false)
        } else {
            current
        }
    }

    private suspend fun refreshTokens(force: Boolean): GrokAuthTokens = tokenMutex.withLock {
        val current = credentialStore.getGrokTokens() ?: error("Connect Grok in Settings.")
        val expiresSoon = current.expiresAtMs <= System.currentTimeMillis() + REFRESH_WINDOW_MS
        if (!force && !expiresSoon && !JwtClaims.isExpiringSoon(current.accessToken)) {
            return@withLock current
        }

        val response = api.refreshTokens(refreshToken = current.refreshToken)
        if (!response.isSuccessful) {
            error("Grok sign-in expired. Disconnect and connect Grok again.")
        }
        val refreshed = response.body() ?: error("Grok token refresh returned no data.")
        val accessToken = refreshed.accessToken ?: error("Grok token refresh omitted access token.")
        val updated = GrokAuthTokens(
            accessToken = accessToken,
            refreshToken = refreshed.refreshToken ?: current.refreshToken,
            idToken = refreshed.idToken ?: current.idToken,
            expiresAtMs = System.currentTimeMillis() +
                (refreshed.expiresIn ?: DEFAULT_TOKEN_LIFETIME_SECONDS) * 1_000L,
        )
        credentialStore.saveGrokTokens(updated)
        updated
    }

    private fun GrokTokenResponseDto?.requireCompleteTokens(): GrokAuthTokens {
        val body = this ?: error("Grok token exchange returned no data.")
        val access = body.accessToken ?: error("Grok access token was missing.")
        val refresh = body.refreshToken ?: error("Grok refresh token was missing.")
        return GrokAuthTokens(
            accessToken = access,
            refreshToken = refresh,
            idToken = body.idToken,
            expiresAtMs = System.currentTimeMillis() +
                (body.expiresIn ?: DEFAULT_TOKEN_LIFETIME_SECONDS) * 1_000L,
        )
    }

    private fun retrofit2.Response<*>.oauthError(): String? = runCatching {
        errorBody()?.string()?.let(JsonParser::parseString)
            ?.asJsonObject
            ?.get("error")
            ?.asString
    }.getOrNull()

    private companion object {
        const val DEFAULT_POLL_INTERVAL_SECONDS = 5L
        const val SLOW_DOWN_INCREMENT_SECONDS = 5L
        const val DEFAULT_TOKEN_LIFETIME_SECONDS = 60L * 60L
        const val REFRESH_WINDOW_MS = 5L * 60L * 1_000L
        const val MIN_FETCH_INTERVAL_MS = 5L * 60L * 1_000L
    }
}
