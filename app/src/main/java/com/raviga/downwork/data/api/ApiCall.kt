package com.raviga.downwork.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * Runs an API call and normalises every failure to [ApiException]; the
 * coroutine machinery's own cancellation is passed through untouched.
 */
suspend inline fun <T> apiCall(json: Json, crossinline block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    throw ApiException.from(t, json)
}

/** What a long-running job is doing right now, for the progress copy. */
data class JobState(val progress: Float, val message: String?)

/**
 * Polls a [Job] until it settles and decodes its result: 2s for 30s, then 5s.
 * A dropped connection mid-poll is retried (the job keeps running server-side,
 * and giving up would tempt a second, duplicate job); after [MAX_WAIT_MS] it
 * stops with [ApiException.JOB_TIMEOUT].
 */
class JobRunner(private val api: DownWorkApi, private val json: Json) {

    private companion object {
        const val MAX_WAIT_MS = 10 * 60 * 1000L
        const val MAX_POLL_FAILURES = 5
        val RETRYABLE = setOf(ApiException.NETWORK, ApiException.INTERNAL, ApiException.RATE_LIMITED)
    }

    class JobFailed(val job: Job) : ApiException(
        code = job.error?.code ?: "job_failed",
        message = job.error?.message ?: "The job failed.",
        details = job.error?.details,
    )

    suspend fun <T> await(
        initial: Job,
        deserializer: DeserializationStrategy<T>,
        onProgress: (JobState) -> Unit = {},
    ): T {
        var job = initial
        val startedAt = System.currentTimeMillis()
        onProgress(job.state())
        var failures = 0
        while (!job.isDone && !job.isFailed && !job.isExpired) {
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed > MAX_WAIT_MS) {
                throw ApiException(ApiException.JOB_TIMEOUT, message = "This is taking longer than usual.")
            }
            delay(if (elapsed < 30_000) 2_000L else 5_000L)
            val jobId = job.jobId
            job = try {
                apiCall(json) { api.job(jobId) }.also { failures = 0 }
            } catch (e: ApiException) {
                if (e.code !in RETRYABLE || ++failures >= MAX_POLL_FAILURES) throw e
                delay(failures * 2_000L)
                continue
            }
            onProgress(job.state())
        }
        if (job.isFailed) throw JobFailed(job)
        if (job.isExpired) throw ApiException(ApiException.JOB_EXPIRED, message = "The result expired before it was read.")
        val result = job.result ?: throw ApiException("job_failed", message = "The job finished without a result.")
        return json.decodeFromJsonElement(deserializer, result)
    }

    private fun Job.state() = JobState(
        progress = (progress ?: if (isDone) 1.0 else 0.0).toFloat().coerceIn(0f, 1f),
        message = progressMessage,
    )
}
