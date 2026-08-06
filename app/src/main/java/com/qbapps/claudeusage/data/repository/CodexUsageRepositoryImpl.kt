package com.qbapps.claudeusage.data.repository

import android.content.Context

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qbapps.claudeusage.data.local.CodexAuthTokens
import com.qbapps.claudeusage.data.local.CodexUsageDataStore
import com.qbapps.claudeusage.data.local.SecureCredentialStore
import com.qbapps.claudeusage.data.mapper.toWeeklyDomain
import com.qbapps.claudeusage.data.remote.CodexApiContract
import com.qbapps.claudeusage.data.remote.CodexAuthApiService
import com.qbapps.claudeusage.data.remote.CodexDeviceCodeRequestDto
import com.qbapps.claudeusage.data.remote.CodexDeviceTokenPollRequestDto
import com.qbapps.claudeusage.data.remote.CodexDeviceTokenPollResponseDto
import com.qbapps.claudeusage.data.remote.CodexRefreshTokenRequestDto
import com.qbapps.claudeusage.data.remote.CodexTokenResponseDto
import com.qbapps.claudeusage.data.remote.CodexUsageApiService
import com.qbapps.claudeusage.domain.model.CodexDeviceCode
import com.qbapps.claudeusage.domain.model.CodexUsage
import com.qbapps.claudeusage.domain.repository.CodexUsageRepository
import com.qbapps.claudeusage.widget.clearCodexWidgetData
import com.qbapps.claudeusage.widget.pushCodexDataToWidgets
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class CodexUsageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authApi: CodexAuthApiService,
    private val usageApi: CodexUsageApiService,
    private val credentialStore: SecureCredentialStore,
    private val dataStore: CodexUsageDataStore,
) : CodexUsageRepository {
    override val cachedUsage: Flow<CodexUsage?> = dataStore.cachedUsage

    private val tokenMutex = Mutex()
    @Volatile private var lastFetchTimeMs = 0L

    override fun isAuthenticated(): Boolean = credentialStore.getCodexTokens() != null

    override suspend fun startDeviceLogin(): Result<CodexDeviceCode> = runCatching {
        val response = authApi.requestDeviceCode(
            CodexDeviceCodeRequestDto(CodexApiContract.CLIENT_ID)
        )
        if (!response.isSuccessful) {
            error("Codex sign-in could not start (${response.code()}).")
        }
        val body = response.body() ?: error("Codex sign-in returned an empty response.")
        val interval = body.interval?.let { value ->
            runCatching { value.asLong }.getOrNull()
                ?: runCatching { value.asString.trim().toLong() }.getOrNull()
        } ?: DEFAULT_POLL_INTERVAL_SECONDS

        CodexDeviceCode(
            verificationUrl = CodexApiContract.DEVICE_VERIFICATION_URL,
            userCode = body.userCode,
            deviceAuthId = body.deviceAuthId,
            intervalSeconds = interval.coerceAtLeast(MIN_POLL_INTERVAL_SECONDS),
        )
    }

    override suspend fun completeDeviceLogin(
        deviceCode: CodexDeviceCode,
    ): Result<CodexUsage> = runCatching {
        val startedAtMs = System.currentTimeMillis()
        var authorization: CodexDeviceTokenPollResponseDto? = null
        while (authorization == null) {
            if (System.currentTimeMillis() - startedAtMs >= DEVICE_LOGIN_TIMEOUT_MS) {
                error("Codex sign-in timed out. Start again to receive a new code.")
            }

            val response = authApi.pollDeviceCode(
                CodexDeviceTokenPollRequestDto(
                    deviceAuthId = deviceCode.deviceAuthId,
                    userCode = deviceCode.userCode,
                )
            )
            when {
                response.isSuccessful -> authorization = response.body()
                    ?: error("Codex authorization returned an empty response.")
                response.code() == 403 || response.code() == 404 ->
                    delay(deviceCode.intervalSeconds * 1_000L)
                else -> error("Codex authorization failed (${response.code()}).")
            }
        }

        val authorized = authorization
            ?: error("Codex authorization returned an empty response.")
        val tokenResponse = authApi.exchangeAuthorizationCode(
            code = authorized.authorizationCode,
            redirectUri = CodexApiContract.DEVICE_REDIRECT_URL,
            clientId = CodexApiContract.CLIENT_ID,
            codeVerifier = authorized.codeVerifier,
        )
        if (!tokenResponse.isSuccessful) {
            error("Codex token exchange failed (${tokenResponse.code()}).")
        }
        credentialStore.saveCodexTokens(tokenResponse.body().requireCompleteTokens())
        fetchWeekly(urgent = true).getOrThrow()
    }

    override suspend fun fetchWeekly(urgent: Boolean): Result<CodexUsage> = runCatching {
        val nowMs = System.currentTimeMillis()
        val usage = if (!urgent && nowMs - lastFetchTimeMs < MIN_FETCH_INTERVAL_MS) {
            dataStore.cachedUsage.first()
                ?: error("Codex usage has not been fetched yet.")
        } else {
            var tokens = validTokens()
            var response = usageApi.getUsage(
                authorization = "Bearer ${tokens.accessToken}",
                accountId = tokens.accountId,
            )
            if (response.code() == 401 || response.code() == 403) {
                tokens = refreshTokens(force = true)
                response = usageApi.getUsage(
                    authorization = "Bearer ${tokens.accessToken}",
                    accountId = tokens.accountId,
                )
            }
            if (!response.isSuccessful) {
                error("Codex usage request failed (${response.code()}).")
            }
            val usage = response.body()?.toWeeklyDomain()
                ?: error("Codex weekly limit was not present in the response.")
            dataStore.save(usage)
            lastFetchTimeMs = nowMs
            usage
        }
        runCatching { pushCodexDataToWidgets(context, usage) }
        usage
    }

    override suspend fun disconnect() {
        credentialStore.clearCodexTokens()
        dataStore.clear()
        lastFetchTimeMs = 0L
        runCatching { clearCodexWidgetData(context) }
    }

    override suspend fun clearCachedData() {
        dataStore.clear()
        lastFetchTimeMs = 0L
    }

    private suspend fun validTokens(): CodexAuthTokens {
        val current = credentialStore.getCodexTokens()
            ?: error("Connect Codex in Settings.")
        return if (JwtClaims.isExpiringSoon(current.accessToken)) {
            refreshTokens(force = false)
        } else {
            current
        }
    }

    private suspend fun refreshTokens(force: Boolean): CodexAuthTokens = tokenMutex.withLock {
        val current = credentialStore.getCodexTokens()
            ?: error("Connect Codex in Settings.")
        if (!force && !JwtClaims.isExpiringSoon(current.accessToken)) return@withLock current

        val response = authApi.refreshTokens(
            CodexRefreshTokenRequestDto(
                clientId = CodexApiContract.CLIENT_ID,
                refreshToken = current.refreshToken,
            )
        )
        if (!response.isSuccessful) {
            error("Codex sign-in expired. Disconnect and connect Codex again.")
        }
        val refreshed = response.body() ?: error("Codex token refresh returned no data.")
        val accessToken = refreshed.accessToken ?: error("Codex token refresh omitted access token.")
        val idToken = refreshed.idToken ?: current.idToken
        val updated = CodexAuthTokens(
            accessToken = accessToken,
            refreshToken = refreshed.refreshToken ?: current.refreshToken,
            idToken = idToken,
            accountId = JwtClaims.accountId(idToken, accessToken) ?: current.accountId,
        )
        credentialStore.saveCodexTokens(updated)
        updated
    }

    private fun CodexTokenResponseDto?.requireCompleteTokens(): CodexAuthTokens {
        val response = this ?: error("Codex token exchange returned no data.")
        val access = response.accessToken ?: error("Codex access token was missing.")
        val refresh = response.refreshToken ?: error("Codex refresh token was missing.")
        return CodexAuthTokens(
            accessToken = access,
            refreshToken = refresh,
            idToken = response.idToken,
            accountId = JwtClaims.accountId(response.idToken, access),
        )
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_SECONDS = 5L
        const val MIN_POLL_INTERVAL_SECONDS = 1L
        const val DEVICE_LOGIN_TIMEOUT_MS = 15L * 60L * 1_000L
        const val MIN_FETCH_INTERVAL_MS = 5L * 60L * 1_000L
    }
}

