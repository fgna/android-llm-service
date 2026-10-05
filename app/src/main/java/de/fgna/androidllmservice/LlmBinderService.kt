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
    private val providers by lazy { ProviderRegistry(this, modelStore) }

    private val binder = object : ILlmService.Stub() {
        override fun isModelReady(): Boolean = modelStore.current() != null

        override fun getActiveModelName(): String = modelStore.current()?.displayName.orEmpty()

        override fun getProviderProfilesJson(): String = providers.profilesJson()

        override fun configureLanProvider(baseUrl: String?, model: String?) {
            providers.configureLan(baseUrl.orEmpty(), model.orEmpty())
        }

        override fun generate(prompt: String?, callback: ILlmCallback?) {
            generateWithProfile("on-device", prompt, callback)
        }

        override fun generateWithProfile(
            profileId: String?,
            prompt: String?,
            callback: ILlmCallback?,
        ) {
            if (callback == null) return
            val cleanPrompt = prompt?.trim().orEmpty()
            if (cleanPrompt.isBlank()) {
                safeError(callback, "INVALID_REQUEST", "Prompt must not be blank.")
                return
            }

            val provider = runCatching { providers.provider(profileId.orEmpty()) }
                .getOrElse { failure ->
                    safeError(callback, "UNKNOWN_PROVIDER", failure.message ?: "Unknown provider profile.")
                    return
                }
            if (!provider.profile().ready) {
                safeError(callback, "PROVIDER_NOT_READY", "Provider '${provider.id}' is not configured or ready.")
                return
            }

            scope.launch {
                runCatching { provider.generate(cleanPrompt) }
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
            val provider = providers.provider("on-device")
            if (!provider.profile().ready) {
                image.close()
                safeError(callback, "PROVIDER_NOT_READY", "On-device provider is not ready.")
                return
            }

            scope.launch(Dispatchers.IO) {
                val tempImage = File.createTempFile("binder-image-", ".jpg", cacheDir)
                try {
                    FileInputStream(image.fileDescriptor).use { input ->
                        tempImage.outputStream().use { output -> input.copyTo(output) }
                    }
                    check(tempImage.length() > 0L) { "Image payload is empty." }
                    val result = provider.generateWithImage(cleanPrompt, tempImage.absolutePath)
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
            // Client disconnected. Provider state remains valid for future clients.
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
