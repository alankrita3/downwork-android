package com.raviga.downwork.data.demo

import com.raviga.downwork.data.api.AcceptanceConfig
import com.raviga.downwork.data.api.AddInputRequest
import com.raviga.downwork.data.api.AiConfig
import com.raviga.downwork.data.api.AiConsent
import com.raviga.downwork.data.api.ApiErrorBody
import com.raviga.downwork.data.api.ApiException
import com.raviga.downwork.data.api.AppConfig
import com.raviga.downwork.data.api.AwsConnectInfo
import com.raviga.downwork.data.api.AwsDelivery
import com.raviga.downwork.data.api.AwsTarget
import com.raviga.downwork.data.api.Bracket
import com.raviga.downwork.data.api.BreakdownItem
import com.raviga.downwork.data.api.Comment
import com.raviga.downwork.data.api.CommentsResponse
import com.raviga.downwork.data.api.Complexity
import com.raviga.downwork.data.api.ConsentRequest
import com.raviga.downwork.data.api.ConsentState
import com.raviga.downwork.data.api.ConsentVersion
import com.raviga.downwork.data.api.CreateProjectRequest
import com.raviga.downwork.data.api.CreditPack
import com.raviga.downwork.data.api.CreditsBalance
import com.raviga.downwork.data.api.CreditsConfig
import com.raviga.downwork.data.api.CreditsResponse
import com.raviga.downwork.data.api.DeleteMeRequest
import com.raviga.downwork.data.api.DeleteMeResponse
import com.raviga.downwork.data.api.Delivery
import com.raviga.downwork.data.api.DeliveryConfig
import com.raviga.downwork.data.api.DeliveryTargets
import com.raviga.downwork.data.api.DeliveryTargetsRequest
import com.raviga.downwork.data.api.Document
import com.raviga.downwork.data.api.DownWorkApi
import com.raviga.downwork.data.api.Empty
import com.raviga.downwork.data.api.ExportResult
import com.raviga.downwork.data.api.Grievance
import com.raviga.downwork.data.api.HealthResponse
import com.raviga.downwork.data.api.HistoryEntry
import com.raviga.downwork.data.api.InstructionRequest
import com.raviga.downwork.data.api.Job
import com.raviga.downwork.data.api.JobEnvelope
import com.raviga.downwork.data.api.LedgerEntry
import com.raviga.downwork.data.api.LedgerRef
import com.raviga.downwork.data.api.LegalConfig
import com.raviga.downwork.data.api.LimitsConfig
import com.raviga.downwork.data.api.LinkCodeResponse
import com.raviga.downwork.data.api.Me
import com.raviga.downwork.data.api.Milestone
import com.raviga.downwork.data.api.NewCommentRequest
import com.raviga.downwork.data.api.PatchInputRequest
import com.raviga.downwork.data.api.PatchProjectRequest
import com.raviga.downwork.data.api.Project
import com.raviga.downwork.data.api.ProjectInput
import com.raviga.downwork.data.api.ProjectStatus
import com.raviga.downwork.data.api.ProjectSummary
import com.raviga.downwork.data.api.ProjectsResponse
import com.raviga.downwork.data.api.PushTokenRequest
import com.raviga.downwork.data.api.Quote
import com.raviga.downwork.data.api.QuoteConfig
import com.raviga.downwork.data.api.QuoteTimeline
import com.raviga.downwork.data.api.RecoverDeviceRequest
import com.raviga.downwork.data.api.RecoveryKeyResponse
import com.raviga.downwork.data.api.RegisterDeviceRequest
import com.raviga.downwork.data.api.RegisterDeviceResponse
import com.raviga.downwork.data.api.ResubmitRequest
import com.raviga.downwork.data.api.RetentionConfig
import com.raviga.downwork.data.api.Review
import com.raviga.downwork.data.api.RevisionRequest
import com.raviga.downwork.data.api.RevisionRequestBody
import com.raviga.downwork.data.api.Revisions
import com.raviga.downwork.data.api.RevisionsConfig
import com.raviga.downwork.data.api.SaveDocumentRequest
import com.raviga.downwork.data.api.SubmitRequest
import com.raviga.downwork.data.api.Submission
import com.raviga.downwork.data.api.SyncResponse
import com.raviga.downwork.data.api.Timeline
import com.raviga.downwork.data.api.TranscribeRequest
import com.raviga.downwork.data.api.TranscriptResult
import com.raviga.downwork.data.api.Transfer
import com.raviga.downwork.data.api.UploadUrlRequest
import com.raviga.downwork.data.api.UploadUrlResponse
import com.raviga.downwork.data.api.VersionSummary
import com.raviga.downwork.data.api.VersionsResponse
import com.raviga.downwork.data.local.CacheStore
import com.raviga.downwork.util.Time
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.ceil

/**
 * A complete in-process DownWork backend (api-contract v0.3) for the time
 * before the real one is deployed (API_BASE_URL empty). It implements the same
 * interface, keeps its state on disk, and walks submitted projects through the
 * review lifecycle on a timer so every screen can be exercised. Nothing here
 * talks to the network.
 */
class DemoApi(private val cache: CacheStore, private val json: Json) : DownWorkApi {

    @Serializable
    private data class State(
        val clientId: String,
        val accessToken: String,
        val recoveryKey: String,
        val createdAt: String,
        val consent: ConsentState = ConsentState(),
        val deliveryTargets: DeliveryTargets = DeliveryTargets(),
        val pushToken: String? = null,
        val projects: Map<String, Project> = emptyMap(),
        val documents: Map<String, List<Document>> = emptyMap(),
        val ledger: List<LedgerEntry> = emptyList(),
        val resubmitted: Set<String> = emptySet(),
        val statusChangedAt: Map<String, String> = emptyMap(),
        val unread: Map<String, Int> = emptyMap(),
    )

