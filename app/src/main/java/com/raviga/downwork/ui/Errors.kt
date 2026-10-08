package com.raviga.downwork.ui

import com.raviga.downwork.data.api.ApiException
import com.raviga.downwork.ui.status.StatusCopy

/** The one line of copy a failure gets, by error code. */
fun Throwable.userLine(): String = when (this) {
    is ApiException -> StatusCopy.errorLine(code, message)
    else -> StatusCopy.errorLine("internal", message)
}

fun Throwable.apiCode(): String? = (this as? ApiException)?.code
