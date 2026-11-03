package dev.namelsking.argumentcat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = QvacEngine(application)
    private val audio = VoiceAudio(application)
    private var voiceJob: Job? = null
    private var voiceEpoch = 0
    private val mutableVoice = MutableStateFlow("idle")
    val voice = mutableVoice.asStateFlow()
    val voiceDetail = MutableStateFlow("")
    private var cachedVoice: Pair<String, ShortArray>? = null
    val muted = MutableStateFlow(false)
    val input = MutableStateFlow("")
    val store = GameStore(viewModelScope, engine, ::speak)

    private fun stopVoice(): Job? {
        val previous = voiceJob
        voiceEpoch++
        audio.stopRecording()
        audio.stopPlayback()
        voiceJob?.cancel()
        voiceJob = null
        mutableVoice.value = "idle"
        voiceDetail.value = ""
        return previous
    }
    fun toggleMute() { muted.value = !muted.value; if (muted.value && voice.value != "recording" && voice.value != "transcribing") stopVoice() }
    fun send() {
        if (store.state.value.phase != Phase.INPUT) return
        stopVoice()
        val text = input.value
        if (text.isBlank() || text.trim().length > 400) return
        val turn = store.state.value.completed + 1
        store.send(text)
        viewModelScope.launch {
            val result = store.state.first { it.phase != Phase.THINKING }
            if (input.value == text && result.lines.any { it.role == "user" && it.turn == turn && it.text == text.trim() }) input.value = ""
        }
    }
    fun restart() { stopVoice(); cachedVoice = null; input.value = ""; store.reset() }
    fun cancel() { stopVoice(); store.cancel() }
    fun background() { cancel() }

    fun record() {
        if (store.state.value.phase != Phase.INPUT || voice.value in listOf("recording", "transcribing")) return
        val previous = stopVoice()
        val id = voiceEpoch
        mutableVoice.value = "recording"
        voiceJob = viewModelScope.launch {
            var file: java.io.File? = null
            try {
                previous?.join()
                if (id != voiceEpoch || voice.value != "recording") return@launch
                file = audio.record { if (id == voiceEpoch) mutableVoice.value = "transcribing" }
                if (id != voiceEpoch) return@launch
                mutableVoice.value = "transcribing"
                val text = engine.transcribe(file)
                if (id == voiceEpoch) {
                    check(text.isNotBlank()) { "Didn't catch that. Try again or type." }
                    input.value = text
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (id == voiceEpoch) store.notice(e.message ?: "Microphone unavailable. You can type instead.") }
            finally { file?.delete(); if (id == voiceEpoch) mutableVoice.value = "idle" }
        }
    }
    fun finishRecording() { if (voice.value == "recording") { mutableVoice.value = "transcribing"; audio.stopRecording() } }
    fun judge() { stopVoice(); store.judge() }

    fun hearReply() {
        if (voice.value in listOf("synthesizing", "speaking")) { stopVoice(); return }
        if (store.state.value.phase !in listOf(Phase.INPUT, Phase.FINISHED) || voice.value != "idle") return
        val text = store.state.value.lines.lastOrNull { it.role == "assistant" }?.text ?: return
        muted.value = false
        speak(text)
    }

    private fun speak(text: String) {
        if (muted.value) return
        val previous = stopVoice()
        val id = voiceEpoch
        mutableVoice.value = "synthesizing"
        voiceDetail.value = "Preparing voice…"
        voiceJob = viewModelScope.launch {
            try {
                previous?.join()
                val pcm = cachedVoice?.takeIf { it.first == text }?.second ?: engine.synthesize(text) {
                    if (id == voiceEpoch) voiceDetail.value = it
                }
                if (id != voiceEpoch || muted.value) return@launch
                cachedVoice = text to pcm
                store.notice(null)
                mutableVoice.value = "speaking"
                voiceDetail.value = "The cat is speaking…"
                audio.play(pcm)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (id == voiceEpoch) store.notice("Voice unavailable; the text reply is still here. ${e.message.orEmpty()}") }
            finally { if (id == voiceEpoch) { mutableVoice.value = "idle"; voiceDetail.value = "" } }
        }
    }
    override fun onCleared() {
        stopVoice()
        // viewModelScope is cancelled now; finish service cleanup on a separate, finite scope.
        CoroutineScope(Dispatchers.Main.immediate).launch { withTimeoutOrNull(10_000L) { engine.close() } }
    }
}
