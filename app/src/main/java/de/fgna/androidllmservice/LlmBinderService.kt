package de.fgna.androidllmservice

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LlmBinderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val modelStore by lazy { ModelStore(this) }
    private val runtime by lazy { LlmRuntimeProvider.get(this) }

    private val binder = object : ILlmService.Stub() {
        override fun isModelReady(): Boolean = modelStore.current() != null

        override fun getActiveModelName(): String = modelStore.current()?.displayName.orEmpty()

        override fun generate(prompt: String?, callback: ILlmCallback?) {
            if (callback == null) return
            val cleanPrompt = prompt?.trim().orEmpty()
            if (cleanPrompt.isBlank()) {
                safeError(callback, "INVALID_REQUEST", "Prompt must not be blank.")
                return
            }
            val model = modelStore.current()
            if (model == null) {
                safeError(callback, "MODEL_NOT_READY", "No model is imported in Android LLM Service.")
                return
            }

            scope.launch {
                runCatching { runtime.generate(model, cleanPrompt) }
                    .onSuccess { result -> safeSuccess(callback, result) }
                    .onFailure { failure ->
                        safeError(callback, "INFERENCE_FAILED", failure.message ?: failure::class.java.simpleName)
                    }
            }
        }

        override fun generateWithImage(
            prompt: String?,
            image: ParcelFileDescriptor?,
            callback: ILlmCallback?,
        ) {
            if (callback == null) {
                image?.close()
                return
            }
            val cleanPrompt = prompt?.trim().orEmpty()
            if (cleanPrompt.isBlank() || image == null) {
                image?.close()
                safeError(callback, "INVALID_REQUEST", "Prompt and image are required.")
                return
            }
            val model = modelStore.current()
            if (model == null) {
                image.close()
                safeError(callback, "MODEL_NOT_READY", "No model is imported in Android LLM Service.")
                return
            }

            scope.launch(Dispatchers.IO) {
                val tempImage = File.createTempFile("binder-image-", ".jpg", cacheDir)
                try {
                    FileInputStream(image.fileDescriptor).use { input ->
                        tempImage.outputStream().use { output -> input.copyTo(output) }
                    }
                    check(tempImage.length() > 0L) { "Image payload is empty." }
                    val result = runtime.generateWithImage(model, cleanPrompt, tempImage.absolutePath)
                    safeSuccess(callback, result)
                } catch (failure: Throwable) {
                    safeError(callback, "INFERENCE_FAILED", failure.message ?: failure::class.java.simpleName)
                } finally {
                    runCatching { image.close() }
                    tempImage.delete()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun safeSuccess(callback: ILlmCallback, result: GenerationResult) {
        try {
            callback.onSuccess(
                result.text,
                result.initializationMillis,
                result.generationMillis,
                result.coldStart,
            )
        } catch (_: RemoteException) {
            // Client disconnected. Runtime state remains valid for future clients.
        }
    }

    private fun safeError(callback: ILlmCallback, code: String, message: String) {
        try {
            callback.onError(code, message)
        } catch (_: RemoteException) {
            // Client disconnected before receiving the error.
        }
    }
}
