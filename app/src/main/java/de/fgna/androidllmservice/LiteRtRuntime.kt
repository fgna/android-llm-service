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
    private data class LoadedModel(val uri: Uri, val engine: Engine, val backend: String)
    private val mutex = Mutex()
    private var loaded: LoadedModel? = null

    suspend fun generate(model: RegisteredModel, prompt: String): GenerationResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loadStarted = System.nanoTime()
            val coldStart = loaded?.uri != model.uri
            if (coldStart) loadPreferredBackend(model)
            val initializationMillis = if (coldStart) elapsedMillis(loadStarted) else 0L
            var current = checkNotNull(loaded)
            val generationStarted = System.nanoTime()
            val response = try {
                runConversation(current, prompt)
            } catch (gpuFailure: Throwable) {
                if (current.backend != "GPU") throw gpuFailure
                closeLoaded()
                current = loadBackend(model, Backend.CPU(), "CPU")
                loaded = current
                runConversation(current, prompt)
            }
            GenerationResult(response.trim(), initializationMillis, elapsedMillis(generationStarted), coldStart)
        }
    }

    private suspend fun runConversation(model: LoadedModel, prompt: String): String {
        val conversation = model.engine.createConversation()
        try {
            return suspendCancellableCoroutine { continuation ->
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
        } finally {
            conversation.close()
        }
    }

    suspend fun unload() = withContext(Dispatchers.IO) { mutex.withLock { closeLoaded() } }

    private fun loadPreferredBackend(model: RegisteredModel) {
        closeLoaded()
        check(File(model.localPath).isFile) { "Service-owned model file is missing." }
        val gpu = runCatching { loadBackend(model, Backend.GPU(), "GPU") }
        loaded = gpu.getOrElse { gpuFailure ->
            runCatching { loadBackend(model, Backend.CPU(), "CPU") }.getOrElse { cpuFailure ->
                throw IllegalStateException(
                    "LiteRT-LM failed on GPU and CPU. GPU: ${gpuFailure.message}; CPU: ${cpuFailure.message}",
                    cpuFailure,
                )
            }
        }
    }

    private fun loadBackend(model: RegisteredModel, backend: Backend, name: String): LoadedModel {
        val engine = Engine(
            EngineConfig(
                modelPath = model.localPath,
                backend = backend,
                maxNumTokens = 8192,
            ),
        )
        try {
            engine.initialize()
            return LoadedModel(model.uri, engine, name)
        } catch (failure: Throwable) {
            runCatching { engine.close() }
            throw failure
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
