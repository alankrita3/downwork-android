package com.raviga.app.data.repo

import com.raviga.app.data.api.AiAppendRequest
import com.raviga.app.data.api.AiDocumentResult
import com.raviga.app.data.api.AiDraftRequest
import com.raviga.app.data.api.AiInput
import com.raviga.app.data.api.AiRegenerateRequest
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.DocumentBody
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.api.JobEnvelope
import com.raviga.app.data.api.JobRunner
import com.raviga.app.data.api.JobState
import com.raviga.app.data.api.QuoteRequest
import com.raviga.app.data.api.QuoteResult
import com.raviga.app.data.api.apiCall
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * The stateless AI calls (contract v0.6): the text goes up, a brief or a
 * quote comes back, and the server keeps nothing. Results are short-lived;
 * if one expired before we read it, the call is simply run again once.
 */
class AiRepository(
    private val api: RavigaApi,
    private val json: Json,
    private val jobs: JobRunner,
) {
    suspend fun draft(inputs: List<AiInput>, title: String?, onProgress: (JobState) -> Unit = {}): AiDocumentResult =
        run(AiDocumentResult.serializer(), onProgress) { api.aiDraft(AiDraftRequest(inputs, title?.ifBlank { null })) }

    suspend fun append(document: DocumentBody, inputs: List<AiInput>, onProgress: (JobState) -> Unit = {}): AiDocumentResult =
        run(AiDocumentResult.serializer(), onProgress) { api.aiAppend(AiAppendRequest(document, inputs)) }

    suspend fun regenerate(
        document: DocumentBody,
        sectionId: String,
        instruction: String,
        context: List<AiInput>?,
        onProgress: (JobState) -> Unit = {},
    ): AiDocumentResult = run(AiDocumentResult.serializer(), onProgress) {
        api.aiRegenerate(AiRegenerateRequest(document, sectionId, instruction.trim().ifBlank { null }, context?.takeIf { it.isNotEmpty() }))
    }

    suspend fun quote(document: DocumentBody, onProgress: (JobState) -> Unit = {}): QuoteResult =
        run(QuoteResult.serializer(), onProgress) { api.createQuote(QuoteRequest(document)) }

    private suspend fun <T> run(
        deserializer: DeserializationStrategy<T>,
        onProgress: (JobState) -> Unit,
        start: suspend () -> JobEnvelope,
    ): T {
        var attempt = 0
        while (true) {
            try {
                val job = apiCall(json) { start() }.job
                return jobs.await(job, deserializer, onProgress)
            } catch (e: ApiException) {
                if (e.code != ApiException.JOB_EXPIRED || ++attempt > 1) throw e
            }
        }
    }
}
