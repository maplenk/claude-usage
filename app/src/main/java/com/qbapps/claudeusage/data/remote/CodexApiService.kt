package com.qbapps.claudeusage.data.remote

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface CodexAuthApiService {
    @POST("api/accounts/deviceauth/usercode")
    suspend fun requestDeviceCode(
        @Body request: CodexDeviceCodeRequestDto,
    ): Response<CodexDeviceCodeResponseDto>

    @POST("api/accounts/deviceauth/token")
    suspend fun pollDeviceCode(
        @Body request: CodexDeviceTokenPollRequestDto,
    ): Response<CodexDeviceTokenPollResponseDto>

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeAuthorizationCode(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String,
    ): Response<CodexTokenResponseDto>

    @POST("oauth/token")
    suspend fun refreshTokens(
        @Body request: CodexRefreshTokenRequestDto,
    ): Response<CodexTokenResponseDto>
}

interface CodexUsageApiService {
    @GET("wham/usage")
    suspend fun getUsage(
        @Header("Authorization") authorization: String,
        @Header("ChatGPT-Account-Id") accountId: String?,
        @Header("User-Agent") userAgent: String = "codex-cli",
    ): Response<CodexUsageResponseDto>
}

data class CodexDeviceCodeRequestDto(
    @SerializedName("client_id") val clientId: String,
)

data class CodexDeviceCodeResponseDto(
    @SerializedName("device_auth_id") val deviceAuthId: String,
    @SerializedName(value = "user_code", alternate = ["usercode"])
    val userCode: String,
    val interval: JsonElement?,
)

data class CodexDeviceTokenPollRequestDto(
    @SerializedName("device_auth_id") val deviceAuthId: String,
    @SerializedName("user_code") val userCode: String,
)

data class CodexDeviceTokenPollResponseDto(
    @SerializedName("authorization_code") val authorizationCode: String,
    @SerializedName("code_challenge") val codeChallenge: String,
    @SerializedName("code_verifier") val codeVerifier: String,
)

data class CodexTokenResponseDto(
    @SerializedName("id_token") val idToken: String?,
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
)

data class CodexRefreshTokenRequestDto(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("grant_type") val grantType: String = "refresh_token",
    @SerializedName("refresh_token") val refreshToken: String,
)

data class CodexUsageResponseDto(
    @SerializedName("rate_limit") val rateLimit: CodexRateLimitDto?,
)

data class CodexRateLimitDto(
    @SerializedName("primary_window") val primaryWindow: CodexRateLimitWindowDto?,
    @SerializedName("secondary_window") val secondaryWindow: CodexRateLimitWindowDto?,
)

data class CodexRateLimitWindowDto(
    @SerializedName("used_percent") val usedPercent: Double?,
    @SerializedName("limit_window_seconds") val limitWindowSeconds: Long?,
    @SerializedName("reset_at") val resetAtEpochSeconds: Long?,
    @SerializedName("reset_after_seconds") val resetAfterSeconds: Long?,
)

object CodexApiContract {
    const val AUTH_BASE_URL = "https://auth.openai.com/"
    const val USAGE_BASE_URL = "https://chatgpt.com/backend-api/"
    const val DEVICE_VERIFICATION_URL = "https://auth.openai.com/codex/device"
    const val DEVICE_REDIRECT_URL = "https://auth.openai.com/deviceauth/callback"
    const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    const val WEEKLY_WINDOW_SECONDS = 7L * 24L * 60L * 60L
}
