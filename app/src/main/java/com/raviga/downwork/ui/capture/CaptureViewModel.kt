package com.raviga.downwork.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.downwork.data.api.ApiException
import com.raviga.downwork.data.audio.AudioRecorder
import com.raviga.downwork.data.audio.DictationEngine
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
import java.util.Locale

/**
 * Capture → Transcript → Drafting. Live dictation when the device has it;
 * otherwise a recording that the backend transcribes. Either way the client
 * reviews the text before the brief is written.
 */
class CaptureViewModel(
    private val container: AppContainer,
    initialProjectId: String,
    private val mode: String,
) : ViewModel() {

    enum class Phase { CAPTURE, TRANSCRIPT, DRAFTING }

    data class State(
        val phase: Phase = Phase.CAPTURE,
        val tab: Int = 0,                       // 0 speak, 1 type
        val listening: Boolean = false,
        val committed: List<String> = emptyList(),
        val partial: String = "",
        val typed: String = "",
        val level: Float = 0f,
        val elapsedSec: Int = 0,
        val transcript: String = "",
        val transcribing: Boolean = false,
        val progress: Float? = null,
        val progressMessage: String? = null,
        val error: String? = null,
        val needsConsent: Boolean = false,
        val done: String? = null,               // project id to open
        val usesRecorder: Boolean = false,
    ) {
        val hasSpeech get() = committed.isNotEmpty() || partial.isNotBlank()
        val isAppend get() = false
    }

    private val _state = MutableStateFlow(State(usesRecorder = !container.dictation.isAvailable))
    val state: StateFlow<State> = _state.asStateFlow()

    val isAppend: Boolean get() = mode == "append"

    private var projectId: String? = initialProjectId.takeIf { it != "new" }
    private var listenJob: Job? = null
    private var timerJob: Job? = null
    private var levelJob: Job? = null
    private var recording: AudioRecorder.Recording? = null
    private var audioId: String? = null
    private var languageDetected: String? = null
    private var pendingAfterConsent: (() -> Unit)? = null
    /** Utterances already moved into the transcript, so "Record more" adds only the new ones. */
    private var consumedUtterances = 0
    /** The input saved for this transcript (id, text): a retry after a failed draft must not add it twice. */
    private var savedInput: Pair<String, String>? = null

    fun selectTab(index: Int) {
        if (_state.value.listening) stopListening()
        _state.update { it.copy(tab = index, error = null) }
    }

    fun setTyped(text: String) = _state.update { it.copy(typed = text) }

    /** Emulators have no microphone and demos want a quick start: offer a canned description. */
    val offersSample: Boolean get() = container.isDemo || isEmulator()

    fun useSample() = _state.update { it.copy(tab = 1, typed = SAMPLE_DESCRIPTION, error = null) }

    private fun isEmulator(): Boolean {
        val fp = android.os.Build.FINGERPRINT.lowercase()
        val product = android.os.Build.PRODUCT.lowercase()
        return fp.contains("generic") || fp.contains("emulator") || product.contains("sdk") || product.contains("emulator") ||
            android.os.Build.HARDWARE.lowercase().let { it.contains("goldfish") || it.contains("ranchu") }
    }

    companion object {
        const val SAMPLE_DESCRIPTION = "I want an app for my salon in Delhi called GlowBook. Customers should be able to see the services and prices, pick a stylist, book a slot, and pay online with UPI. They should get a reminder the day before. Staff need a simple admin panel to manage the calendar, mark no-shows and see daily earnings. Later I might add loyalty points."
    }
    fun setTranscript(text: String) = _state.update { it.copy(transcript = text) }

    private fun needsConsent(): Boolean = container.session.needsAiConsent(container.session.me.value)

    /** The screen has navigated to the consent flow; do not ask again on re-entry. */
    fun consentRequested() = _state.update { it.copy(needsConsent = false) }

    /** Called after the AI consent screen returns granted. */
    fun consentGranted() {
        _state.update { it.copy(needsConsent = false) }
        pendingAfterConsent?.invoke()
        pendingAfterConsent = null
    }

    fun consentDismissed() {
        _state.update { it.copy(needsConsent = false) }
        pendingAfterConsent = null
    }

    // ----- Speak -----

    fun toggleListening() {
        if (_state.value.listening) stopListening() else startListening()
    }

    private fun startListening() {
        if (needsConsent()) {
            pendingAfterConsent = { startListening() }
            _state.update { it.copy(needsConsent = true) }
            return
        }
        _state.update { it.copy(listening = true, error = null) }
        startTimer()
        if (_state.value.usesRecorder) startRecorder() else startDictation()
    }

    private fun startDictation() {
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            container.dictation.listen(Locale.getDefault()).collect { event ->
                when (event) {
                    is DictationEngine.Event.Partial -> _state.update { it.copy(partial = event.text) }
                    is DictationEngine.Event.Utterance -> _state.update { it.copy(committed = it.committed + event.text, partial = "") }
                    is DictationEngine.Event.Level -> _state.update { it.copy(level = DictationEngine.levelFromRms(event.rmsDb)) }
                    is DictationEngine.Event.Error -> if (event.fatal) {
                        stopListening()
                        _state.update { it.copy(error = "The microphone stopped. Tap the button to try again, or type instead.") }
                    }
                    DictationEngine.Event.Ready -> Unit
                }
            }
        }
    }

    private fun startRecorder() {
        runCatching { container.recorder.start(onLimit = { viewModelScope.launch { onRecorderLimit() } }) }
            .onFailure {
                _state.update { it.copy(listening = false, error = "Couldn't start the microphone. Type instead.") }
                stopTimer()
                return
            }
        levelJob?.cancel()
        levelJob = viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(level = container.recorder.level()) }
                delay(50)
            }
        }
    }

    private fun onRecorderLimit() {
        if (!_state.value.listening) return
        stopListening()
        _state.update { it.copy(error = "Recordings stop at 10 minutes. Tap Done, or record another part.") }
    }

    fun stopListening() {
        val wasRecorder = _state.value.usesRecorder
        listenJob?.cancel(); listenJob = null
        levelJob?.cancel(); levelJob = null
        stopTimer()
        if (wasRecorder) {
            recording = container.recorder.stop()
        }
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

    // ----- Done → Transcript -----

    fun finishCapture() {
        if (_state.value.listening) stopListening()
        val s = _state.value
        if (s.tab == 1) {
            _state.update { it.copy(phase = Phase.TRANSCRIPT, transcript = s.typed.trim()) }
            return
        }
        if (s.usesRecorder) {
            val rec = recording
            if (rec == null) {
                if (s.transcript.isNotBlank()) _state.update { it.copy(phase = Phase.TRANSCRIPT, error = null) }
                else _state.update { it.copy(error = "Nothing was recorded yet.") }
                return
            }
            // Each clip is transcribed once; "Record more" adds the next clip's words to the end.
            recording = null
            _state.update { it.copy(phase = Phase.TRANSCRIPT, transcribing = true, error = null) }
            viewModelScope.launch { transcribe(rec) }
        } else {
            val fresh = s.committed.drop(consumedUtterances).joinToString(" ")
            consumedUtterances = s.committed.size
            _state.update { it.copy(phase = Phase.TRANSCRIPT, transcript = joinText(it.transcript, fresh)) }
        }
    }

    private suspend fun transcribe(rec: AudioRecorder.Recording) {
        try {
            val id = ensureProject()
            val (audio, result) = container.projects.transcribe(id, rec.file, (rec.durationMs / 1000).toInt(), Locale.getDefault().language) { st ->
                _state.update { it.copy(progress = st.progress, progressMessage = st.message) }
            }
            audioId = audio
            languageDetected = result.languageDetected
            rec.file.delete()
            _state.update { it.copy(transcribing = false, transcript = joinText(it.transcript, result.transcript), progress = null, progressMessage = null) }
        } catch (e: Exception) {
            // Keep the clip so Done tries it again.
            if (recording == null) recording = rec
            _state.update { it.copy(transcribing = false, error = e.userLine(), progress = null) }
        }
    }

    private fun joinText(a: String, b: String): String = listOf(a.trim(), b.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    fun recordMore() {
        _state.update { it.copy(phase = Phase.CAPTURE, error = null) }
    }

    // ----- Transcript → Drafting -----

    fun writeBrief() {
        if (needsConsent()) {
            pendingAfterConsent = { writeBrief() }
            _state.update { it.copy(needsConsent = true) }
            return
        }
        val s = _state.value
        val text = s.transcript.trim()
        if (text.isBlank()) {
            _state.update { it.copy(error = "Say or type something about the project first.") }
            return
        }
        _state.update { it.copy(phase = Phase.DRAFTING, error = null, progress = null, progressMessage = "Listening back") }
        viewModelScope.launch {
            try {
                val id = ensureProject()
                val voice = s.tab == 0
                val saved = savedInput
                when {
                    saved == null -> {
                        val project = container.projects.addInput(
                            id = id,
                            kind = if (voice && (audioId != null || !s.usesRecorder)) "voice" else "text",
                            text = text,
                            audioId = audioId,
                            durationSec = if (voice) s.elapsedSec.takeIf { it > 0 } else null,
                            languageDetected = if (voice) (languageDetected ?: Locale.getDefault().language) else null,
                        )
                        val inputId = project.inputs.lastOrNull { it.text.trim() == text }?.id ?: project.inputs.lastOrNull()?.id
                        savedInput = inputId?.let { it to text }
                    }
                    saved.second != text -> {
                        container.projects.editInput(id, saved.first, text)
                        savedInput = saved.first to text
                    }
                }
                val onProgress: (com.raviga.downwork.data.api.JobState) -> Unit = { st ->
                    _state.update { it.copy(progress = st.progress, progressMessage = st.message ?: it.progressMessage) }
                }
                val hasDocument = container.projects.cachedProject(id)?.document != null
                if (isAppend && hasDocument) container.projects.append(id, onProgress) else container.projects.generate(id, "", onProgress)
                _state.update { it.copy(done = id) }
            } catch (e: Exception) {
                val line = if ((e as? ApiException)?.code == ApiException.CONSENT_REQUIRED) {
                    pendingAfterConsent = { writeBrief() }
                    _state.update { it.copy(needsConsent = true) }
                    null
                } else e.userLine()
                _state.update { it.copy(phase = Phase.TRANSCRIPT, error = line, progress = null, progressMessage = null) }
            }
        }
    }

    private suspend fun ensureProject(): String {
        projectId?.let { return it }
        val created = container.projects.create()
        projectId = created.id
        return created.id
    }

    override fun onCleared() {
        listenJob?.cancel()
        levelJob?.cancel()
        timerJob?.cancel()
        if (container.recorder.isRecording) container.recorder.cancel()
    }
}
