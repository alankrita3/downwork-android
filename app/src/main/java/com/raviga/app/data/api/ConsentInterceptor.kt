package com.raviga.app.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Notices `403 consent_required` whose `details.missing` names terms or
 * privacy: the legal versions moved on (contract section 4) and the client
 * must agree again before content routes work. AI consent is handled where
 * it is asked for; this only covers the legal documents.
 */
class ConsentInterceptor(private val onLegalRequired: () -> Unit) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 403) {
            val body = runCatching { response.peekBody(8_192).string() }.getOrDefault("")
            if ("consent_required" in body && ("\"terms\"" in body || "\"privacy\"" in body)) onLegalRequired()
        }
        return response
    }
}
