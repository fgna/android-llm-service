package de.fgna.androidllmservice

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
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
                    .onSuccess { result ->
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
                    .onFailure { failure ->
                        safeError(
                            callback,
                            "INFERENCE_FAILED",
                            failure.message ?: failure::class.java.simpleName,
                        )
                    }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun safeError(callback: ILlmCallback, code: String, message: String) {
        try {
            callback.onError(code, message)
        } catch (_: RemoteException) {
            // Client disconnected before receiving the error.
        }
    }
}
