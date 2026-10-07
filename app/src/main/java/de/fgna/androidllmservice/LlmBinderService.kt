package de.fgna.androidllmservice

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private data class RequestKey(val callerUid: Int, val requestId: String)
private data class RequestCallbacks(
    val onSuccess: (GenerationResult) -> Unit,
    val onError: (String, String) -> Unit,
)
private data class ActiveRequest(val job: Job, val callbacks: RequestCallbacks)

class LlmBinderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val modelStore by lazy { ModelStore(this) }
    private val providers by lazy { ProviderRegistry(this, modelStore) }
    private val requests = ConcurrentHashMap<RequestKey, ActiveRequest>()

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
            generateWithRequest(UUID.randomUUID().toString(), profileId, prompt, callback)
        }

        override fun generateWithRequest(
            requestId: String?,
            profileId: String?,
            prompt: String?,
            callback: ILlmCallback?,
        ) {
            if (callback == null) return
            startRequest(
                callerUid = Binder.getCallingUid(),
                requestId = requestId,
                profileId = profileId,
                prompt = prompt,
                callbacks = RequestCallbacks(
                    onSuccess = { result -> safeSuccess(callback, result) },
                    onError = { code, message -> safeError(callback, code, message) },
                ),
            )
        }

        override fun generateWithRequestMetadata(
            requestId: String?,
            profileId: String?,
            prompt: String?,
            callback: ILlmResultCallback?,
        ) {
            if (callback == null) return
            startRequest(
                callerUid = Binder.getCallingUid(),
                requestId = requestId,
                profileId = profileId,
                prompt = prompt,
                callbacks = RequestCallbacks(
                    onSuccess = { result -> safeMetadataSuccess(callback, result) },
                    onError = { code, message -> safeError(callback, code, message) },
                ),
            )
        }

        override fun cancelRequest(requestId: String?): Boolean {
            val cleanRequestId = requestId?.trim().orEmpty()
            if (cleanRequestId.isBlank()) return false
            val requestKey = RequestKey(Binder.getCallingUid(), cleanRequestId)
            val active = requests.remove(requestKey) ?: return false
            active.job.cancel(CancellationException("Cancelled by Binder client."))
            active.callbacks.onError(
                ProviderErrorCodes.REQUEST_CANCELLED,
                "Request '$cleanRequestId' was cancelled.",
            )
            return true
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
                safeError(callback, ProviderErrorCodes.PROVIDER_NOT_READY, "On-device provider is not ready.")
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
                    val error = failure.toProviderError()
                    safeError(callback, error.code, error.message)
                } finally {
                    runCatching { image.close() }
                    tempImage.delete()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        requests.forEach { (requestKey, active) ->
            if (requests.remove(requestKey, active)) {
                active.job.cancel(CancellationException("Service destroyed."))
                active.callbacks.onError(
                    ProviderErrorCodes.REQUEST_CANCELLED,
                    "Request '\${requestKey.requestId}' was cancelled because the service stopped.",
                )
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun startRequest(
        callerUid: Int,
        requestId: String?,
        profileId: String?,
        prompt: String?,
        callbacks: RequestCallbacks,
    ) {
        val cleanRequestId = requestId?.trim().orEmpty()
        val requestKey = RequestKey(callerUid, cleanRequestId)
        val cleanPrompt = prompt?.trim().orEmpty()
        if (cleanRequestId.isBlank() || cleanPrompt.isBlank()) {
            callbacks.onError("INVALID_REQUEST", "Request ID and prompt must not be blank.")
            return
        }

        val provider = runCatching { providers.provider(profileId.orEmpty()) }
            .getOrElse { failure ->
                callbacks.onError(
                    ProviderErrorCodes.UNKNOWN_PROVIDER,
                    failure.message ?: "Unknown provider profile.",
                )
                return
            }
        if (!provider.profile().ready) {
            callbacks.onError(
                ProviderErrorCodes.PROVIDER_NOT_READY,
                "Provider '\${provider.id}' is not configured or ready.",
            )
            return
        }

        lateinit var active: ActiveRequest
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = provider.generate(cleanPrompt)
                if (requests.remove(requestKey, active)) {
                    callbacks.onSuccess(result)
                }
            } catch (_: CancellationException) {
                // cancelRequest owns the single terminal cancellation callback.
            } catch (failure: Throwable) {
                if (requests.remove(requestKey, active)) {
                    val error = failure.toProviderError()
                    callbacks.onError(error.code, error.message)
                }
            } finally {
                requests.remove(requestKey, active)
            }
        }
        active = ActiveRequest(job, callbacks)
        if (requests.putIfAbsent(requestKey, active) != null) {
            job.cancel()
            callbacks.onError(
                ProviderErrorCodes.REQUEST_ALREADY_ACTIVE,
                "Request ID '$cleanRequestId' is already active.",
            )
            return
        }
        job.start()
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

    private fun safeMetadataSuccess(callback: ILlmResultCallback, result: GenerationResult) {
        try {
            callback.onSuccess(
                result.text,
                result.initializationMillis,
                result.generationMillis,
                result.coldStart,
                result.providerId,
                result.modelName,
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

    private fun safeError(callback: ILlmResultCallback, code: String, message: String) {
        try {
            callback.onError(code, message)
        } catch (_: RemoteException) {
            // Client disconnected before receiving the error.
        }
    }
}
