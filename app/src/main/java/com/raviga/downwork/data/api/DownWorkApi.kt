package com.raviga.downwork.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The DownWork API (api-contract.md v0.3). Implemented by Retrofit against the
 * real backend and by [com.raviga.downwork.data.demo.DemoApi] when no backend
 * URL is configured. Non-2xx responses surface as [ApiException] via [apiCall].
 */
interface DownWorkApi {

    @GET("health")
    suspend fun health(): HealthResponse

    @GET("config")
    suspend fun config(): AppConfig

    // Identity
    @POST("devices/register")
    suspend fun registerDevice(@Body body: RegisterDeviceRequest): RegisterDeviceResponse

    @POST("devices/recover")
    suspend fun recoverDevice(@Body body: RecoverDeviceRequest): RegisterDeviceResponse

    @POST("me/recovery-key")
    suspend fun issueRecoveryKey(@Body body: Empty = Empty()): RecoveryKeyResponse

    @POST("me/link-codes")
    suspend fun createLinkCode(@Body body: Empty = Empty()): LinkCodeResponse

    @GET("me")
    suspend fun me(): Me

    @PUT("me/push-token")
    suspend fun putPushToken(@Body body: PushTokenRequest)

    @DELETE("me/push-token")
    suspend fun deletePushToken()

    // Consent
    @GET("me/consent")
    suspend fun consent(): ConsentState

    @PUT("me/consent")
    suspend fun putConsent(@Body body: ConsentRequest): ConsentState

    // Projects
    @GET("projects")
    suspend fun projects(@Query("before") before: String? = null, @Query("limit") limit: Int = 50): ProjectsResponse

    @POST("projects")
    suspend fun createProject(@Body body: CreateProjectRequest = CreateProjectRequest()): Project

    @GET("projects/{id}")
    suspend fun project(@Path("id") id: String): Project

    @PATCH("projects/{id}")
    suspend fun patchProject(@Path("id") id: String, @Body body: PatchProjectRequest): Project

    @DELETE("projects/{id}")
    suspend fun deleteProject(@Path("id") id: String)

    @POST("projects/{id}/cancel")
    suspend fun cancelProject(@Path("id") id: String, @Body body: Empty = Empty()): Project

    // Inputs
    @POST("projects/{id}/audio/upload-url")
    suspend fun audioUploadUrl(@Path("id") id: String, @Body body: UploadUrlRequest): UploadUrlResponse

    @POST("projects/{id}/files/upload-url")
    suspend fun fileUploadUrl(@Path("id") id: String, @Body body: FileUploadUrlRequest): FileUploadUrlResponse

    @POST("projects/{id}/files/{fileId}/extract")
    suspend fun extractFile(@Path("id") id: String, @Path("fileId") fileId: String): JobEnvelope

    @POST("projects/{id}/transcribe")
    suspend fun transcribe(@Path("id") id: String, @Body body: TranscribeRequest): JobEnvelope

    @POST("projects/{id}/inputs")
    suspend fun addInput(@Path("id") id: String, @Body body: AddInputRequest): Project

    @PATCH("projects/{id}/inputs/{inputId}")
    suspend fun patchInput(@Path("id") id: String, @Path("inputId") inputId: String, @Body body: PatchInputRequest): Project

    @DELETE("projects/{id}/inputs/{inputId}")
    suspend fun deleteInput(@Path("id") id: String, @Path("inputId") inputId: String): Project

    // Document
    @POST("projects/{id}/document/generate")
    suspend fun generateDocument(@Path("id") id: String, @Body body: InstructionRequest = InstructionRequest()): JobEnvelope

    @POST("projects/{id}/document/append")
    suspend fun appendDocument(@Path("id") id: String, @Body body: Empty = Empty()): JobEnvelope

    @POST("projects/{id}/document/sections/{sectionId}/regenerate")
    suspend fun regenerateSection(
        @Path("id") id: String,
        @Path("sectionId") sectionId: String,
        @Body body: InstructionRequest,
    ): JobEnvelope

    @GET("projects/{id}/document")
    suspend fun document(@Path("id") id: String): Document

    @PUT("projects/{id}/document")
    suspend fun saveDocument(@Path("id") id: String, @Body body: SaveDocumentRequest): Document

    @GET("projects/{id}/document/versions")
    suspend fun documentVersions(@Path("id") id: String): VersionsResponse

    @GET("projects/{id}/document/versions/{v}")
    suspend fun documentVersion(@Path("id") id: String, @Path("v") version: Int): Document

    @POST("projects/{id}/document/versions/{v}/restore")
    suspend fun restoreDocumentVersion(@Path("id") id: String, @Path("v") version: Int, @Body body: Empty = Empty()): Document

    // Quote, submit, review
    @POST("projects/{id}/quote")
    suspend fun quote(@Path("id") id: String, @Body body: Empty = Empty()): JobEnvelope

    @GET("projects/{id}/quote")
    suspend fun latestQuote(@Path("id") id: String): Quote

    @POST("projects/{id}/submit")
    suspend fun submit(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String, @Body body: SubmitRequest): Project

    @POST("projects/{id}/resubmit")
    suspend fun resubmit(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String, @Body body: ResubmitRequest): Project

    @GET("projects/{id}/comments")
    suspend fun comments(@Path("id") id: String): CommentsResponse

    @POST("projects/{id}/comments")
    suspend fun addComment(@Path("id") id: String, @Body body: NewCommentRequest): Comment

    // Delivery
    @POST("projects/{id}/accept")
    suspend fun accept(@Path("id") id: String, @Body body: Empty = Empty()): Project

    @POST("projects/{id}/revisions")
    suspend fun requestRevision(@Path("id") id: String, @Body body: RevisionRequestBody): Project

    // Delivery targets
    @GET("me/delivery-targets")
    suspend fun deliveryTargets(): DeliveryTargets

    @PUT("me/delivery-targets")
    suspend fun putDeliveryTargets(@Body body: DeliveryTargetsRequest): DeliveryTargets

    /** Same route with explicit nulls, used to clear a target (explicitNulls is off globally). */
    @PUT("me/delivery-targets")
    suspend fun putDeliveryTargetsRaw(@Body body: kotlinx.serialization.json.JsonObject): DeliveryTargets

    @GET("me/aws/connect")
    suspend fun awsConnect(): AwsConnectInfo

    @POST("me/aws/verify")
    suspend fun awsVerify(@Body body: Empty = Empty()): AwsTarget

    // Credits
    @GET("me/credits")
    suspend fun credits(@Query("before") before: String? = null, @Query("limit") limit: Int = 50): CreditsResponse

    @POST("me/credits/sync")
    suspend fun syncCredits(@Header("Idempotency-Key") idempotencyKey: String, @Body body: Empty = Empty()): SyncResponse

    // Privacy
    @POST("me/export")
    suspend fun exportData(@Body body: Empty = Empty()): JobEnvelope

    @HTTP(method = "DELETE", path = "me", hasBody = true)
    suspend fun deleteMe(@Body body: DeleteMeRequest = DeleteMeRequest()): DeleteMeResponse

    // Jobs
    @GET("jobs/{jobId}")
    suspend fun job(@Path("jobId") jobId: String): Job
}
