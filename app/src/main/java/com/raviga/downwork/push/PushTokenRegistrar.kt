package com.raviga.downwork.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.raviga.downwork.data.local.PrefsStore
import com.raviga.downwork.data.repo.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Keeps the backend's copy of the FCM token current: re-sent on every token
 * refresh and once per app start (contract section 13). Silently does nothing
 * when Firebase is not configured (no google-services.json yet).
 */
class PushTokenRegistrar(
    private val context: Context,
    private val prefs: PrefsStore,
    private val session: SessionRepository,
    private val scope: CoroutineScope,
) {
    private var sentThisProcess = false

    val isFirebaseConfigured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    fun onNewToken(token: String) {
        scope.launch {
            prefs.setPushToken(token, synced = false)
            sentThisProcess = false
            runCatching { syncIfNeeded() }
        }
    }

    suspend fun syncIfNeeded() {
        if (!isFirebaseConfigured || !session.isRegistered) return
        val current = prefs.current()
        val token = current.pushToken ?: runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull() ?: return
        if (current.pushToken == token && current.pushTokenSynced && sentThisProcess) return
        session.registerPushToken(token)
        prefs.setPushToken(token, synced = true)
        sentThisProcess = true
    }
}
