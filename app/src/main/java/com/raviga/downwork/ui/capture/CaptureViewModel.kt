package com.raviga.downwork.ui.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.downwork.data.api.ApiException
import com.raviga.downwork.data.audio.DictationEngine
import com.raviga.downwork.data.files.FileImporter
import com.raviga.downwork.data.files.TextExtractor
import com.raviga.downwork.data.screening.SensitiveScan
import com.raviga.downwork.di.AppContainer
import com.raviga.downwork.ui.userLine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Capture → review → Drafting, local-first (contract v0.6). Speech is
 * recognised on the phone, documents are read on the phone, and the notes are
 * saved in the local draft. Only their text goes to the AI to write the brief.
 */
class CaptureViewModel(
    private val container: AppContainer,
    initialDraftId: String,
    private val mode: String,
    initialTab: String = DescribeWith.SPEAK,
) : ViewModel() {

    enum class Phase { CAPTURE, TRANSCRIPT, DRAFTING }

    /** What was read from a document on this phone. */
    data class FileRead(
        val fileName: String,
        val pageCount: Int?,
        val truncated: Boolean,
        val notice: String?,
    )

    sealed interface Speech {
        data object Checking : Speech
        data class Ready(val engine: DictationEngine.Availability.Ready) : Speech
        data class NotYet(val message: String) : Speech
        data class Unavailable(val message: String) : Speech
    }

    data class State(
        val phase: Phase = Phase.CAPTURE,
        val tab: Int = 0,                       // 0 speak, 1 type, 2 upload
        val speech: Speech = Speech.Checking,
        val listening: Boolean = false,
        val committed: List<String> = emptyList(),
        val partial: String = "",
        val typed: String = "",
        val level: Float = 0f,
        val elapsedSec: Int = 0,
        val transcript: String = "",
        /** Reading a document on the phone. */
        val reading: Boolean = false,
        val progress: Float? = null,
        val progressMessage: String? = null,
        val error: String? = null,
        val needsConsent: Boolean = false,
        val done: String? = null,               // draft id to open
        val file: FileRead? = null,
        /** Title of the "this looks like it includes…" sheet, while it is open. */
        val sensitive: String? = null,
        /** Bumped to ask the review editor for focus (after "Edit" on the sensitive sheet). */
        val focusEditor: Int = 0,
    ) {
        val hasSpeech get() = committed.isNotEmpty() || partial.isNotBlank()
    }

    private val _state = MutableStateFlow(
        State(
            tab = when (initialTab) {
                DescribeWith.TYPE -> TAB_TYPE
                DescribeWith.UPLOAD -> TAB_UPLOAD
                else -> TAB_SPEAK
            },
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    val isAppend: Boolean get() = mode == "append"

    private var draftId: String? = initialDraftId.takeIf { it != "new" }
    private var listenJob: Job? = null
    private var timerJob: Job? = null
    private var pendingAfterConsent: (() -> Unit)? = null
    /** Utterances already moved into the transcript, so "Record more" adds only the new ones. */
    private var consumedUtterances = 0
    /** The note saved for this transcript (id, text): a retry must not add it twice. */
    private var savedInput: Pair<String, String>? = null
    /** Text the client chose to keep despite the sensitive-data warning. */
    private var acknowledgedText: String? = null

    init { checkSpeech() }

    fun checkSpeech() {
        viewModelScope.launch {
            val speech = when (val a = container.dictation.availability()) {
                is DictationEngine.Availability.Ready -> Speech.Ready(a)
                is DictationEngine.Availability.Downloading ->
                    Speech.NotYet("Getting ${a.language} speech ready on this phone so it works offline. Try again in a minute.")
                is DictationEngine.Availability.Unavailable -> Speech.Unavailable(a.message)
            }
            _state.update { it.copy(speech = speech) }
        }
    }

    fun selectTab(index: Int) {
        if (_state.value.listening) stopListening()
        _state.update { it.copy(tab = index, error = null) }
    }

    fun setTyped(text: String) = _state.update { it.copy(typed = text) }
    fun setTranscript(text: String) = _state.update { it.copy(transcript = text) }

    /** Emulators have no microphone and demos want a quick start: offer a canned description. */
    val offersSample: Boolean get() = container.isDemo || isEmulator()

    fun useSample() = _state.update { it.copy(tab = TAB_TYPE, typed = SAMPLE_DESCRIPTION, error = null) }

    private fun isEmulator(): Boolean {
        val fp = android.os.Build.FINGERPRINT.lowercase()
        val product = android.os.Build.PRODUCT.lowercase()
        return fp.contains("generic") || fp.contains("emulator") || product.contains("sdk") || product.contains("emulator") ||
            android.os.Build.HARDWARE.lowercase().let { it.contains("goldfish") || it.contains("ranchu") }
    }

    private fun needsConsent(): Boolean = container.session.needsAiConsent(container.session.me.value)

    /** The screen has navigated to the consent flow; do not ask again on re-entry. */
    fun consentRequested() = _state.update { it.copy(needsConsent = false) }

    fun consentGranted() {
        _state.update { it.copy(needsConsent = false) }
        pendingAfterConsent?.invoke()
        pendingAfterConsent = null
    }

    // ----- Speak (on the device only) -----

    fun toggleListening() {
        if (_state.value.listening) stopListening() else startListening()
    }

    private fun startListening() {
        val speech = _state.value.speech
        if (speech !is Speech.Ready) {
            if (speech is Speech.NotYet) checkSpeech()
            return
        }
        _state.update { it.copy(listening = true, error = null) }
        startTimer()
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            container.dictation.listen(speech.engine).collect { event ->
                when (event) {
                    is DictationEngine.Event.Partial -> _state.update { it.copy(partial = event.text) }
                    is DictationEngine.Event.Utterance -> _state.update { it.copy(committed = it.committed + event.text, partial = "") }
                    is DictationEngine.Event.Level -> _state.update { it.copy(level = DictationEngine.levelFromRms(event.rmsDb)) }
                    is DictationEngine.Event.Error -> if (event.fatal) {
                        stopListening()
                        _state.update { it.copy(error = DictationEngine.errorLine(event.code)) }
                    }
                    DictationEngine.Event.Ready -> Unit
                }
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel(); listenJob = null
        stopTimer()
        _state.update { s ->
            val committed = if (s.partial.isNotBlank()) s.committed + s.partial else s.committed
            s.copy(listening = false, level = 0f, committed = committed, partial = "")
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _state.update { it.copy(elapsedSec = it.elapsedSec + 1) }
            }
        }
    }

    private fun stopTimer() { timerJob?.cancel(); timerJob = null }

    // ----- Upload (read on the device) -----

    fun pickedFile(uri: Uri) {
        val limits = container.session.config.value.limits
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            val picked = try {
                container.files.import(uri, limits.fileMaxBytes, limits.acceptedFileTypes)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Couldn't open that file.") }
                return@launch
            }
            // A new document is a new note, even if an earlier one was already saved.
            savedInput = null
            acknowledgedText = null
            _state.update {
                it.copy(
                    phase = Phase.TRANSCRIPT, tab = TAB_UPLOAD, reading = true, transcript = "", file = null,
                    progress = null, progressMessage = "Reading ${picked.name}", error = null,
                )
            }
            try {
                val result = container.extractor.extract(
                    picked.file, picked.contentType,
                    maxChars = limits.textInputMaxChars, maxPages = limits.fileMaxPages,
                ) { step -> _state.update { it.copy(progressMessage = step) } }
                _state.update {
                    it.copy(
                        reading = false, progress = null, progressMessage = null,
                        transcript = result.text,
                        file = FileRead(picked.name, result.pageCount, result.truncated, result.notice),
                    )
                }
            } catch (e: TextExtractor.Unreadable) {
                _state.update { it.copy(phase = Phase.CAPTURE, reading = false, progressMessage = null, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(phase = Phase.CAPTURE, reading = false, progressMessage = null, error = "Couldn't read that file. Try another copy, or paste the text instead.") }
            } finally {
                // The file never leaves the phone, and doesn't stay on it either.
                picked.file.delete()
            }
        }
    }

    // ----- Done → review -----

    fun finishCapture() {
        if (_state.value.listening) stopListening()
        val s = _state.value
        when (s.tab) {
            TAB_UPLOAD -> return
            TAB_TYPE -> _state.update { it.copy(phase = Phase.TRANSCRIPT, transcript = s.typed.trim()) }
            else -> {
                val fresh = s.committed.drop(consumedUtterances).joinToString(" ")
                consumedUtterances = s.committed.size
                _state.update { it.copy(phase = Phase.TRANSCRIPT, transcript = joinText(it.transcript, fresh)) }
            }
        }
    }

    private fun joinText(a: String, b: String): String = listOf(a.trim(), b.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    fun recordMore() {
        _state.update {
            if (it.tab == TAB_UPLOAD) it.copy(phase = Phase.CAPTURE, error = null, file = null, transcript = "")
            else it.copy(phase = Phase.CAPTURE, error = null)
        }
    }

    // ----- Sensitive data (layer 0) -----

    fun removeSensitive() {
        _state.update { it.copy(transcript = SensitiveScan.redact(it.transcript.trim()), sensitive = null) }
        writeBrief()
    }

    fun keepSensitive() {
        acknowledgedText = _state.value.transcript.trim()
        _state.update { it.copy(sensitive = null) }
        writeBrief()
    }

    fun editSensitive() = _state.update { it.copy(sensitive = null, focusEditor = it.focusEditor + 1) }

    // ----- Review → Drafting -----

    fun writeBrief() {
        val s = _state.value
        val text = s.transcript.trim()
        if (text.isBlank()) {
            _state.update { it.copy(error = "Say or type something about the project first.") }
            return
        }
        // Text goes to the AI from here on; ask once.
        if (needsConsent()) {
            pendingAfterConsent = { writeBrief() }
            _state.update { it.copy(needsConsent = true) }
            return
        }
        if (text != acknowledgedText && text != savedInput?.second) {
            val findings = SensitiveScan.scan(text)
            if (findings.isNotEmpty()) {
                _state.update { it.copy(sensitive = SensitiveScan.title(findings), error = null) }
                return
            }
        }
        _state.update { it.copy(phase = Phase.DRAFTING, error = null, progress = null, progressMessage = "Reading your notes") }
        viewModelScope.launch {
            val drafts = container.drafts
            val id = draftId ?: drafts.create().id.also { draftId = it }
            try {
                val saved = savedInput
                when {
                    saved == null -> {
                        val voice = s.tab == TAB_SPEAK
                        val file = s.file?.takeIf { s.tab == TAB_UPLOAD }
                        val input = drafts.addInput(
                            id = id,
                            kind = when {
                                file != null -> "file"
                                voice -> "voice"
                                else -> "text"
                            },
                            text = text,
                            languageDetected = (s.speech as? Speech.Ready)?.engine?.languageTag?.takeIf { voice },
                            fileName = file?.fileName,
                            pageCount = file?.pageCount,
                            durationSec = s.elapsedSec.takeIf { voice && it > 0 },
                        )
                        savedInput = input.id to text
                    }
                    saved.second != text -> {
                        drafts.editInput(id, saved.first, text)
                        savedInput = saved.first to text
                    }
                }
                drafts.writeBrief(id) { st ->
                    _state.update { it.copy(progress = st.progress, progressMessage = st.message ?: it.progressMessage) }
                }
                _state.update { it.copy(done = id) }
            } catch (e: Exception) {
                onWriteFailed(id, e)
            }
        }
    }

    private suspend fun onWriteFailed(id: String, e: Exception) {
        val api = e as? ApiException
        val legal = api?.detailStrings("missing")?.any { it == "terms" || it == "privacy" } == true
        when {
            // Terms out of date: the app shows them (ConsentInterceptor). AI consent is asked here.
            api?.code == ApiException.CONSENT_REQUIRED && !legal -> {
                pendingAfterConsent = { writeBrief() }
                _state.update { it.copy(phase = Phase.TRANSCRIPT, needsConsent = true, progress = null, progressMessage = null) }
            }
            // A policy refusal froze the draft (DraftRepository); the brief screen explains it.
            api?.code == ApiException.CONTENT_REJECTED && api.detailString("kind") == "policy" ->
                _state.update { it.copy(done = id) }
            else -> {
                // A quality refusal means this note isn't usable as it is: take it back out, so a
                // fixed version replaces it instead of joining it.
                if (api?.code == ApiException.CONTENT_REJECTED) {
                    savedInput?.let { runCatching { container.drafts.removeInput(id, it.first) } }
                    savedInput = null
                }
                _state.update { it.copy(phase = Phase.TRANSCRIPT, error = e.userLine(), progress = null, progressMessage = null) }
            }
        }
    }

    override fun onCleared() {
        listenJob?.cancel()
        timerJob?.cancel()
        // A draft made here that never got a note is just noise on Home.
        val id = draftId
        if (id != null && savedInput == null && mode != "append") {
            container.appScope.launch {
                container.drafts.get(id)?.takeIf { it.inputs.isEmpty() && it.versions.isEmpty() }?.let { container.drafts.delete(it.id) }
            }
        }
    }

    companion object {
        const val TAB_SPEAK = 0
        const val TAB_TYPE = 1
        const val TAB_UPLOAD = 2
        const val SAMPLE_DESCRIPTION = "I want an app for my salon in Delhi called GlowBook. Customers should be able to see the services and prices, pick a stylist, book a slot, and pay online with UPI. They should get a reminder the day before. Staff need a simple admin panel to manage the calendar, mark no-shows and see daily earnings. Later I might add loyalty points."
    }
}
