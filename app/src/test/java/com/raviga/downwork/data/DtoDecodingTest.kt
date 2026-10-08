package com.raviga.downwork.data

import com.raviga.downwork.data.api.AppConfig
import com.raviga.downwork.data.api.Document
import com.raviga.downwork.data.api.Job
import com.raviga.downwork.data.api.JobEnvelope
import com.raviga.downwork.data.api.Me
import com.raviga.downwork.data.api.Project
import com.raviga.downwork.data.api.ProjectsResponse
import com.raviga.downwork.data.api.Quote
import com.raviga.downwork.data.api.RegisterDeviceResponse
import com.raviga.downwork.data.api.VersionsResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Decodes real backend responses (captured by the iOS session against dev) and
 * hand-written contract examples with the app's Json configuration.
 */
class DtoDecodingTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; isLenient = true }

    private fun fixture(name: String): String? {
        val url = javaClass.classLoader?.getResource("fixtures/$name") ?: return null
        return File(url.toURI()).readText()
    }

    private fun fixtures(): List<File> {
        val url = javaClass.classLoader?.getResource("fixtures") ?: return emptyList()
        return File(url.toURI()).listFiles { f -> f.extension == "json" }?.toList().orEmpty()
    }

    @Test
    fun `contract example project decodes with document summary`() {
        val raw = """
            {"id":"pr_1","ref":"DW-A1B2C3","title":"Tiffin delivery app","status":"draft","createdAt":"2026-10-09T04:30:00Z","updatedAt":"2026-10-09T04:31:00Z",
             "inputs":[{"id":"in_1","kind":"text","text":"hello","audioId":null,"durationSec":null,"languageDetected":null,"audioExpiresAt":null,"createdAt":"2026-10-09T04:30:00Z"}],
             "document":{"version":3,"title":"Tiffin delivery app","updatedAt":"2026-10-09T04:31:00Z","locked":false},
             "quote":null,"lockedVersion":null,"submission":null,"review":{"comments":[],"decidedAt":null},"timeline":null,"delivery":null,
             "revisions":{"used":0,"included":2,"requests":[]},"unreadComments":0,
             "history":[{"status":"draft","at":"2026-10-09T04:30:00Z","by":"system","note":""}]}
        """.trimIndent()
        val p = json.decodeFromString(Project.serializer(), raw)
        assertEquals("DW-A1B2C3", p.ref)
        assertEquals(3, p.document?.version)
        assertTrue(p.isEditable)
        assertTrue(p.quoteIsStale)
        assertEquals("system", p.history.first().by)
    }

    @Test
    fun `job envelope and bare job decode`() {
        val envelope = json.decodeFromString(JobEnvelope.serializer(), """{"job":{"jobId":"jb_1","type":"generate","status":"queued","projectId":"pr_1","progress":0,"progressMessage":null}}""")
        assertEquals("jb_1", envelope.job.jobId)
        val done = json.decodeFromString(Job.serializer(), """{"jobId":"jb_1","type":"generate","status":"done","progress":1,"result":{"version":1,"title":"x","sections":[]}}""")
        assertTrue(done.isDone)
        assertEquals(1, done.result?.jsonObject?.get("version")?.toString()?.toInt())
    }

    @Test
    fun `quote with breakdown and timeline decodes`() {
        val raw = """{"id":"qt_1","documentVersion":3,"status":"current","credits":42,"inr":42000,"bracketId":"starter","estimatedWorkingDays":30,
            "timeline":{"minWeeks":4,"maxWeeks":6},"complexity":{"score":5,"drivers":["Payments","Two platforms"]},
            "breakdown":[{"area":"Mobile apps","credits":24},{"area":"Backend","credits":14}],"assumptions":["Single language UI"],"createdAt":"2026-10-09T04:30:00Z","expiresAt":"2026-10-23T04:30:00Z"}"""
        val q = json.decodeFromString(Quote.serializer(), raw)
        assertEquals(42, q.credits)
        assertEquals(6, q.timeline?.maxWeeks)
        assertEquals(2, q.breakdown.size)
    }

    @Test
    fun `register response without recovery key decodes`() {
        val r = json.decodeFromString(RegisterDeviceResponse.serializer(), """{"clientId":"cl_1","deviceId":"dv_1","accessToken":"dwt_x","recoveryKey":null,"isNewClient":false}""")
        assertEquals(null, r.recoveryKey)
        assertEquals(false, r.isNewClient)
    }

    @Test
    fun `me with nulls decodes`() {
        val me = json.decodeFromString(Me.serializer(), """{"clientId":"cl_1","createdAt":"2026-10-09T04:30:00Z","credits":{"balance":25},
            "consent":{"terms":null,"privacy":null,"aiProcessing":{"granted":false,"updatedAt":null},"current":false},
            "recoveryEmail":null,"deliveryTargets":{"githubUsername":"","aws":null},"deviceCount":2,"deletion":null}""")
        assertEquals(25, me.credits.balance)
        assertEquals(false, me.consent.aiGranted)
        assertEquals("", me.deliveryTargets.githubUsername)
    }

    @Test
    fun `captured dev fixtures decode by name`() {
        // Any fixture copied from the iOS session decodes with the matching DTO, chosen by filename.
        val files = fixtures()
        var checked = 0
        files.forEach { f ->
            val text = f.readText()
            val name = f.nameWithoutExtension.lowercase()
            runCatching {
                when {
                    name.startsWith("job") && text.trimStart().startsWith("{\"job\"") -> json.decodeFromString(JobEnvelope.serializer(), text).also { assertTrue(it.job.jobId.isNotBlank()) }
                    name.startsWith("job") -> json.decodeFromString(Job.serializer(), text).also { assertTrue(it.jobId.isNotBlank()) }
                    name.startsWith("error") -> json.decodeFromString(com.raviga.downwork.data.api.ApiErrorEnvelope.serializer(), text).also { assertTrue(it.error.code.isNotBlank()) }
                    name == "config" -> json.decodeFromString(AppConfig.serializer(), text).also { assertTrue(it.credits.packs.isNotEmpty()) }
                    name == "me" -> json.decodeFromString(Me.serializer(), text)
                    name == "credits" -> json.decodeFromString(com.raviga.downwork.data.api.CreditsResponse.serializer(), text)
                    name == "comments" -> json.decodeFromString(com.raviga.downwork.data.api.CommentsResponse.serializer(), text)
                    name == "comment" -> json.decodeFromString(com.raviga.downwork.data.api.Comment.serializer(), text)
                    name.startsWith("delivery-targets") -> json.decodeFromString(com.raviga.downwork.data.api.DeliveryTargets.serializer(), text)
                    name == "aws-connect" -> json.decodeFromString(com.raviga.downwork.data.api.AwsConnectInfo.serializer(), text)
                    name.startsWith("projects") -> json.decodeFromString(ProjectsResponse.serializer(), text)
                    name.startsWith("project") -> json.decodeFromString(Project.serializer(), text).also { assertNotNull(it.id) }
                    name.startsWith("versions") -> json.decodeFromString(VersionsResponse.serializer(), text)
                    name.startsWith("document") -> json.decodeFromString(Document.serializer(), text).also { assertEquals(12, it.sections.size) }
                    name.startsWith("quote") -> json.decodeFromString(Quote.serializer(), text).also { assertTrue(it.credits > 0) }
                    name.startsWith("register") -> json.decodeFromString(RegisterDeviceResponse.serializer(), text)
                    else -> null
                }
            }.onSuccess { if (it != null) checked++ }
                .onFailure { throw AssertionError("fixture ${f.name} failed to decode: ${it.message}", it) }
        }
        println("decoded $checked of ${files.size} fixtures")
    }
}
