package com.raviga.downwork.data.repo

import com.raviga.downwork.data.api.AddInputRequest
import com.raviga.downwork.data.api.AudioUploader
import com.raviga.downwork.data.api.Comment
import com.raviga.downwork.data.api.CreateProjectRequest
import com.raviga.downwork.data.api.Document
import com.raviga.downwork.data.api.DownWorkApi
import com.raviga.downwork.data.api.InstructionRequest
import com.raviga.downwork.data.api.JobRunner
import com.raviga.downwork.data.api.JobState
import com.raviga.downwork.data.api.NewCommentRequest
import com.raviga.downwork.data.api.PatchInputRequest
import com.raviga.downwork.data.api.PatchProjectRequest
import com.raviga.downwork.data.api.Project
import com.raviga.downwork.data.api.ProjectSummary
import com.raviga.downwork.data.api.Quote
import com.raviga.downwork.data.api.ResubmitRequest
import com.raviga.downwork.data.api.RevisionRequestBody
import com.raviga.downwork.data.api.SaveDocumentRequest
import com.raviga.downwork.data.api.SectionBody
import com.raviga.downwork.data.api.SubmitRequest
import com.raviga.downwork.data.api.TranscribeRequest
import com.raviga.downwork.data.api.TranscriptResult
import com.raviga.downwork.data.api.UploadUrlRequest
import com.raviga.downwork.data.api.VersionSummary
import com.raviga.downwork.data.api.apiCall
import com.raviga.downwork.data.local.CacheStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Projects, their documents and comments. Holds the latest known copy of
 * everything the UI has touched, mirrors it to disk, and funnels every
 * mutation through the API so the backend stays the source of truth.
 */
