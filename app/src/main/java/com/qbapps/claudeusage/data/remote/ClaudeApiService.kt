package com.qbapps.claudeusage.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface ClaudeApiService {

    @GET("api/organizations")
    suspend fun getOrganizations(
        @Header("Cookie") sessionCookie: String
    ): Response<List<OrganizationDto>>

    @GET("api/organizations/{orgId}/usage")
    suspend fun getUsage(
        @Path("orgId") orgId: String,
        @Header("Cookie") sessionCookie: String
    ): Response<UsageResponseDto>

    companion object {
        const val BASE_URL = "https://claude.ai/"

        fun formatSessionCookie(sessionKey: String): String =
            "sessionKey=$sessionKey"
    }
}
