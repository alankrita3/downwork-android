package com.raviga.downwork.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The client identity: clientId, bearer token and the recovery key, kept in
 * EncryptedSharedPreferences (Android's nearest thing to Keychain). Excluded
 * from backup so a restored phone registers afresh or links with the key.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Keystore can be broken on a handful of devices; a plain file beats a crash.
        context.getSharedPreferences("session_plain", Context.MODE_PRIVATE)
    }

    var clientId: String?
        get() = prefs.getString(KEY_CLIENT_ID, null)
        set(value) = prefs.edit { putString(KEY_CLIENT_ID, value) }

    var accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_TOKEN, value) }

    var recoveryKey: String?
        get() = prefs.getString(KEY_RECOVERY, null)
        set(value) = prefs.edit { putString(KEY_RECOVERY, value) }

    val isRegistered: Boolean get() = !clientId.isNullOrBlank() && !accessToken.isNullOrBlank()

    /** Stable per-install UUID; re-registering with it yields the same clientId. */
    val installId: String
        get() = prefs.getString(KEY_INSTALL_ID, null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY_INSTALL_ID, it) }
        }

    fun set(clientId: String, accessToken: String, recoveryKey: String?) {
        prefs.edit {
            putString(KEY_CLIENT_ID, clientId)
            putString(KEY_TOKEN, accessToken)
            if (recoveryKey != null) putString(KEY_RECOVERY, recoveryKey)
        }
    }

    /** Forgets the identity but keeps the installId so a re-register maps to the same client. */
    fun clear() {
        val install = installId
        prefs.edit {
            clear()
            putString(KEY_INSTALL_ID, install)
        }
    }

    /** Full wipe, including the installId (after account deletion). */
    fun wipe() = prefs.edit { clear() }

    private companion object {
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_TOKEN = "access_token"
        const val KEY_RECOVERY = "recovery_key"
        const val KEY_INSTALL_ID = "install_id"
    }
}
