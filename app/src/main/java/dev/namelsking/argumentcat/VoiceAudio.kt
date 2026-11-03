package dev.namelsking.argumentcat

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceAudio(private val context: Context) {
    @Volatile private var recording = false
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var playback: AudioTrack? = null

    @SuppressLint("MissingPermission")
    suspend fun record(onLimit: () -> Unit): File {
        // Set before switching threads so an immediate Stop cannot be lost.
        recording = true
        return withContext(Dispatchers.IO) {
            val rate = 16_000
            val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "Microphone format unavailable" }
            val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 8192))
            if (audio.state != AudioRecord.STATE_INITIALIZED) { audio.release(); error("Couldn't open microphone") }
            recorder = audio
            val file = File.createTempFile("argument-", ".f32le", context.cacheDir)
            var complete = false
            try {
                audio.startRecording()
                file.outputStream().use { output ->
                    val samples = ShortArray(2048)
                    var count = 0
                    while (recording && currentCoroutineContext().isActive && count < rate * 20) {
                        val read = audio.read(samples, 0, minOf(samples.size, rate * 20 - count))
                        if (read < 0) { if (!recording) break else error("Microphone read failed ($read)") }
                        if (read == 0) continue
                        val bytes = ByteBuffer.allocate(read * 4).order(ByteOrder.LITTLE_ENDIAN)
                        repeat(read) { bytes.putFloat(samples[it] / 32768f) }
                        output.write(bytes.array())
                        count += read
                    }
                    currentCoroutineContext().ensureActive()
                    check(count > 0) { "No audio recorded" }
                    if (count >= rate * 20) withContext(Dispatchers.Main) { onLimit() }
                }
                complete = true
                file
            } finally {
                recording = false
                runCatching { audio.stop() }
                audio.release()
                if (recorder === audio) recorder = null
                if (!complete) file.delete()
            }
        }
    }

    fun stopRecording() { recording = false; runCatching { recorder?.stop() } }
    fun stopPlayback() { runCatching { playback?.pause(); playback?.flush() } }

    suspend fun play(samples: ShortArray) = withContext(Dispatchers.IO) {
        val format = AudioFormat.Builder().setSampleRate(44_100).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(format).setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(8192, AudioTrack.getMinBufferSize(44_100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))).build()
        if (track.state != AudioTrack.STATE_INITIALIZED) { track.release(); error("Audio output unavailable") }
        playback = track
        try {
            track.setVolume(1f)
            track.play()
            var written = 0
            while (written < samples.size) {
                currentCoroutineContext().ensureActive()
                val count = track.write(samples, written, minOf(2048, samples.size - written), AudioTrack.WRITE_BLOCKING)
                check(count > 0) { "Audio playback failed" }
                written += count
            }
            while (track.playbackHeadPosition < samples.size) { delay(25); currentCoroutineContext().ensureActive() }
        } finally {
            runCatching { track.stop() }
            track.release()
            if (playback === track) playback = null
        }
    }
}
