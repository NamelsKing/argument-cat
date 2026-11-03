package dev.namelsking.argumentcat

import android.content.Context
import io.tether.qvac.sdk.*
import io.tether.qvac.sdk.barekit.AndroidServiceTransport
import io.tether.qvac.sdk.generated.ModelConstant
import io.tether.qvac.sdk.generated.Models
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File

class QvacEngine(private val context: Context) : CatEngine {
    private val lock = Mutex()
    private var client: QvacClient? = null
    private val loadedModels = linkedMapOf<String, String>()
    private val chatModel = Models.QWEN3_5_2B_MULTIMODAL_Q4_K_M
    var status: (String) -> Unit = {}

    private suspend fun client(): QvacClient = client ?: QvacClient(
        AndroidServiceTransport.connect(context.applicationContext),
    ).also { client = it }

    private suspend fun model(model: ModelConstant, config: JsonObject, progress: (String) -> Unit = status): String {
        loadedModels[model.name]?.let { return it }
        val sdk = client()
        // Release the previous runtime before switching chat/speech on mobile.
        for ((_, id) in loadedModels) sdk.models.unload(id, clearStorage = false)
        loadedModels.clear()
        val label = if (model.name == chatModel.name) "chat model" else "voice model"
        progress("Loading $label…")
        sdk.models.loadWithProgress(
            source = model.src, modelType = model.engine, modelName = model.name, modelConfig = config,
        ).collect { event ->
            when (event) {
                is QvacProgressEvent.Progress -> progress("Downloading $label: ${event.value.percentage.toInt()}%")
                is QvacProgressEvent.Result -> {
                    check(event.value.success) { event.value.error ?: "Model load failed" }
                    loadedModels[model.name] = requireNotNull(event.value.modelId)
                }
            }
        }
        return requireNotNull(loadedModels[model.name]) { "No model returned" }
    }

    private val llmConfig get() = buildJsonObject {
        put("ctx_size", 4096); put("device", "cpu"); put("gpu_layers", 0); put("n_threads", 4)
    }
    override suspend fun prepare(status: (String) -> Unit) = lock.withLock {
        this.status = status
        modelDeadline(30 * 60_000L, "Model loading") { model(chatModel, llmConfig) }
        Unit
    }

    private fun responseFormat() = QvacResponseFormat.JsonSchema(
        "argument_cat_reply", buildJsonObject {
            put("type", "object"); put("additionalProperties", false)
            putJsonArray("required") { add("conceded"); add("reply") }
            putJsonObject("properties") {
                putJsonObject("conceded") { put("type", "boolean") }
                putJsonObject("reply") { put("type", "string"); put("minLength", 1); put("maxLength", 180) }
            }
        }, strict = true,
    )

    override suspend fun complete(system: String, history: List<Line>): String = lock.withLock {
        val id = modelDeadline(30 * 60_000L, "Model loading") { model(chatModel, llmConfig) }
        modelDeadline(90_000L, "The cat's reply") {
            val run = client().completion.run(
                modelId = id,
                history = listOf(QvacMessage.system(system)) + history.map { QvacMessage(it.role, it.text) },
                options = QvacCompletionOptions(
                    generation = QvacGenerationOptions(
                        predict = 96, reasoningBudget = 0,
                        temperature = 0.4, repeatPenalty = 1.1,
                    ),
                    responseFormat = responseFormat(),
                    kvCache = QvacKvCache.Enabled(false),
                ),
            )
            try { run.final.await().text }
            finally {
                if (!run.final.isCompleted) withContext(NonCancellable) {
                    runCatching { withTimeout(5_000L) { run.cancel() } }
                }
            }
        }
    }

    suspend fun transcribe(audio: File): String = lock.withLock {
        val config = buildJsonObject {
            put("audio_format", "f32le"); put("language", "en"); put("strategy", "greedy")
            put("n_threads", 4); put("translate", false); put("no_timestamps", true)
            putJsonObject("contextParams") { put("use_gpu", false); put("flash_attn", false) }
        }
        val id = modelDeadline(30 * 60_000L, "Model loading") { model(Models.WHISPER_TINY_Q8_0, config) }
        modelDeadline(90_000L, "Transcription") { client().speech.transcribe(id, QvacDataInput.FilePath(audio.absolutePath)).text.trim().take(400) }
    }

    suspend fun synthesize(text: String, progress: (String) -> Unit = {}): ShortArray = lock.withLock {
        val config = buildJsonObject {
            put("ttsEngine", "parler")
            put("description", "Laura speaks with a soft, warm female voice at a conversational pace. Her delivery is gentle and lightly playful, with clear articulation. The recording is very clear, with no background noise.")
            put("threads", 4); put("useGPU", false); put("seed", 42)
            put("maxFrames", 1720); put("outputSampleRate", 44_100)
        }
        val id = modelDeadline(30 * 60_000L, "Model loading") { model(Models.TTS_MINI_V1_EN_PARLER_TTS_Q8_0, config, progress) }
        // Parler Mini returns signed PCM16 at the same native 44.1 kHz as playback.
        progress("Generating the cat's voice…")
        modelDeadline(180_000L, "Voice generation") {
            val samples = client().speech.synthesize(id, text).samples
            SpeechPcm.normalized(samples).also {
                android.util.Log.d("ArgumentCatAudio", "engine=parler voice=Laura samples=${it.size} sourcePeak=${samples.maxOf { v -> kotlin.math.abs(v) }} playbackPeak=${it.maxOf { v -> kotlin.math.abs(v.toInt()) }}")
            }
        }
    }

    suspend fun close() = lock.withLock {
        loadedModels.clear()
        val old = client
        client = null
        old?.close()
    }
}
