package com.raviga.downwork.data.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Live dictation that never leaves the phone (contract v0.6): only the
 * on-device recogniser (Android 12+) is used, never the cloud one. It stops
 * at every pause, so this restarts it until the flow is cancelled and reports
 * each finished utterance separately. No audio is recorded or kept.
 */
class DictationEngine(private val context: Context) {

    sealed interface Event {
        data object Ready : Event
        data class Partial(val text: String) : Event
        data class Utterance(val text: String) : Event
        data class Level(val rmsDb: Float) : Event
        data class Error(val code: Int, val fatal: Boolean) : Event
    }

    sealed interface Availability {
        /** [languageTag] is the on-device model that will be used. */
        data class Ready(val languageTag: String, val alsoHindi: Boolean) : Availability
        /** The model for this language is downloading (we asked for it); try again shortly. */
        data class Downloading(val language: String) : Availability
        data class Unavailable(val message: String) : Availability
    }

    /** True only where speech can be recognised on the device itself. */
    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /** Which on-device model to use for [locale], downloading it first if the phone offers that. Main thread. */
    suspend fun availability(locale: Locale = Locale.getDefault()): Availability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Availability.Unavailable(NEEDS_ANDROID_12)
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return Availability.Unavailable(NO_OFFLINE_SPEECH)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Availability.Ready(locale.toLanguageTag(), alsoHindi = false)
        return supportOnT(locale)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun supportOnT(locale: Locale): Availability {
        val support = querySupport(locale) ?: return Availability.Ready(locale.toLanguageTag(), alsoHindi = false)
        val installed = support.installedOnDeviceLanguages
        val wanted = locale.toLanguageTag()
        pick(wanted, installed)?.let { tag ->
            return Availability.Ready(tag, alsoHindi = installed.any { it.startsWith("hi") } && !tag.startsWith("hi"))
        }
        val pending = support.pendingOnDeviceLanguages
        pick(wanted, pending)?.let { return Availability.Downloading(locale.displayLanguage) }
        val supported = support.supportedOnDeviceLanguages
        pick(wanted, supported)?.let { tag ->
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                runCatching { triggerModelDownload(intent(Locale.forLanguageTag(tag), alsoHindi = false)) }
                destroy()
            }
            return Availability.Downloading(locale.displayLanguage)
        }
        // The phone's language has no offline model: fall back to an English one if installed.
        pick("en-IN", installed)?.let { return Availability.Ready(it, alsoHindi = installed.any { l -> l.startsWith("hi") }) }
        return Availability.Unavailable(NO_MODEL_FOR_LANGUAGE)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun querySupport(locale: Locale): RecognitionSupport? = suspendCancellableCoroutine { cont ->
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        cont.invokeOnCancellation { runCatching { recognizer.destroy() } }
        runCatching {
            recognizer.checkRecognitionSupport(
                intent(locale, alsoHindi = false),
                ContextCompat.getMainExecutor(context),
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        recognizer.destroy()
                        if (cont.isActive) cont.resume(support)
                    }
                    override fun onError(error: Int) {
                        recognizer.destroy()
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }.onFailure {
            recognizer.destroy()
            if (cont.isActive) cont.resume(null)
        }
    }

    /** Same language exactly, else the same language in another region. */
    private fun pick(wanted: String, offered: List<String>): String? {
        offered.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return it }
        val lang = wanted.substringBefore('-').lowercase()
        return offered.firstOrNull { it.substringBefore('-').lowercase() == lang }
    }

    private fun intent(language: Locale, alsoHindi: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        // Keep listening through natural pauses between sentences.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        // English or Hindi, as the chooser promises, where the phone has both models (Android 14+).
        if (alsoHindi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val languages = arrayListOf(language.toLanguageTag(), "hi-IN")
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
            putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, languages)
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
            putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, languages)
        }
    }

    /** Collect on the main thread. Requires [Availability.Ready]. */
    @RequiresApi(Build.VERSION_CODES.S)
    fun listen(ready: Availability.Ready): Flow<Event> = callbackFlow {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        var active = true
        // Busy/client errors in a row: past a few, the recognizer is stuck rather than idle.
        var stuck = 0
        val intent = intent(Locale.forLanguageTag(ready.languageTag), ready.alsoHindi)

        fun restart() {
            if (!active) return
            runCatching { recognizer.startListening(intent) }
        }

        fun restartSoon(afterMs: Long) {
            launch {
                delay(afterMs)
                restart()
            }
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(Event.Ready) }
            override fun onBeginningOfSpeech() { stuck = 0 }
            override fun onRmsChanged(rmsdB: Float) { trySend(Event.Level(rmsdB)) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) trySend(Event.Partial(text))
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) trySend(Event.Utterance(text))
                stuck = 0
                restart()
            }
            override fun onError(error: Int) {
                val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                val hiccup = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT
                if (hiccup) stuck++ else if (silence) stuck = 0
                val recoverable = silence || (hiccup && stuck <= MAX_HICCUPS)
                trySend(Event.Error(error, fatal = !recoverable))
                if (recoverable) restartSoon(if (hiccup) 250L * stuck else 100L)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        restart()

        awaitClose {
            active = false
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.destroy() }
        }
    }

    companion object {
        private const val MAX_HICCUPS = 5

        const val NEEDS_ANDROID_12 = "Speaking needs Android 12 or newer, so your voice never leaves this phone. Type or upload instead."
        const val NO_OFFLINE_SPEECH = "This phone can't recognise speech offline, and your voice never leaves it. Type or upload instead."
        const val NO_MODEL_FOR_LANGUAGE = "This phone has no offline speech for your language yet. Type or upload instead."

        /** Copy for a fatal recogniser error. */
        fun errorLine(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> NO_MODEL_FOR_LANGUAGE
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "DownWork needs the microphone to listen. Allow it in Settings, or type instead."
            else -> "The microphone stopped. Tap the button to try again, or type instead."
        }

        /** Maps the recognizer's rms (about -2..10 dB) onto 0..1 for the ink line. */
        fun levelFromRms(rmsDb: Float): Float = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
    }
}