    private class DemoJob(
        val id: String,
        val type: String,
        val projectId: String?,
        val steps: List<String>,
        val stepMillis: Long,
        val produce: suspend () -> JsonElement,
    ) {
        val startedAt = System.currentTimeMillis()
        var result: JsonElement? = null
        var error: ApiErrorBody? = null
    }

    private val lock = Mutex()
    private var state: State? = null
    private val jobs = mutableMapOf<String, DemoJob>()

    val config: AppConfig = demoConfig()

    // ----- state plumbing -----

    private suspend fun load(): State {
        state?.let { return it }
        val loaded = cache.read(STATE, State.serializer()) ?: freshState()
        state = loaded
        return loaded
    }

    private suspend fun save(next: State) {
        state = next
        cache.write(STATE, State.serializer(), next)
    }

    private fun freshState() = State(
        clientId = "cl_demo" + UUID.randomUUID().toString().replace("-", "").take(10),
        accessToken = "dwt_demo_" + UUID.randomUUID(),
        recoveryKey = "dwrk_" + List(4) { UUID.randomUUID().toString().replace("-", "").take(4).uppercase() }.joinToString("-"),
        createdAt = Time.nowIso(),
    )

    private suspend fun <T> mutate(block: (State) -> Pair<State, T>): T = lock.withLock {
        val (next, value) = block(load())
        save(next)
        value
    }

    private suspend fun <T> readState(block: (State) -> T): T = lock.withLock { block(load()) }

    private fun balanceOf(s: State) = s.ledger.lastOrNull()?.balanceAfter ?: 0

    private fun meOf(s: State) = Me(
        clientId = s.clientId,
        createdAt = s.createdAt,
        credits = CreditsBalance(balanceOf(s)),
        consent = s.consent,
        deliveryTargets = s.deliveryTargets,
        deviceCount = 1,
    )

    private fun requireProject(s: State, id: String): Project =
        s.projects[id] ?: throw ApiException(ApiException.NOT_FOUND, 404, "No such project.")

    private fun put(s: State, project: Project, touch: Boolean = true): State {
        val p = if (touch) project.copy(updatedAt = Time.nowIso()) else project
        return s.copy(projects = s.projects + (p.id to p))
    }

    private fun transition(s: State, project: Project, status: String, by: String, note: String = ""): Pair<State, Project> {
        val now = Time.nowIso()
        val p = project.copy(status = status, history = project.history + HistoryEntry(status, now, by, note))
        val withMilestones = p.copy(timeline = p.timeline?.copy(milestones = milestones(status, p)))
        return put(s, withMilestones).copy(statusChangedAt = s.statusChangedAt + (p.id to now)) to withMilestones
    }

    private fun ledger(s: State, type: String, credits: Int, ref: LedgerRef? = null, note: String = ""): State {
        val entry = LedgerEntry(
            id = "le_" + UUID.randomUUID().toString().take(8),
            at = Time.nowIso(),
            type = type,
            credits = credits,
            balanceAfter = balanceOf(s) + credits,
            ref = ref,
            note = note,
        )
        return s.copy(ledger = s.ledger + entry)
    }

    private fun requireConsent(s: State) {
        val c = s.consent
        val missing = buildList {
            if (c.terms == null) add("terms")
            if (c.privacy == null) add("privacy")
            if (!c.aiGranted) add("aiProcessing")
        }
        if (missing.isNotEmpty()) throw ApiException(
            ApiException.CONSENT_REQUIRED, 403, "Consent is required first.",
            details = buildJsonObject { put("missing", kotlinx.serialization.json.JsonArray(missing.map { kotlinx.serialization.json.JsonPrimitive(it) })) },
        )
    }

    private fun requireEditable(p: Project) {
        if (!p.isEditable) throw ApiException(
            ApiException.DOCUMENT_LOCKED, 423, "This brief is locked because it has been submitted.",
            details = buildJsonObject { put("lockedVersion", p.lockedVersion ?: 0) },
        )
    }

    private fun currentDoc(s: State, id: String): Document? = s.documents[id]?.maxByOrNull { it.version }

    // ----- lifecycle simulation -----

