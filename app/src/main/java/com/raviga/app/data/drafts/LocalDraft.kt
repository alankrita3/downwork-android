package com.raviga.app.data.drafts

import com.raviga.app.data.api.Document
import com.raviga.app.data.api.Quote
import kotlinx.serialization.Serializable

/**
 * A project that lives only on this phone until it is submitted (founder's
 * local-first rule, contract v0.6). Recordings and files never get here:
 * only the text read from them. After submit it keeps [serverProjectId] and a
 * copy of the brief, so the client can still read it once the server deletes
 * its copy on acceptance.
 */
@Serializable
data class LocalDraft(
    val id: String,
    val title: String = "",
    val createdAt: String,
    val updatedAt: String,
    val inputs: List<LocalInput> = emptyList(),
    /** Inputs the brief already reflects; the rest go to /ai/append next time. */
    val inputsInBrief: List<String> = emptyList(),
    /** Oldest first; each is a full copy of the brief. */
    val versions: List<LocalVersion> = emptyList(),
    val quote: Quote? = null,
    val quoteToken: String? = null,
    /** The brief version the quote was made for; any later edit needs a new quote. */
    val quotedVersion: Int? = null,
    val refusal: Refusal? = null,
    val serverProjectId: String? = null,
    val submittedVersion: Int? = null,
    val submittedAt: String? = null,
) {
    val current: LocalVersion? get() = versions.lastOrNull()
    val document: Document? get() = current?.document
    val isSubmitted: Boolean get() = serverProjectId != null
    val isRefused: Boolean get() = refusal != null
    val quoteIsCurrent: Boolean get() = quote != null && quoteToken != null && quotedVersion == current?.version
    val displayTitle: String get() = (document?.title ?: title).ifBlank { title }
    val newInputs: List<LocalInput> get() = inputs.filter { it.id !in inputsInBrief }
}

@Serializable
data class LocalInput(
    val id: String,
    val kind: String,                 // voice | text | file
    val text: String,
    val languageDetected: String? = null,
    val fileName: String? = null,
    val pageCount: Int? = null,
    val durationSec: Int? = null,
    val createdAt: String,
)

@Serializable
data class LocalVersion(
    val version: Int,
    val document: Document,
    val createdAt: String,
    /** draft | append | regenerate | edit | restore */
    val source: String,
    val changeSummary: String = "",
)

/** Screening refused this brief; it is frozen and can only be deleted. */
@Serializable
data class Refusal(val reason: String, val at: String)
