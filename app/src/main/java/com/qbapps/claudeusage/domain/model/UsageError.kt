package com.qbapps.claudeusage.domain.model

sealed class UsageError {
    data object Unauthorized : UsageError()
    data object RateLimited : UsageError()
    data object NetworkError : UsageError()
    data class ServerError(val code: Int, val message: String?) : UsageError()
    data object NoCredentials : UsageError()
    data class Unknown(val throwable: Throwable) : UsageError()
}
