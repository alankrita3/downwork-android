package com.raviga.downwork.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records AAC in an MP4 container (what the backend's upload-url expects as
 * audio/mp4). Used when live dictation is unavailable on the device; the
 * backend then transcribes the upload.
 */
class AudioRecorder(private val context: Context) {

    data class Recording(val file: File, val durationMs: Long)

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(): File {
        stopQuietly()
        val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val out = File(dir, "capture_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(44_100)
        r.setAudioEncodingBitRate(96_000)
        r.setMaxDuration(MAX_DURATION_MS)
        r.setOutputFile(out.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = out
        startedAt = System.currentTimeMillis()
        return out
    }

    /** 0..1 from the recorder's peak amplitude since the last call. */
    fun level(): Float {
        val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        return (amp / 12_000f).coerceIn(0f, 1f)
    }

    fun stop(): Recording? {
        val r = recorder ?: return null
        val out = file
        val duration = System.currentTimeMillis() - startedAt
        runCatching { r.stop() }
        runCatching { r.release() }
        recorder = null
        file = null
        return if (out != null && out.exists() && duration > 500) Recording(out, duration) else null
    }

    fun cancel() {
        stop()?.file?.delete()
    }

    private fun stopQuietly() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
    }

    companion object {
        const val MAX_DURATION_MS = 10 * 60 * 1000
    }
}
