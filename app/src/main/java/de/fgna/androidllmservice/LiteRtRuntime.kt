package de.fgna.androidllmservice

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class GenerationResult(
    val text: String,
    val initializationMillis: Long,
    val generationMillis: Long,
    val coldStart: Boolean,
)

internal class LiteRtRuntime(private val context: Context) : AutoCloseable {
    private data class LoadedModel(
        val uri: Uri,
        val descriptor: ParcelFileDescriptor,
        val engine: Engine,
    )

    private val mutex = Mutex()
    private var loaded: LoadedModel? = null

    suspend fun generate(model: RegisteredModel, prompt: String): GenerationResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val loadStarted = System.nanoTime()
                val coldStart = loaded?.uri != model.uri
                if (coldStart) load(model)
                val initializationMillis = if (coldStart) elapsedMillis(loadStarted) else 0L

                val current = checkNotNull(loaded)
                val generationStarted = System.nanoTime()
                val response = current.engine.createConversation().use { conversation ->
                    conversation.sendMessage(prompt).text
                }
                GenerationResult(
                    text = response.trim(),
                    initializationMillis = initializationMillis,
                    generationMillis = elapsedMillis(generationStarted),
                    coldStart = coldStart,
                )
            }
        }

    suspend fun unload() = withContext(Dispatchers.IO) {
        mutex.withLock { closeLoaded() }
    }

    private fun load(model: RegisteredModel) {
        closeLoaded()
        val descriptor = context.contentResolver.openFileDescriptor(model.uri, "r")
            ?: error("Model file is no longer accessible.")
        val modelPath = "/proc/self/fd/${descriptor.fd}"

        val engine = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                cacheDir = context.cacheDir.absolutePath,
            ),
        )
        try {
            engine.initialize()
            loaded = LoadedModel(model.uri, descriptor, engine)
        } catch (gpuFailure: Throwable) {
            runCatching { engine.close() }
            val cpuEngine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath,
                ),
            )
            try {
                cpuEngine.initialize()
                loaded = LoadedModel(model.uri, descriptor, cpuEngine)
            } catch (cpuFailure: Throwable) {
                runCatching { cpuEngine.close() }
                descriptor.close()
                throw IllegalStateException(
                    "LiteRT-LM could not load the selected model on GPU or CPU. " +
                        "GPU: ${gpuFailure.message}; CPU: ${cpuFailure.message}",
                    cpuFailure,
                )
            }
        }
    }

    private fun closeLoaded() {
        val current = loaded ?: return
        loaded = null
        runCatching { current.engine.close() }
        runCatching { current.descriptor.close() }
    }

    override fun close() {
        closeLoaded()
    }
}

private fun elapsedMillis(startedAtNanos: Long): Long =
    (System.nanoTime() - startedAtNanos) / 1_000_000
