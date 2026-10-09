package com.raviga.app.data.api

import com.raviga.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the bearer token plus the platform headers the contract requires on
 * every request (section 2).
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
            .header("X-App-Platform", "android")
            .header("X-App-Version", BuildConfig.VERSION_NAME.substringBefore('-'))
        val token = tokenProvider()
        if (!token.isNullOrBlank() && request.header("Authorization") == null) {
            builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }
}
