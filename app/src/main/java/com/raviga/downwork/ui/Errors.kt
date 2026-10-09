package com.raviga.downwork.ui

import com.raviga.downwork.data.api.ApiException
import com.raviga.downwork.ui.status.StatusCopy

/** The one line of copy a failure gets, by error code. */
fun Throwable.userLine(): String = when (this) {
    is ApiException -> {
        // The server's message, when it sent one (otherwise the exception carries the bare code).
        val server = message?.takeIf { it.isNotBlank() && it != code }
        when {
            // Policy refusals are final from the app's side; say where to appeal.
            code == ApiException.CONTENT_REJECTED && detailString("kind") == "policy" ->
                StatusCopy.errorLine(code, server) + StatusCopy.mistakeSuffix()
            // The terms changed: the app shows them again; the line just says why the action stopped.
            code == ApiException.CONSENT_REQUIRED && detailStrings("missing").any { it == "terms" || it == "privacy" } ->
                "Agree to the updated terms to continue."
            // A paused account: the server's message says for how long.
            code == ApiException.RATE_LIMITED && detailString("reason") == "paused" && server != null -> server
            else -> StatusCopy.errorLine(code, server)
        }
    }
    else -> StatusCopy.errorLine("internal", message)
}

fun Throwable.apiCode(): String? = (this as? ApiException)?.code
