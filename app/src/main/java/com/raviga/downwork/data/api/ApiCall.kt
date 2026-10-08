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

/** Polls a [Job] until it settles and decodes its result. 2s for 30s, then 5s. */
class JobRunner(private val api: DownWorkApi, private val json: Json) {

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
        while (!job.isDone && !job.isFailed) {
            val elapsed = System.currentTimeMillis() - startedAt
            delay(if (elapsed < 30_000) 2_000 else 5_000)
            job = apiCall(json) { api.job(job.jobId) }
            onProgress(job.state())
        }
        if (job.isFailed) throw JobFailed(job)
        val result = job.result ?: throw ApiException("job_failed", message = "The job finished without a result.")
        return json.decodeFromJsonElement(deserializer, result)
    }

    private fun Job.state() = JobState(
        progress = (progress ?: if (isDone) 1.0 else 0.0).toFloat().coerceIn(0f, 1f),
        message = progressMessage,
    )
}
