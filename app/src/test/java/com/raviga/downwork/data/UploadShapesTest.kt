package com.raviga.downwork.data

import com.raviga.downwork.data.api.ExtractResult
import com.raviga.downwork.data.api.FileTypes
import com.raviga.downwork.data.api.Project
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract v0.5: uploads, screening and purged inputs. */
class UploadShapesTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test fun fileTypesComeFromTheProviderOrTheExtension() {
        val accepted = FileTypes.DEFAULT
        assertEquals(FileTypes.PDF, FileTypes.resolve("brief.pdf", "application/pdf", accepted))
        assertEquals(FileTypes.MD, FileTypes.resolve("notes.md", "application/octet-stream", accepted))
        assertEquals(FileTypes.RTF, FileTypes.resolve("spec.rtf", "text/rtf", accepted))
        assertEquals(FileTypes.DOCX, FileTypes.resolve("Spec.DOCX", null, accepted))
        assertNull(FileTypes.resolve("old.doc", "application/msword", accepted))
        assertNull(FileTypes.resolve("photo.jpg", "image/jpeg", accepted))
    }

    @Test fun extractResultDecodes() {
        val r = json.decodeFromString(
            ExtractResult.serializer(),
            """{"fileId":"fl_1","fileName":"brief.pdf","text":"Hello","pageCount":null,"truncated":true,"notice":"Only the first 60 pages fit."}""",
        )
        assertNull(r.pageCount)
        assertTrue(r.truncated)
        assertEquals("Only the first 60 pages fit.", r.notice)
    }

    @Test fun screeningAndFileInputsDecode() {
        val p = json.decodeFromString(
            Project.serializer(),
            """
            {"id":"pr_1","status":"draft",
             "screening":{"status":"rejected","reason":"Outside our Acceptable use policy.","checkedAt":"2026-10-09T10:00:00Z"},
             "inputs":[
               {"id":"in_1","kind":"file","text":"Bakery app","fileId":"fl_1","fileName":"brief.pdf","pageCount":4,
                "screening":{"status":"clear","notice":"We removed an API key from this note."}},
               {"id":"in_2","kind":"something_new","text":"","purgedAt":"2026-11-09T00:00:00Z"}
             ]}
            """.trimIndent(),
        )
        assertTrue(p.isRejected)
        assertEquals("brief.pdf", p.inputs[0].fileName)
        assertEquals("We removed an API key from this note.", p.inputs[0].screening?.notice)
        assertEquals("2026-11-09T00:00:00Z", p.inputs[1].purgedAt)
    }
}