internal object JwtClaims {
    private const val ACCOUNT_CLAIMS_KEY = "https://api.openai.com/auth"
    private const val ACCOUNT_ID_KEY = "chatgpt_account_id"
    private const val REFRESH_WINDOW_SECONDS = 5L * 60L

    fun isExpiringSoon(token: String, now: Instant = Instant.now()): Boolean {
        val expiration = payload(token)?.get("exp")?.asLong ?: return false
        return expiration <= now.epochSecond + REFRESH_WINDOW_SECONDS
    }

    fun accountId(idToken: String?, accessToken: String): String? =
        idToken?.let(::payload)?.findAccountId()
            ?: payload(accessToken).findAccountId()

    private fun JsonObject?.findAccountId(): String? {
        if (this == null) return null
        get(ACCOUNT_ID_KEY)?.takeIf { it.isJsonPrimitive }?.asString?.let { return it }
        return getAsJsonObject(ACCOUNT_CLAIMS_KEY)
            ?.get(ACCOUNT_ID_KEY)
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
    }

    private fun payload(token: String): JsonObject? = runCatching {
        val encoded = token.split('.').getOrNull(1) ?: return null
        val decoded = Base64.getUrlDecoder().decode(encoded.padBase64())
        JsonParser.parseString(String(decoded, Charsets.UTF_8)).asJsonObject
    }.getOrNull()

    private fun String.padBase64(): String = this + "=".repeat((4 - length % 4) % 4)
}