    /** Moves a project along the review lifecycle if enough demo time has passed. */
    private fun advance(s: State, project: Project): Pair<State, Project> {
        val since = Time.parse(s.statusChangedAt[project.id] ?: project.updatedAt) ?: return s to project
        val elapsed = System.currentTimeMillis() - since.toEpochMilli()
        var next = s
        var p = project
        when (p.status) {
            ProjectStatus.SUBMITTED -> {
                val firstPass = p.id !in s.resubmitted
                if (firstPass && elapsed > 20_000) {
                    val comment = Comment(
                        id = "cm_" + UUID.randomUUID().toString().take(6),
                        createdAt = Time.nowIso(),
                        author = "team",
                        body = "Thanks, this is clear and we can build it. One question before we approve: should payments be in the first release, or can it launch without them and add payments in a revision? Edit the brief if anything changes and resubmit.",
                    )
                    p = p.copy(review = p.review.copy(comments = listOf(comment) + p.review.comments, decidedAt = Time.nowIso()), unreadComments = p.unreadComments + 1)
                    val (n, t) = transition(next, p, ProjectStatus.CHANGES_REQUESTED, "team", "Question about payments")
                    next = n; p = t
                } else if (!firstPass && elapsed > 15_000) {
                    val days = p.quote?.estimatedWorkingDays ?: 10
                    val eta = Time.addWorkingDays(LocalDate.now(), days)
                    p = p.copy(
                        review = p.review.copy(decidedAt = Time.nowIso()),
                        timeline = Timeline(estimatedWorkingDays = days, estimatedDeliveryDate = Time.isoDate(eta), milestones = emptyList()),
                    )
                    val (n, t) = transition(next, p, ProjectStatus.APPROVED, "team", "Approved")
                    next = n; p = t
                }
            }
            ProjectStatus.APPROVED -> if (elapsed > 40_000) {
                val slug = p.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "project" }
                val now = Time.nowIso()
                val hasGithub = p.submission?.githubUsername?.isNotBlank() == true || s.deliveryTargets.githubUsername.isNotBlank()
                val hasAws = p.submission?.awsAccountId != null || s.deliveryTargets.aws != null
                p = p.copy(
                    delivery = Delivery(
                        repoUrl = "https://github.com/downwork-builds/$slug",
                        deliveredAt = now,
                        note = "Repo transferred. " + if (hasAws) "Backend deployed to your AWS account in ap-south-1: one API Gateway, three Lambda functions, one DynamoDB table and one S3 bucket. The README lists every resource." else "Backend deployment instructions are in the README.",
                        transfer = Transfer(status = if (hasGithub) "initiated" else "awaiting_target", initiatedAt = if (hasGithub) now else null),
                        aws = AwsDelivery(status = if (hasAws) "deployed" else "not_requested", note = if (hasAws) "Deployed with DownWorkDeployRole" else ""),
                        acceptBy = Instant.now().plusSeconds(config.acceptance.autoAcceptDays * 86_400L).toString(),
                    ),
                )
                val (n, t) = transition(next, p, ProjectStatus.DELIVERED, "team", "Delivered")
                next = n; p = t
            }
            ProjectStatus.REVISION_REQUESTED -> if (elapsed > 25_000) {
                val last = p.revisions.requests.lastOrNull()?.message ?: "as requested"
                p = p.copy(
                    revisions = p.revisions.copy(requests = p.revisions.requests.map { r -> if (r.resolvedAt == null) r.copy(resolvedAt = Time.nowIso()) else r }),
                    delivery = p.delivery?.copy(deliveredAt = Time.nowIso(), note = "Revision delivered: $last. Pull the latest commit on main."),
                )
                val (n, t) = transition(next, p, ProjectStatus.DELIVERED, "team", "Revision delivered")
                next = n; p = t
            }
        }
        return next to p
    }

    private fun milestones(status: String, p: Project): List<Milestone> {
        val order = listOf("submitted" to "Submitted", "review" to "Reviewed", "approved" to "Approved", "build" to "Building", "delivered" to "Delivered", "accepted" to "Accepted")
        val reached = when (status) {
            ProjectStatus.SUBMITTED -> 2
            ProjectStatus.CHANGES_REQUESTED, ProjectStatus.REJECTED -> 2
            ProjectStatus.APPROVED -> 4
            ProjectStatus.DELIVERED, ProjectStatus.REVISION_REQUESTED -> 6
            ProjectStatus.ACCEPTED -> 7
            else -> 0
        }
        return order.mapIndexed { i, (id, label) ->
            val st = when {
                i + 1 < reached -> "done"
                i + 1 == reached -> "current"
                else -> "upcoming"
            }
            val at = when (id) {
                "submitted" -> p.submission?.submittedAt
                "review" -> p.review.decidedAt
                "approved" -> p.history.lastOrNull { it.status == ProjectStatus.APPROVED }?.at
                "build" -> p.history.lastOrNull { it.status == ProjectStatus.APPROVED }?.at
                "delivered" -> p.delivery?.deliveredAt
                "accepted" -> p.history.lastOrNull { it.status == ProjectStatus.ACCEPTED }?.at
                else -> null
            }
            Milestone(id = id, label = label, at = at.takeIf { st == "done" }, state = st)
        }
    }

    // ----- jobs -----

    private fun startJob(type: String, projectId: String?, steps: List<String>, stepMillis: Long, produce: suspend () -> JsonElement): JobEnvelope {
        val job = DemoJob("jb_" + UUID.randomUUID().toString().take(8), type, projectId, steps, stepMillis, produce)
        synchronized(jobs) { jobs[job.id] = job }
        return JobEnvelope(Job(jobId = job.id, type = type, status = "queued", projectId = projectId, progress = 0.0, progressMessage = steps.first(), createdAt = Time.nowIso()))
    }

    override suspend fun job(jobId: String): Job {
        val job = synchronized(jobs) { jobs[jobId] } ?: throw ApiException(ApiException.NOT_FOUND, 404, "No such job.")
        val elapsed = System.currentTimeMillis() - job.startedAt
        val total = job.steps.size * job.stepMillis
        if (elapsed < total) {
            val idx = (elapsed / job.stepMillis).toInt().coerceIn(0, job.steps.lastIndex)
            return Job(jobId, job.type, "running", job.projectId, progress = (elapsed.toDouble() / total).coerceIn(0.0, 0.95), progressMessage = job.steps[idx])
        }
        if (job.result == null && job.error == null) {
            try {
                job.result = job.produce()
            } catch (e: ApiException) {
                job.error = ApiErrorBody(code = e.code, message = e.message)
            }
        }
        return if (job.error != null) Job(jobId, job.type, "failed", job.projectId, progress = 1.0, error = job.error)
        else Job(jobId, job.type, "done", job.projectId, progress = 1.0, result = job.result)
    }

    // ----- health, config, identity -----

    override suspend fun health() = HealthResponse(ok = true, version = "demo", stage = "demo")

    override suspend fun config(): AppConfig = config

    override suspend fun registerDevice(body: RegisterDeviceRequest): RegisterDeviceResponse = readState {
        RegisterDeviceResponse(it.clientId, "dv_demo", it.accessToken, it.recoveryKey, isNewClient = it.projects.isEmpty() && it.ledger.isEmpty())
    }

    override suspend fun recoverDevice(body: RecoverDeviceRequest): RegisterDeviceResponse = readState {
        if (body.recoveryKey.trim().equals(it.recoveryKey, ignoreCase = true)) RegisterDeviceResponse(it.clientId, "dv_demo", it.accessToken, null, false)
        else throw ApiException(ApiException.OTP_INVALID, 400, "That recovery key does not match.")
    }

    override suspend fun issueRecoveryKey(body: Empty): RecoveryKeyResponse = mutate { s ->
        val fresh = freshState().recoveryKey
        s.copy(recoveryKey = fresh) to RecoveryKeyResponse(fresh)
    }

    override suspend fun createLinkCode(body: Empty): LinkCodeResponse =
        LinkCodeResponse(code = List(2) { UUID.randomUUID().toString().take(4).uppercase() }.joinToString("-"), expiresAt = Instant.now().plusSeconds(600).toString())

    override suspend fun me(): Me = readState { meOf(it) }

    override suspend fun putPushToken(body: PushTokenRequest) = mutate { it.copy(pushToken = body.token) to Unit }

    override suspend fun deletePushToken() = mutate { it.copy(pushToken = null) to Unit }

    override suspend fun consent(): ConsentState = readState { it.consent }

    override suspend fun putConsent(body: ConsentRequest): ConsentState = mutate { s ->
        val now = Time.nowIso()
        var c = s.consent
        body.termsVersion?.let {
            if (it != config.legal.termsVersion) throw ApiException(ApiException.INVALID_ARGUMENT, 400, "termsVersion is not current.")
            c = c.copy(terms = ConsentVersion(it, now))
        }
        body.privacyVersion?.let {
            if (it != config.legal.privacyVersion) throw ApiException(ApiException.INVALID_ARGUMENT, 400, "privacyVersion is not current.")
            c = c.copy(privacy = ConsentVersion(it, now))
        }
        body.aiProcessing?.let { c = c.copy(aiProcessing = AiConsent(it, now)) }
        c = c.copy(current = c.terms?.version == config.legal.termsVersion && c.privacy?.version == config.legal.privacyVersion)
        s.copy(consent = c) to c
    }

    // ----- projects -----

    override suspend fun projects(before: String?, limit: Int): ProjectsResponse = mutate { s ->
        var next = s
        val advanced = s.projects.values.map { p -> val (n, a) = advance(next, p); next = n; a }
        next to ProjectsResponse(
            advanced.sortedByDescending { it.updatedAt ?: "" }.map {
                ProjectSummary(it.id, it.ref, it.title, it.status, it.createdAt, it.updatedAt, it.quote?.credits, it.timeline?.estimatedDeliveryDate, it.unreadComments)
            },
        )
    }

    override suspend fun createProject(body: CreateProjectRequest): Project = mutate { s ->
        requireConsent(s)
        val now = Time.nowIso()
        val p = Project(
            id = "pr_" + UUID.randomUUID().toString().replace("-", "").take(10),
            ref = "DW-" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase(),
            title = body.title.trim(),
            status = ProjectStatus.DRAFT,
            createdAt = now,
            updatedAt = now,
            revisions = Revisions(used = 0, included = config.revisions.includedRounds),
            history = listOf(HistoryEntry(ProjectStatus.DRAFT, now, "system")),
        )
        put(s, p, touch = false) to p
    }

    override suspend fun project(id: String): Project = mutate { s ->
        val (next, p) = advance(s, requireProject(s, id))
        next to p
    }

    override suspend fun patchProject(id: String, body: PatchProjectRequest): Project = mutate { s ->
        val p = requireProject(s, id)
        if (ProjectStatus.isTerminal(p.status)) throw ApiException(ApiException.INVALID_STATE, 409, "This project is closed.")
        val next = p.copy(title = body.title.trim())
        put(s, next) to next
    }

    override suspend fun deleteProject(id: String) = mutate { s ->
        val p = requireProject(s, id)
        if (p.status != ProjectStatus.DRAFT) throw ApiException(ApiException.INVALID_STATE, 409, "Only drafts can be deleted.")
        s.copy(projects = s.projects - id, documents = s.documents - id) to Unit
    }

    override suspend fun cancelProject(id: String, body: Empty): Project = mutate { s ->
        val p = requireProject(s, id)
        if (!ProjectStatus.canCancel(p.status)) throw ApiException(ApiException.INVALID_STATE, 409, "This project can no longer be cancelled.")
        var next = s
        val refund = p.submission?.creditsCharged ?: 0
        if (refund > 0) next = ledger(next, "refund_project", refund, LedgerRef(projectId = id), "Cancelled ${p.title}")
        transition(next, p, ProjectStatus.CANCELLED, "client")
    }

    // ----- inputs -----

    override suspend fun audioUploadUrl(id: String, body: UploadUrlRequest): UploadUrlResponse = readState { s ->
        requireConsent(s)
        requireEditable(requireProject(s, id))
        UploadUrlResponse(audioId = "au_" + UUID.randomUUID().toString().take(8), uploadUrl = DEMO_UPLOAD_URL, headers = mapOf("Content-Type" to body.contentType))
    }

    override suspend fun transcribe(id: String, body: TranscribeRequest): JobEnvelope =
        startJob("transcribe", id, listOf("Listening back"), 1_500) {
            json.encodeToJsonElement(
                TranscriptResult.serializer(),
                TranscriptResult(transcript = "(Demo mode cannot transcribe recordings. Use live dictation or type your description, or point the app at the real backend.)", languageDetected = "en", durationSec = 0),
            )
        }

    override suspend fun addInput(id: String, body: AddInputRequest): Project = mutate { s ->
        requireConsent(s)
        val p = requireProject(s, id)
        requireEditable(p)
        if (p.inputs.size >= config.limits.inputsPerProject) throw ApiException(ApiException.INVALID_ARGUMENT, 400, "A project can have ${config.limits.inputsPerProject} inputs.")
        val isText = body.kind == "text"
        val input = ProjectInput(
            id = "in_" + UUID.randomUUID().toString().take(8),
            kind = body.kind,
            text = body.text.trim(),
            audioId = if (isText) null else body.audioId,
            durationSec = if (isText) null else body.durationSec,
            languageDetected = if (isText) null else body.languageDetected,
            audioExpiresAt = if (isText || body.audioId == null) null else Instant.now().plusSeconds(config.retention.audioDays * 86_400L).toString(),
            createdAt = Time.nowIso(),
        )
        val next = p.copy(inputs = p.inputs + input)
        put(s, next) to next
    }

    override suspend fun patchInput(id: String, inputId: String, body: PatchInputRequest): Project = mutate { s ->
        val p = requireProject(s, id)
        requireEditable(p)
        val next = p.copy(inputs = p.inputs.map { if (it.id == inputId) it.copy(text = body.text.trim()) else it })
        put(s, next) to next
    }

    override suspend fun deleteInput(id: String, inputId: String): Project = mutate { s ->
        val p = requireProject(s, id)
        requireEditable(p)
        val next = p.copy(inputs = p.inputs.filterNot { it.id == inputId })
        put(s, next) to next
    }

    // ----- document -----

    private fun newVersion(s: State, p: Project, doc: Document): Pair<State, Project> {
        val version = (s.documents[p.id]?.maxOfOrNull { it.version } ?: 0) + 1
        val stamped = doc.copy(version = version, createdAt = Time.nowIso())
        val next = s.copy(documents = s.documents + (p.id to (s.documents[p.id].orEmpty() + stamped)))
        val project = p.copy(
            document = stamped.summary().copy(locked = p.lockedVersion != null && p.status != ProjectStatus.CHANGES_REQUESTED),
            title = stamped.title.ifBlank { p.title },
            quote = p.quote?.let { q -> if (q.documentVersion != version) q.copy(status = "stale") else q },
        )
        return put(next, project) to project
    }

    private fun docJson(project: Project, s: State): JsonElement =
        json.encodeToJsonElement(Document.serializer(), currentDoc(s, project.id)!!)

    override suspend fun generateDocument(id: String, body: InstructionRequest): JobEnvelope =
        startJob("generate", id, listOf("Listening back", "Finding the features", "Writing the brief"), 1_100) {
            mutate { s ->
                requireConsent(s)
                val p = requireProject(s, id)
                requireEditable(p)
                if (p.inputs.isEmpty()) throw ApiException(ApiException.INVALID_STATE, 409, "Describe the project first.")
                val draft = BriefWriter.write(p.inputs.map { it.text })
                val doc = Document(
                    version = 0,
                    title = p.title.ifBlank { draft.title },
                    sections = draft.sections,
                    source = "ai",
                    changeSummary = "Written from your description",
                    generatedFrom = com.raviga.downwork.data.api.GeneratedFrom(p.inputs.map { it.id }),
                )
                val (next, project) = newVersion(s, p, doc)
                next to docJson(project, next)
            }
        }

    override suspend fun appendDocument(id: String, body: Empty): JobEnvelope =
        startJob("append", id, listOf("Listening back", "Adding to the brief"), 1_100) {
            mutate { s ->
                requireConsent(s)
                val p = requireProject(s, id)
                requireEditable(p)
                val current = currentDoc(s, id) ?: throw ApiException(ApiException.INVALID_STATE, 409, "Write the brief first.")
                val merged = BriefWriter.append(current, p.inputs.map { it.text })
                val doc = current.copy(sections = merged, source = "append", changeSummary = "Added from your new description", generatedFrom = com.raviga.downwork.data.api.GeneratedFrom(p.inputs.map { it.id }))
                val (next, project) = newVersion(s, p, doc)
                next to docJson(project, next)
            }
        }

    override suspend fun regenerateSection(id: String, sectionId: String, body: InstructionRequest): JobEnvelope =
        startJob("regenerate", id, listOf("Rereading the section", "Rewriting"), 900) {
            mutate { s ->
                requireConsent(s)
                val p = requireProject(s, id)
                requireEditable(p)
                val current = currentDoc(s, id) ?: throw ApiException(ApiException.INVALID_STATE, 409, "Write the brief first.")
                val section = current.section(sectionId) ?: throw ApiException(ApiException.NOT_FOUND, 404, "No such section.")
                val rewritten = BriefWriter.regenerate(section, p.inputs.map { it.text }, body.instruction)
                val doc = current.copy(
                    sections = current.sections.map { if (it.id == sectionId) rewritten else it },
                    source = "regenerate",
                    changeSummary = "Regenerated ${section.heading}",
                )
                val (next, project) = newVersion(s, p, doc)
                next to docJson(project, next)
            }
        }

    override suspend fun document(id: String): Document = readState { s ->
        requireProject(s, id)
        currentDoc(s, id) ?: throw ApiException(ApiException.NOT_FOUND, 404, "No document yet.")
    }

    override suspend fun saveDocument(id: String, body: SaveDocumentRequest): Document = mutate { s ->
        val p = requireProject(s, id)
        requireEditable(p)
        val current = currentDoc(s, id) ?: throw ApiException(ApiException.INVALID_STATE, 409, "Write the brief first.")
        if (body.baseVersion != current.version) throw ApiException(
            ApiException.VERSION_CONFLICT, 409, "The brief changed elsewhere. Reload and try again.",
            details = buildJsonObject { put("currentVersion", current.version) },
        )
        val incoming = body.sections.associate { it.id to it.body }
        val changed = current.sections.filter { incoming[it.id] != null && incoming[it.id] != it.body }.map { it.heading }
        val titleChanged = body.title != null && body.title.trim() != current.title
        val summary = "You edited " + (changed + if (titleChanged) listOf("the title") else emptyList()).ifEmpty { listOf("the brief") }.joinToString(", ")
        val doc = current.copy(
            title = body.title?.trim()?.ifBlank { null } ?: current.title,
            sections = current.sections.map { sec -> incoming[sec.id]?.let { sec.copy(body = it.trim()) } ?: sec },
            source = "edit",
            changeSummary = summary,
        )
        val (next, _) = newVersion(s, p, doc)
        next to currentDoc(next, id)!!
    }

    override suspend fun documentVersions(id: String): VersionsResponse = readState { s ->
        requireProject(s, id)
        VersionsResponse(s.documents[id].orEmpty().sortedByDescending { it.version }.map { VersionSummary(it.version, it.createdAt, it.source, it.changeSummary) })
    }

    override suspend fun documentVersion(id: String, version: Int): Document = readState { s ->
        requireProject(s, id)
        s.documents[id]?.firstOrNull { it.version == version } ?: throw ApiException(ApiException.NOT_FOUND, 404, "No such version.")
    }

    override suspend fun restoreDocumentVersion(id: String, version: Int, body: Empty): Document = mutate { s ->
        val p = requireProject(s, id)
        requireEditable(p)
        val old = s.documents[id]?.firstOrNull { it.version == version } ?: throw ApiException(ApiException.NOT_FOUND, 404, "No such version.")
        val doc = old.copy(source = "restore", changeSummary = "Restored version $version")
        val (next, _) = newVersion(s, p, doc)
        next to currentDoc(next, id)!!
    }

    // ----- quote, submit, review -----

    override suspend fun quote(id: String, body: Empty): JobEnvelope =
        startJob("quote", id, listOf("Sizing the work", "Checking the timeline"), 900) {
            mutate { s ->
                requireConsent(s)
                val p = requireProject(s, id)
                val doc = currentDoc(s, id) ?: throw ApiException(ApiException.INVALID_STATE, 409, "Write the brief first.")
                val parts = BriefWriter.quote(doc, config)
                val maxWeeks = parts.workingDays / 5
                val quote = Quote(
                    id = "qt_" + UUID.randomUUID().toString().take(8),
                    documentVersion = doc.version,
                    status = "current",
                    credits = parts.credits,
                    inr = parts.credits * config.credits.creditValueInr,
                    bracketId = parts.bracketId,
                    estimatedWorkingDays = parts.workingDays,
                    timeline = QuoteTimeline(minWeeks = ceil(maxWeeks * 0.6).toInt().coerceAtLeast(1), maxWeeks = maxWeeks),
                    complexity = Complexity(parts.score, parts.drivers),
                    breakdown = parts.breakdown.map { BreakdownItem(it.first, it.second) },
                    assumptions = parts.assumptions,
                    createdAt = Time.nowIso(),
                    expiresAt = Instant.now().plusSeconds(config.quote.validDays * 86_400L).toString(),
                )
                val project = p.copy(quote = quote)
                put(s, project) to json.encodeToJsonElement(Quote.serializer(), quote)
            }
        }

    override suspend fun latestQuote(id: String): Quote = readState { s ->
        requireProject(s, id).quote ?: throw ApiException(ApiException.NOT_FOUND, 404, "No quote yet.")
    }

    private fun requireQuote(p: Project, quoteId: String): Quote {
        val quote = p.quote
        if (quote == null || quote.id != quoteId || p.quoteIsStale) throw ApiException(ApiException.QUOTE_STALE, 409, "The brief changed since this quote. Get a new quote.")
        return quote
    }

    override suspend fun submit(id: String, idempotencyKey: String, body: SubmitRequest): Project = mutate { s ->
        val p = requireProject(s, id)
        if (p.status != ProjectStatus.DRAFT) throw ApiException(ApiException.INVALID_STATE, 409, "This project has already been submitted.", details = buildJsonObject { put("status", p.status) })
        requireConsent(s)
        var next = s
        if (body.githubUsername != null || body.awsAccountId != null) {
            next = applyTargets(next, DeliveryTargetsRequest(body.githubUsername, body.awsAccountId)).first
        }
        val quote = requireQuote(p, body.quoteId)
        val balance = balanceOf(next)
        if (balance < quote.credits) throw ApiException(
            ApiException.INSUFFICIENT_CREDITS, 402, "You need ${quote.credits - balance} more credits.",
            details = buildJsonObject { put("required", quote.credits); put("balance", balance) },
        )
        next = ledger(next, "charge", -quote.credits, LedgerRef(projectId = id), p.title)
        val now = Time.nowIso()
        val project = p.copy(
            lockedVersion = p.document?.version,
            document = p.document?.copy(locked = true),
            quote = quote.copy(status = "used"),
            submission = Submission(submittedAt = now, githubUsername = next.deliveryTargets.githubUsername.ifBlank { null }, awsAccountId = next.deliveryTargets.aws?.accountId, creditsCharged = quote.credits),
            timeline = Timeline(estimatedWorkingDays = quote.estimatedWorkingDays, estimatedDeliveryDate = null, milestones = emptyList()),
        )
        transition(next, project, ProjectStatus.SUBMITTED, "client")
    }

    override suspend fun resubmit(id: String, idempotencyKey: String, body: ResubmitRequest): Project = mutate { s ->
        val p = requireProject(s, id)
        if (p.status != ProjectStatus.CHANGES_REQUESTED) throw ApiException(ApiException.INVALID_STATE, 409, "Only projects with comments can be resubmitted.", details = buildJsonObject { put("status", p.status) })
        requireConsent(s)
        val quote = requireQuote(p, body.quoteId)
        val charged = p.submission?.creditsCharged ?: 0
        val diff = quote.credits - charged
        var next = s
        if (diff > 0) {
            val balance = balanceOf(s)
            if (balance < diff) throw ApiException(ApiException.INSUFFICIENT_CREDITS, 402, "You need ${diff - balance} more credits.", details = buildJsonObject { put("required", diff); put("balance", balance) })
            next = ledger(next, "charge", -diff, LedgerRef(projectId = id), "Resubmitted ${p.title}")
        } else if (diff < 0) {
            next = ledger(next, "refund_project", -diff, LedgerRef(projectId = id), "Resubmitted ${p.title}")
        }
        val now = Time.nowIso()
        val project = p.copy(
            lockedVersion = p.document?.version,
            document = p.document?.copy(locked = true),
            quote = quote.copy(status = "used"),
            submission = p.submission?.copy(submittedAt = now, creditsCharged = quote.credits),
            review = p.review.copy(decidedAt = null),
            timeline = Timeline(estimatedWorkingDays = quote.estimatedWorkingDays, estimatedDeliveryDate = null, milestones = emptyList()),
        )
        val (n, t) = transition(next, project, ProjectStatus.SUBMITTED, "client", "Resubmitted")
        n.copy(resubmitted = n.resubmitted + id) to t
    }

    override suspend fun comments(id: String): CommentsResponse = mutate { s ->
        val p = requireProject(s, id)
        val cleared = p.copy(unreadComments = 0)
        put(s, cleared, touch = false) to CommentsResponse(p.review.comments)
    }

    override suspend fun addComment(id: String, body: NewCommentRequest): Comment = mutate { s ->
        val p = requireProject(s, id)
        if (!ProjectStatus.canComment(p.status)) throw ApiException(ApiException.INVALID_STATE, 409, "Comments open after submission.")
        val comment = Comment(id = "cm_" + UUID.randomUUID().toString().take(6), createdAt = Time.nowIso(), author = "client", body = body.body.trim(), sectionId = body.sectionId)
        put(s, p.copy(review = p.review.copy(comments = listOf(comment) + p.review.comments))) to comment
    }

    // ----- delivery -----

    override suspend fun accept(id: String, body: Empty): Project = mutate { s ->
        val p = requireProject(s, id)
        if (p.status != ProjectStatus.DELIVERED) throw ApiException(ApiException.INVALID_STATE, 409, "Nothing to accept yet.")
        val project = p.copy(delivery = p.delivery?.copy(transfer = p.delivery.transfer?.copy(status = "accepted")))
        transition(s, project, ProjectStatus.ACCEPTED, "client")
    }

    override suspend fun requestRevision(id: String, body: RevisionRequestBody): Project = mutate { s ->
        val p = requireProject(s, id)
        if (p.status != ProjectStatus.DELIVERED) throw ApiException(ApiException.INVALID_STATE, 409, "Revisions can be requested after delivery.")
        if (p.revisions.used >= p.revisions.included) throw ApiException(
            ApiException.REVISION_LIMIT_REACHED, 409, "Your included revision rounds are used up.",
            details = buildJsonObject { put("used", p.revisions.used); put("included", p.revisions.included) },
        )
        val now = Time.nowIso()
        val request = RevisionRequest(id = "rv_" + UUID.randomUUID().toString().take(6), createdAt = now, message = body.message.trim())
        val comment = Comment(id = "cm_" + UUID.randomUUID().toString().take(6), createdAt = now, author = "client", body = body.message.trim())
        val project = p.copy(
            revisions = p.revisions.copy(used = p.revisions.used + 1, requests = p.revisions.requests + request),
            review = p.review.copy(comments = listOf(comment) + p.review.comments),
        )
        transition(s, project, ProjectStatus.REVISION_REQUESTED, "client")
    }

    // ----- delivery targets -----

    private fun applyTargets(s: State, body: DeliveryTargetsRequest): Pair<State, DeliveryTargets> {
        var targets = s.deliveryTargets
        body.githubUsername?.let { name ->
            val trimmed = name.trim()
            if (trimmed.isNotBlank() && !GITHUB.matches(trimmed)) throw ApiException(ApiException.GITHUB_USER_NOT_FOUND, 400, "No GitHub user with that name.")
            targets = targets.copy(githubUsername = trimmed)
        }
        body.awsAccountId?.let { acct ->
            val digits = acct.filter { it.isDigit() }
            if (acct.isBlank()) targets = targets.copy(aws = null)
            else if (digits.length != 12) throw ApiException(ApiException.INVALID_ARGUMENT, 400, "awsAccountId must be 12 digits.")
            else if (targets.aws?.accountId != digits) targets = targets.copy(
                aws = AwsTarget(
                    accountId = digits,
                    externalId = targets.aws?.externalId ?: ("dw-ext-" + UUID.randomUUID().toString().take(12)),
                    roleName = config.delivery.awsRoleName,
                    roleArn = "arn:aws:iam::$digits:role/${config.delivery.awsRoleName}",
                    verified = false,
                ),
            )
        }
        return s.copy(deliveryTargets = targets) to targets
    }

    override suspend fun deliveryTargets(): DeliveryTargets = readState { it.deliveryTargets }

    override suspend fun putDeliveryTargets(body: DeliveryTargetsRequest): DeliveryTargets = mutate { s -> applyTargets(s, body) }

    override suspend fun putDeliveryTargetsRaw(body: kotlinx.serialization.json.JsonObject): DeliveryTargets = mutate { s ->
        var targets = s.deliveryTargets
        if (body.containsKey("githubUsername")) {
            val v = body["githubUsername"]
            targets = if (v == null || v is kotlinx.serialization.json.JsonNull) targets.copy(githubUsername = "")
            else applyTargets(s.copy(deliveryTargets = targets), DeliveryTargetsRequest(githubUsername = (v as kotlinx.serialization.json.JsonPrimitive).content)).second
        }
        if (body.containsKey("awsAccountId")) {
            val v = body["awsAccountId"]
            targets = if (v == null || v is kotlinx.serialization.json.JsonNull) targets.copy(aws = null)
            else applyTargets(s.copy(deliveryTargets = targets), DeliveryTargetsRequest(awsAccountId = (v as kotlinx.serialization.json.JsonPrimitive).content)).second
        }
        s.copy(deliveryTargets = targets) to targets
    }

    override suspend fun awsConnect(): AwsConnectInfo = readState { s ->
        val aws = s.deliveryTargets.aws ?: throw ApiException(ApiException.NOT_FOUND, 404, "Enter your AWS account ID first.")
        val external = aws.externalId ?: "dw-ext"
        val region = config.delivery.awsRegion
        val template = "https://downwork-templates.s3.$region.amazonaws.com/downwork-deploy-role.yaml"
        AwsConnectInfo(
            accountId = aws.accountId,
            externalId = external,
            roleName = config.delivery.awsRoleName,
            trustedAccountId = config.delivery.awsTrustedAccountId,
            quickCreateUrl = "https://$region.console.aws.amazon.com/cloudformation/home?region=$region#/stacks/quickcreate" +
                "?templateURL=$template&stackName=${config.delivery.awsRoleName}&param_TrustedAccountId=${config.delivery.awsTrustedAccountId}&param_ExternalId=$external",
            templateUrl = template,
            instructions = listOf(
                "Sign in to the AWS account you want the project deployed to.",
                "Review the stack. It creates one IAM role named ${config.delivery.awsRoleName}.",
                "Tick the acknowledgement that the template creates IAM resources, then choose Create stack.",
                "Come back here and check the connection.",
            ),
        )
    }

    override suspend fun awsVerify(body: Empty): AwsTarget = mutate { s ->
        val aws = s.deliveryTargets.aws ?: throw ApiException(ApiException.NOT_FOUND, 404, "Enter your AWS account ID first.")
        val verified = aws.copy(verified = true, verifiedAt = Time.nowIso(), lastError = null)
        s.copy(deliveryTargets = s.deliveryTargets.copy(aws = verified)) to verified
    }

    // ----- credits -----

    override suspend fun credits(before: String?, limit: Int): CreditsResponse = readState { s ->
        CreditsResponse(balance = balanceOf(s), ledger = s.ledger.asReversed().take(limit))
    }

    override suspend fun syncCredits(idempotencyKey: String, body: Empty): SyncResponse = readState { SyncResponse(balanceOf(it), reconciled = true) }

    /** Demo-only: grants a pack as if the store purchase had cleared. */
    suspend fun demoPurchase(productId: String) = mutate { s ->
        val pack = config.credits.packs.firstOrNull { it.productId == productId } ?: throw ApiException(ApiException.NOT_FOUND, 404, "No such pack.")
        val tx = "DEMO." + UUID.randomUUID().toString().take(8)
        var next = ledger(s, "purchase", pack.credits, LedgerRef(productId = productId, storeTransactionId = tx), "Demo purchase")
        if (pack.bonusCredits > 0) next = ledger(next, "bonus", pack.bonusCredits, LedgerRef(productId = productId, storeTransactionId = tx), "Pack bonus")
        next to Unit
    }

    // ----- privacy -----

    override suspend fun exportData(body: Empty): JobEnvelope =
        startJob("export", null, listOf("Collecting your projects", "Packaging"), 900) {
            json.encodeToJsonElement(ExportResult.serializer(), ExportResult(downloadUrl = "https://downwork.in/exports/demo-export.zip", expiresAt = Instant.now().plusSeconds(config.retention.exportLinkHours * 3600L).toString(), sizeBytes = 48_213))
        }

    override suspend fun deleteMe(body: DeleteMeRequest): DeleteMeResponse = lock.withLock {
        val s = load()
        val active = s.projects.values.filter { it.status != ProjectStatus.DRAFT && !ProjectStatus.isTerminal(it.status) }.map { it.id }
        if (active.isNotEmpty()) throw ApiException(
            ApiException.ACTIVE_PROJECTS, 409, "Finish or cancel your active projects first.",
            details = buildJsonObject { put("projectIds", kotlinx.serialization.json.JsonArray(active.map { kotlinx.serialization.json.JsonPrimitive(it) })) },
        )
        save(freshState())
        synchronized(jobs) { jobs.clear() }
        DeleteMeResponse(requestedAt = Time.nowIso(), purgeAt = Instant.now().plusSeconds(config.retention.deletionGraceDays * 86_400L).toString())
    }

    companion object {
        private const val STATE = "demo_state"
        const val DEMO_UPLOAD_URL = "https://demo.invalid/upload"
        private val GITHUB = Regex("^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}$")

        fun demoConfig() = AppConfig(
            minAppVersion = mapOf("ios" to "1.0.0", "android" to "1.0.0"),
            credits = CreditsConfig(
                creditValueInr = 1000,
                packs = listOf(
                    CreditPack("credits_10", 10, 0, 9_999, "10 credits"),
                    CreditPack("credits_25", 25, 2, 24_999, "25 + 2 credits"),
                    CreditPack("credits_50", 50, 6, 49_999, "50 + 6 credits"),
                    CreditPack("credits_100", 100, 15, 99_999, "100 + 15 credits"),
                ),
            ),
            quote = QuoteConfig(
                brackets = listOf(
                    Bracket("micro", "Micro", 15, 30, "One job, one screen or a simple site"),
                    Bracket("starter", "Starter", 31, 60, "A focused app with a handful of screens"),
                    Bracket("standard", "Standard", 61, 150, "A full product with accounts, payments or integrations"),
                    Bracket("pro", "Pro", 151, 400, "Multiple platforms, complex data, admin tooling"),
                    Bracket("enterprise", "Enterprise", 401, null, "Quoted as a custom engagement"),
                ),
                validDays = 14,
                timelineNote = "Most projects are delivered well ahead of this estimate.",
            ),
            revisions = RevisionsConfig(2),
            acceptance = AcceptanceConfig(14),
            retention = RetentionConfig(30, 7, 24, 24),
            limits = LimitsConfig(),
            ai = AiConfig(listOf("OpenAI (speech to text and document drafting)")),
            delivery = DeliveryConfig("521901166785", "DownWorkDeployRole", "ap-south-1"),
            legal = LegalConfig(
                companyName = "Raviga Apps Private Limited",
                companyAddress = "Address to be confirmed",
                termsUrl = "https://downwork.in/terms",
                termsVersion = "2026-10-01",
                privacyUrl = "https://downwork.in/privacy",
                privacyVersion = "2026-10-01",
                grievance = Grievance("Grievance Officer", "grievance@downwork.in", "Address to be confirmed", 30),
                supportEmail = "support@downwork.in",
            ),
        )
    }
}
