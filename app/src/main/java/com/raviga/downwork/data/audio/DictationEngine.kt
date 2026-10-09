package com.raviga.downwork.data.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Live dictation through the platform SpeechRecognizer. The recognizer stops
 * at every pause, so this restarts it until [stop] is called and reports each
 * finished utterance separately; the view model stitches them into a transcript.
 */
class DictationEngine(private val context: Context) {

    sealed interface Event {
        data object Ready : Event
        data class Partial(val text: String) : Event
        data class Utterance(val text: String) : Event
        data class Level(val rmsDb: Float) : Event
        data class Error(val code: Int, val fatal: Boolean) : Event
    }

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun listen(language: Locale = Locale.getDefault()): Flow<Event> = callbackFlow {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var active = true
        // Busy/client errors in a row: past a few, the recognizer is stuck rather than idle.
        var stuck = 0

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Keep listening through natural pauses between sentences.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }

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

        /** Maps the recognizer's rms (about -2..10 dB) onto 0..1 for the ink line. */
        fun levelFromRms(rmsDb: Float): Float = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
    }
}
