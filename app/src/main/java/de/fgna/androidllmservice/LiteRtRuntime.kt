package de.fgna.androidllmservice

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
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
    private data class LoadedModel(val uri: Uri, val engine: Engine)
    private val mutex = Mutex()
    private var loaded: LoadedModel? = null

    suspend fun generate(model: RegisteredModel, prompt: String): GenerationResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loadStarted = System.nanoTime()
            val coldStart = loaded?.uri != model.uri
            if (coldStart) load(model)
            val initializationMillis = if (coldStart) elapsedMillis(loadStarted) else 0L
            val current = checkNotNull(loaded)
            val generationStarted = System.nanoTime()
            val response = current.engine.createConversation().use { conversation ->
                suspendCancellableCoroutine<String> { continuation ->
                    val output = StringBuilder()
                    conversation.sendMessageAsync(
                        Contents.of(prompt),
                        object : MessageCallback {
                            override fun onMessage(message: Message) { output.append(message.toString()) }
                            override fun onDone() { if (continuation.isActive) continuation.resume(output.toString()) }
                            override fun onError(throwable: Throwable) {
                                if (continuation.isActive) continuation.resumeWithException(throwable)
                            }
                        },
                    )
                    continuation.invokeOnCancellation { runCatching { conversation.cancelProcess() } }
                }
            }
            GenerationResult(response.trim(), initializationMillis, elapsedMillis(generationStarted), coldStart)
        }
    }

    suspend fun unload() = withContext(Dispatchers.IO) { mutex.withLock { closeLoaded() } }

    private fun load(model: RegisteredModel) {
        closeLoaded()
        check(File(model.localPath).isFile) { "Service-owned model file is missing." }
        var gpuFailure: Throwable? = null
        val gpu = Engine(EngineConfig(modelPath = model.localPath, backend = Backend.GPU(), cacheDir = context.cacheDir.absolutePath))
        try {
            gpu.initialize()
            loaded = LoadedModel(model.uri, gpu)
            return
        } catch (failure: Throwable) {
            gpuFailure = failure
            runCatching { gpu.close() }
        }
        val cpu = Engine(EngineConfig(modelPath = model.localPath, backend = Backend.CPU(), cacheDir = context.cacheDir.absolutePath))
        try {
            cpu.initialize()
            loaded = LoadedModel(model.uri, cpu)
        } catch (cpuFailure: Throwable) {
            runCatching { cpu.close() }
            throw IllegalStateException(
                "LiteRT-LM failed on GPU and CPU. GPU: ${gpuFailure?.message}; CPU: ${cpuFailure.message}",
                cpuFailure,
            )
        }
    }

    private fun closeLoaded() {
        val current = loaded ?: return
        loaded = null
        runCatching { current.engine.close() }
    }

    override fun close() { closeLoaded() }
}

private fun elapsedMillis(startedAtNanos: Long): Long = (System.nanoTime() - startedAtNanos) / 1_000_000
