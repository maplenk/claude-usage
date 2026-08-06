package com.qbapps.claudeusage.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface GrokApiService {
    @FormUrlEncoded
    @POST
    suspend fun requestDeviceCode(
        @Url url: String = GrokApiContract.DEVICE_CODE_URL,
        @Field("client_id") clientId: String = GrokApiContract.CLIENT_ID,
        @Field("scope") scope: String = GrokApiContract.SCOPES,
        @Field("referrer") referrer: String = GrokApiContract.REFERRER,
        @Header("x-grok-client-version") clientVersion: String,
        @Header("x-grok-client-surface") clientSurface: String = "ui",
    ): Response<GrokDeviceCodeResponseDto>

    @FormUrlEncoded
    @POST
    suspend fun pollDeviceCode(
        @Url url: String = GrokApiContract.TOKEN_URL,
        @Field("grant_type") grantType: String = GrokApiContract.DEVICE_GRANT_TYPE,
        @Field("device_code") deviceCode: String,
        @Field("client_id") clientId: String = GrokApiContract.CLIENT_ID,
        @Header("x-grok-client-version") clientVersion: String,
        @Header("x-grok-client-surface") clientSurface: String = "ui",
    ): Response<GrokTokenResponseDto>

    @FormUrlEncoded
    @POST
    suspend fun refreshTokens(
        @Url url: String = GrokApiContract.TOKEN_URL,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("client_id") clientId: String = GrokApiContract.CLIENT_ID,
        @Field("refresh_token") refreshToken: String,
    ): Response<GrokTokenResponseDto>

    @GET
    suspend fun getCredits(
        @Url url: String = GrokApiContract.CREDITS_URL,
        @Header("Authorization") authorization: String,
        @Header("X-XAI-Token-Auth") tokenAuth: String = GrokApiContract.TOKEN_AUTH_HEADER,
        @Header("Accept") accept: String = "application/json",
        @Header("User-Agent") userAgent: String = "OpenUsage Android",
    ): Response<GrokCreditsResponseDto>
}

data class GrokDeviceCodeResponseDto(
    @SerializedName("device_code") val deviceCode: String,
    @SerializedName("user_code") val userCode: String,
    @SerializedName("verification_uri") val verificationUri: String,
    @SerializedName("verification_uri_complete") val verificationUriComplete: String?,
    @SerializedName("expires_in") val expiresIn: Long,
    val interval: Long?,
)

data class GrokTokenResponseDto(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("id_token") val idToken: String?,
    @SerializedName("expires_in") val expiresIn: Long?,
)

data class GrokCreditsResponseDto(
    val config: GrokCreditsConfigDto?,
)

data class GrokCreditsConfigDto(
    @SerializedName("creditUsagePercent") val creditUsagePercent: Double?,
    @SerializedName("currentPeriod") val currentPeriod: GrokCurrentPeriodDto?,
)

data class GrokCurrentPeriodDto(
    val type: String?,
    val start: String?,
    val end: String?,
)

object GrokApiContract {
    const val BASE_URL = "https://auth.x.ai/"
    const val DEVICE_CODE_URL = "https://auth.x.ai/oauth2/device/code"
    const val TOKEN_URL = "https://auth.x.ai/oauth2/token"
    const val CREDITS_URL = "https://cli-chat-proxy.grok.com/v1/billing?format=credits"
    const val CLIENT_ID = "b1a00492-073a-47ea-816f-4c329264a828"
    const val TOKEN_AUTH_HEADER = "xai-grok-cli"
    const val REFERRER = "openusage-android"
    const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
    const val WEEKLY_PERIOD_TYPE = "USAGE_PERIOD_TYPE_WEEKLY"
    const val SCOPES = "openid profile email offline_access grok-cli:access api:access"
}
