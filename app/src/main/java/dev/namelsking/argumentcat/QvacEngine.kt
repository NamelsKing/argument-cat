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
    private var activeModel: Pair<String, String>? = null
    var status: (String) -> Unit = {}

    private suspend fun client(): QvacClient = client ?: QvacClient(
        AndroidServiceTransport.connect(context.applicationContext),
    ).also { client = it }

    private suspend fun model(model: ModelConstant, config: JsonObject): String {
        activeModel?.takeIf { it.first == model.name }?.let { return it.second }
        val sdk = client()
        activeModel?.let { sdk.models.unload(it.second, clearStorage = false) }
        activeModel = null
        status("Loading ${model.name}…")
        sdk.models.loadWithProgress(
            source = model.src, modelType = model.engine, modelName = model.name, modelConfig = config,
        ).collect { event ->
            when (event) {
                is QvacProgressEvent.Progress -> status("Model download ${event.value.percentage.toInt()}%")
                is QvacProgressEvent.Result -> {
                    check(event.value.success) { event.value.error ?: "Model load failed" }
                    activeModel = model.name to requireNotNull(event.value.modelId)
                }
            }
        }
        return requireNotNull(activeModel?.second) { "No model returned" }
    }

    private val llmConfig get() = buildJsonObject {
        put("ctx_size", 8192); put("device", "cpu"); put("gpu_layers", 0); put("n_threads", 4)
    }
    override suspend fun prepare(status: (String) -> Unit) = lock.withLock {
        this.status = status
        withTimeout(15 * 60_000L) { model(Models.QWEN3_600M_INST_Q4, llmConfig) }
        Unit
    }

    override suspend fun complete(system: String, history: List<Line>, json: Boolean): String = lock.withLock {
        val id = withTimeout(15 * 60_000L) { model(Models.QWEN3_600M_INST_Q4, llmConfig) }
        withTimeout(90_000L) {
            val run = client().completion.run(
                modelId = id,
                history = listOf(QvacMessage.system(system)) + history.map { QvacMessage(it.role, it.text) },
                options = QvacCompletionOptions(
                    generation = QvacGenerationOptions(predict = 256, reasoningBudget = 0, temperature = if (json) 0.2 else 0.7),
                    responseFormat = if (json) QvacResponseFormat.JsonValue else QvacResponseFormat.Text,
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
        val id = withTimeout(15 * 60_000L) { model(Models.WHISPER_TINY_Q8_0, config) }
        withTimeout(90_000L) { client().speech.transcribe(id, QvacDataInput.FilePath(audio.absolutePath)).text.trim().take(400) }
    }

    suspend fun synthesize(text: String): ShortArray = lock.withLock {
        val config = buildJsonObject { put("ttsEngine", "supertonic"); put("language", "en") }
        val id = withTimeout(15 * 60_000L) { model(Models.TTS_MULTILINGUAL_SUPERTONIC3_Q4_0, config) }
        // This specific public Supertonic3 model uses signed PCM16 at 44.1 kHz.
        withTimeout(90_000L) {
            client().speech.synthesize(id, text).samples.map {
                it.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }.toShortArray().also { check(it.isNotEmpty()) { "No speech returned" } }
        }
    }

    suspend fun close() = lock.withLock {
        activeModel = null
        val old = client
        client = null
        old?.close()
    }
}
