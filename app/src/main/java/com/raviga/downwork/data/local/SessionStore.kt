package com.raviga.downwork.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the network layer needs of the identity; [SessionStore] in the app, a fake in tests. */
interface SessionTokens {
    val clientId: String?
    val accessToken: String?
    val installId: String
    fun set(clientId: String, accessToken: String, recoveryKey: String?)
}

/**
 * The client identity: clientId, bearer token and the recovery key, kept in
 * EncryptedSharedPreferences (Android's nearest thing to Keychain). Excluded
 * from backup so a restored phone registers afresh or links with the key.
 */
class SessionStore(context: Context) : SessionTokens {

    private val prefs: SharedPreferences = run {
        // Keystore is broken on a handful of devices; a private plain file beats a crash.
        // Once the plain file holds an identity it stays in use, so a Keystore that
        // recovers later cannot swap this phone back to an older identity.
        val plain = context.getSharedPreferences("session_plain", Context.MODE_PRIVATE)
        if (plain.contains(KEY_INSTALL_ID)) return@run plain
        runCatching {
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
        }.getOrElse { plain }
    }

    private val _clientIdFlow = MutableStateFlow(prefs.getString(KEY_CLIENT_ID, null))

    /** The client this device belongs to; changes on register, recover, sign-out and deletion. */
    val clientIdFlow: StateFlow<String?> = _clientIdFlow.asStateFlow()

    override val clientId: String? get() = prefs.getString(KEY_CLIENT_ID, null)

    override var accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_TOKEN, value) }

    var recoveryKey: String?
        get() = prefs.getString(KEY_RECOVERY, null)
        set(value) = prefs.edit { putString(KEY_RECOVERY, value) }

    val isRegistered: Boolean get() = !clientId.isNullOrBlank() && !accessToken.isNullOrBlank()

    /** Stable per-install UUID; re-registering with it yields the same clientId. */
    override val installId: String
        get() = prefs.getString(KEY_INSTALL_ID, null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY_INSTALL_ID, it) }
        }

    override fun set(clientId: String, accessToken: String, recoveryKey: String?) {
        prefs.edit {
            putString(KEY_CLIENT_ID, clientId)
            putString(KEY_TOKEN, accessToken)
            if (recoveryKey != null) putString(KEY_RECOVERY, recoveryKey)
        }
        _clientIdFlow.value = clientId
    }

    /** Forgets the identity but keeps the installId so a re-register maps to the same client. */
    fun clear() {
        val install = installId
        prefs.edit {
            clear()
            putString(KEY_INSTALL_ID, install)
        }
        _clientIdFlow.value = null
    }

    /** Full wipe, including the installId (after account deletion). */
    fun wipe() {
        prefs.edit { clear() }
        _clientIdFlow.value = null
    }

    private companion object {
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_TOKEN = "access_token"
        const val KEY_RECOVERY = "recovery_key"
        const val KEY_INSTALL_ID = "install_id"
    }
}
