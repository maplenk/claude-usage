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

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "claude_secure_prefs"
        private const val KEY_SESSION = "session_key"
        private const val KEY_ORG_ID = "selected_org_id"
    }
}