class ProjectRepository(
    private val api: DownWorkApi,
    private val json: Json,
    private val jobs: JobRunner,
    private val uploader: AudioUploader,
    private val cache: CacheStore,
) {
    private val _summaries = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val summaries: StateFlow<List<ProjectSummary>> = _summaries.asStateFlow()

    private val _loadedOnce = MutableStateFlow(false)
    val loadedOnce: StateFlow<Boolean> = _loadedOnce.asStateFlow()

    private val _projects = MutableStateFlow<Map<String, Project>>(emptyMap())
    private val _documents = MutableStateFlow<Map<String, Document>>(emptyMap())

    fun project(id: String): Flow<Project?> = _projects.map { it[id] }.distinctUntilChanged()
    fun document(id: String): Flow<Document?> = _documents.map { it[id] }.distinctUntilChanged()
    fun cachedProject(id: String): Project? = _projects.value[id]
    fun cachedDocument(id: String): Document? = _documents.value[id]

    suspend fun warmFromCache() {
        cache.read(CacheStore.PROJECTS, ListSerializer(ProjectSummary.serializer()))?.let {
            _summaries.value = it
            _loadedOnce.value = true
        }
    }

    suspend fun warmProject(id: String) {
        if (_projects.value[id] == null) {
            cache.read(CacheStore.project(id), Project.serializer())?.let { p -> _projects.update { it + (id to p) } }
        }
        if (_documents.value[id] == null) {
            cache.read(CacheStore.document(id), Document.serializer())?.let { d -> _documents.update { it + (id to d) } }
        }
    }

    /** Forgets everything held for the previous client (sign-out, recovery, deletion). */
    suspend fun reset() {
        _summaries.value = emptyList()
        _projects.value = emptyMap()
        _documents.value = emptyMap()
        _loadedOnce.value = false
        cache.clearProjects()
    }

    suspend fun refreshAll(): List<ProjectSummary> {
        val list = mutableListOf<ProjectSummary>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = apiCall(json) { api.projects(before = cursor) }
            list += page.projects
            cursor = page.nextCursor
        } while (cursor != null && ++pages < MAX_PAGES)
        _summaries.value = list
        _loadedOnce.value = true
        cache.write(CacheStore.PROJECTS, ListSerializer(ProjectSummary.serializer()), list)
        return list
    }

    suspend fun refresh(id: String): Project = store(apiCall(json) { api.project(id) })

    /** Project plus its document, if one exists yet. */
    suspend fun refreshWithDocument(id: String): Project {
        val project = refresh(id)
        if (project.document != null && cachedDocument(id)?.version != project.document.version) {
            runCatching { refreshDocument(id) }
        }
        return project
    }

    suspend fun refreshDocument(id: String): Document = storeDocument(id, apiCall(json) { api.document(id) })

    suspend fun create(title: String = ""): Project =
        store(apiCall(json) { api.createProject(CreateProjectRequest(title)) })

    suspend fun rename(id: String, title: String): Project =
        store(apiCall(json) { api.patchProject(id, PatchProjectRequest(title)) })

    suspend fun delete(id: String) {
        apiCall(json) { api.deleteProject(id) }
        _projects.update { it - id }
        _documents.update { it - id }
        _summaries.update { list -> list.filterNot { it.id == id } }
        cache.delete(CacheStore.project(id))
        cache.delete(CacheStore.document(id))
        cache.write(CacheStore.PROJECTS, ListSerializer(ProjectSummary.serializer()), _summaries.value)
    }

    suspend fun cancel(id: String): Project = store(apiCall(json) { api.cancelProject(id) })

    // ----- Inputs (section 8) -----

    /** Uploads a recording and waits for its transcript. Returns the audioId too. */
    suspend fun transcribe(
        id: String,
        file: File,
        durationSec: Int,
        languageHint: String?,
        onProgress: (JobState) -> Unit = {},
    ): Pair<String, TranscriptResult> {
        onProgress(JobState(0.05f, "Uploading your recording"))
        val slot = apiCall(json) { api.audioUploadUrl(id, UploadUrlRequest(bytes = file.length(), durationSec = durationSec)) }
        apiCall(json) { uploader.upload(file, slot) }
        onProgress(JobState(0.2f, "Listening back"))
        val job = apiCall(json) { api.transcribe(id, TranscribeRequest(audioId = slot.audioId, languageHint = languageHint)) }.job
        val result = jobs.await(job, TranscriptResult.serializer(), onProgress = onProgress)
        return slot.audioId to result
    }

    suspend fun addInput(
        id: String,
        kind: String,
        text: String,
        audioId: String? = null,
        durationSec: Int? = null,
        languageDetected: String? = null,
    ): Project = store(
        apiCall(json) {
            api.addInput(id, AddInputRequest(kind = kind, text = text, audioId = audioId, languageDetected = languageDetected, durationSec = durationSec))
        },
    )

    suspend fun editInput(id: String, inputId: String, text: String): Project =
        store(apiCall(json) { api.patchInput(id, inputId, PatchInputRequest(text)) })

    suspend fun deleteInput(id: String, inputId: String): Project =
        store(apiCall(json) { api.deleteInput(id, inputId) })

    // ----- Document (section 9) -----

    suspend fun generate(id: String, instruction: String = "", onProgress: (JobState) -> Unit = {}): Document {
        val job = apiCall(json) { api.generateDocument(id, InstructionRequest(instruction)) }.job
        val doc = jobs.await(job, Document.serializer(), onProgress = onProgress)
        storeDocument(id, doc)
        runCatching { refresh(id) }
        return doc
    }

    suspend fun append(id: String, onProgress: (JobState) -> Unit = {}): Document {
        val job = apiCall(json) { api.appendDocument(id) }.job
        val doc = jobs.await(job, Document.serializer(), onProgress = onProgress)
        storeDocument(id, doc)
        runCatching { refresh(id) }
        return doc
    }

    suspend fun regenerateSection(id: String, sectionId: String, instruction: String, onProgress: (JobState) -> Unit = {}): Document {
        val job = apiCall(json) { api.regenerateSection(id, sectionId, InstructionRequest(instruction.trim())) }.job
        val doc = jobs.await(job, Document.serializer(), onProgress = onProgress)
        storeDocument(id, doc)
        runCatching { refresh(id) }
        return doc
    }

    suspend fun saveDocument(id: String, baseVersion: Int, title: String?, sections: List<SectionBody>): Document {
        val doc = apiCall(json) { api.saveDocument(id, SaveDocumentRequest(baseVersion, title, sections)) }
        storeDocument(id, doc)
        _projects.value[id]?.let { p ->
            store(p.copy(document = doc.summary(), title = doc.title.ifBlank { p.title }, quote = p.quote?.copy(status = "stale")))
        }
        return doc
    }

    suspend fun versions(id: String): List<VersionSummary> = apiCall(json) { api.documentVersions(id) }.versions

    suspend fun version(id: String, version: Int): Document = apiCall(json) { api.documentVersion(id, version) }

    suspend fun restore(id: String, version: Int): Document {
        val doc = apiCall(json) { api.restoreDocumentVersion(id, version) }
        storeDocument(id, doc)
        runCatching { refresh(id) }
        return doc
    }

    // ----- Quote, submit, review (sections 10, 11) -----

    suspend fun quote(id: String, onProgress: (JobState) -> Unit = {}): Quote {
        val job = apiCall(json) { api.quote(id) }.job
        val quote = jobs.await(job, Quote.serializer(), onProgress = onProgress)
        _projects.value[id]?.let { store(it.copy(quote = quote)) }
        runCatching { refresh(id) }
        return quote
    }

    /** [idempotencyKey] must be reused when retrying the same submit (contract section 2). */
    suspend fun submit(id: String, quoteId: String, githubUsername: String?, awsAccountId: String?, idempotencyKey: String): Project =
        store(apiCall(json) { api.submit(id, idempotencyKey, SubmitRequest(quoteId, githubUsername, awsAccountId)) })

    suspend fun resubmit(id: String, quoteId: String, idempotencyKey: String): Project =
        store(apiCall(json) { api.resubmit(id, idempotencyKey, ResubmitRequest(quoteId)) })

    /** Newest first; the backend marks team comments read. */
    suspend fun comments(id: String): List<Comment> {
        val list = apiCall(json) { api.comments(id) }.comments
        _projects.value[id]?.let { p -> store(p.copy(review = p.review.copy(comments = list), unreadComments = 0)) }
        return list
    }

    suspend fun addComment(id: String, body: String): Comment {
        val comment = apiCall(json) { api.addComment(id, NewCommentRequest(body.trim())) }
        _projects.value[id]?.let { p -> store(p.copy(review = p.review.copy(comments = listOf(comment) + p.review.comments))) }
        return comment
    }

    // ----- Delivery (section 12) -----

    suspend fun accept(id: String): Project = store(apiCall(json) { api.accept(id) })

    suspend fun requestRevision(id: String, message: String): Project =
        store(apiCall(json) { api.requestRevision(id, RevisionRequestBody(message.trim())) })

    // ----- internal -----

    private companion object {
        /** 50 per page; a client with more than 500 projects sees the newest 500. */
        const val MAX_PAGES = 10
    }

    private suspend fun store(project: Project): Project {
        _projects.update { it + (project.id to project) }
        cache.write(CacheStore.project(project.id), Project.serializer(), project)
        val summary = ProjectSummary(
            id = project.id,
            ref = project.ref,
            title = project.title,
            status = project.status,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
            credits = project.quote?.credits,
            estimatedDeliveryDate = project.timeline?.estimatedDeliveryDate,
            unreadComments = project.unreadComments,
        )
        _summaries.update { list ->
            (listOf(summary) + list.filterNot { it.id == project.id }).sortedByDescending { it.updatedAt ?: "" }
        }
        cache.write(CacheStore.PROJECTS, ListSerializer(ProjectSummary.serializer()), _summaries.value)
        return project
    }

    private suspend fun storeDocument(id: String, doc: Document): Document {
        _documents.update { it + (id to doc) }
        cache.write(CacheStore.document(id), Document.serializer(), doc)
        return doc
    }
}
