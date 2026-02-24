package com.qbapps.claudeusage.data.remote

import com.qbapps.claudeusage.data.local.SecureCredentialStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that automatically attaches the session key as a Cookie
 * header on every outgoing request. If no session key is stored, the request
 * proceeds without the header (the server will return 401).
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val credentialStore: SecureCredentialStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // If the request already carries a Cookie header (e.g. from an explicit
        // @Header annotation when calling fetchOrganizations with a provided key),
        // do not overwrite it.
        if (originalRequest.header("Cookie") != null) {
            return chain.proceed(originalRequest)
        }

        val sessionKey = credentialStore.getSessionKey()
            ?: return chain.proceed(originalRequest)

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Cookie", "sessionKey=$sessionKey")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
