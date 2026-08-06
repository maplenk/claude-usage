package com.qbapps.claudeusage.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores sensitive credentials (session key, selected org ID) in
 * EncryptedSharedPreferences backed by the Android Keystore.
 */
@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSessionKey(sessionKey: String) {
        prefs.edit().putString(KEY_SESSION, sessionKey).apply()
    }

    fun getSessionKey(): String? {
        return prefs.getString(KEY_SESSION, null)
    }

    fun saveOrgId(orgId: String) {
        prefs.edit().putString(KEY_ORG_ID, orgId).apply()
    }

    fun getOrgId(): String? {
        return prefs.getString(KEY_ORG_ID, null)
    }

    fun saveCodexTokens(tokens: CodexAuthTokens) {
        prefs.edit()
            .putString(KEY_CODEX_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_CODEX_REFRESH_TOKEN, tokens.refreshToken)
            .putString(KEY_CODEX_ID_TOKEN, tokens.idToken)
            .putString(KEY_CODEX_ACCOUNT_ID, tokens.accountId)
            .apply()
    }

    fun getCodexTokens(): CodexAuthTokens? {
        val accessToken = prefs.getString(KEY_CODEX_ACCESS_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val refreshToken = prefs.getString(KEY_CODEX_REFRESH_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return CodexAuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            idToken = prefs.getString(KEY_CODEX_ID_TOKEN, null),
            accountId = prefs.getString(KEY_CODEX_ACCOUNT_ID, null),
        )
    }

    fun clearCodexTokens() {
        prefs.edit()
            .remove(KEY_CODEX_ACCESS_TOKEN)
            .remove(KEY_CODEX_REFRESH_TOKEN)
            .remove(KEY_CODEX_ID_TOKEN)
            .remove(KEY_CODEX_ACCOUNT_ID)
            .apply()
    }

    fun saveGrokTokens(tokens: GrokAuthTokens) {
        prefs.edit()
            .putString(KEY_GROK_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_GROK_REFRESH_TOKEN, tokens.refreshToken)
            .putString(KEY_GROK_ID_TOKEN, tokens.idToken)
            .putLong(KEY_GROK_EXPIRES_AT_MS, tokens.expiresAtMs)
            .apply()
    }

    fun getGrokTokens(): GrokAuthTokens? {
        val accessToken = prefs.getString(KEY_GROK_ACCESS_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val refreshToken = prefs.getString(KEY_GROK_REFRESH_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return GrokAuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            idToken = prefs.getString(KEY_GROK_ID_TOKEN, null),
            expiresAtMs = prefs.getLong(KEY_GROK_EXPIRES_AT_MS, Long.MAX_VALUE),
        )
    }

    fun clearGrokTokens() {
        prefs.edit()
            .remove(KEY_GROK_ACCESS_TOKEN)
            .remove(KEY_GROK_REFRESH_TOKEN)
            .remove(KEY_GROK_ID_TOKEN)
            .remove(KEY_GROK_EXPIRES_AT_MS)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "claude_secure_prefs"
        private const val KEY_SESSION = "session_key"
        private const val KEY_ORG_ID = "selected_org_id"
        private const val KEY_CODEX_ACCESS_TOKEN = "codex_access_token"
        private const val KEY_CODEX_REFRESH_TOKEN = "codex_refresh_token"
        private const val KEY_CODEX_ID_TOKEN = "codex_id_token"
        private const val KEY_CODEX_ACCOUNT_ID = "codex_account_id"
        private const val KEY_GROK_ACCESS_TOKEN = "grok_access_token"
        private const val KEY_GROK_REFRESH_TOKEN = "grok_refresh_token"
        private const val KEY_GROK_ID_TOKEN = "grok_id_token"
        private const val KEY_GROK_EXPIRES_AT_MS = "grok_expires_at_ms"
    }
}

data class CodexAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String?,
    val accountId: String?,
)

data class GrokAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String?,
    val expiresAtMs: Long,
)
