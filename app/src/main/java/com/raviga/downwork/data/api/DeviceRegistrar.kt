package com.raviga.downwork.data.api

import android.os.Build
import com.raviga.downwork.BuildConfig
import com.raviga.downwork.data.local.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.Locale

/** Device facts sent at register and recover. */
object DeviceInfo {
    val appVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')
    val deviceModel: String get() = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ")
    val osVersion: String get() = "Android ${Build.VERSION.RELEASE}"
    val locale: String get() = Locale.getDefault().toLanguageTag()

    fun registerRequest(installId: String, linkCode: String? = null) = RegisterDeviceRequest(
        installId = installId,
        appVersion = appVersion,
        deviceModel = deviceModel,
        osVersion = osVersion,
        locale = locale,
        linkCode = linkCode,
    )
}

/**
 * On 401 the token was revoked (another device re-registered, or a deletion).
 * Re-registering with the same installId returns the same clientId and a fresh
 * token, so the app heals itself without bothering the client. Runs on OkHttp's
 * thread, hence the blocking call through a plain client.
 */
class ReRegisterAuthenticator(
    private val baseUrl: String,
    private val plainClient: OkHttpClient,
    private val json: Json,
    private val sessionStore: SessionStore,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (priorResponses(response) >= 2) return null
        if (response.request.url.encodedPath.endsWith("/devices/register")) return null
        synchronized(this) {
            val failed = response.request.header("Authorization")
            val current = sessionStore.accessToken
            if (!current.isNullOrBlank() && failed != "Bearer $current") {
                return response.request.newBuilder().header("Authorization", "Bearer $current").build()
            }
            val fresh = registerBlocking() ?: return null
            return response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
        }
    }

    private fun registerBlocking(): String? {
        val body = json.encodeToString(RegisterDeviceRequest.serializer(), DeviceInfo.registerRequest(sessionStore.installId))
        val request = Request.Builder()
            .url(baseUrl + "devices/register")
            .header("X-App-Platform", "android")
            .header("X-App-Version", DeviceInfo.appVersion)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            plainClient.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return null
                val parsed = json.decodeFromString(RegisterDeviceResponse.serializer(), r.body!!.string())
                sessionStore.set(parsed.clientId, parsed.accessToken, parsed.recoveryKey)
                parsed.accessToken
            }
        }.getOrNull()
    }

    private fun priorResponses(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) { count++; prior = prior.priorResponse }
        return count
    }
}
