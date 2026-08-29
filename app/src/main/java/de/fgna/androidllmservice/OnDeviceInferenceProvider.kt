package de.fgna.androidllmservice

import android.content.Context

internal class OnDeviceInferenceProvider(
    context: Context,
    private val modelStore: ModelStore,
) : InferenceProvider {
    override val id: String = "on-device"
    private val runtime = LlmRuntimeProvider.get(context)

    override fun profile(): ProviderProfile {
        val model = modelStore.current()
        return ProviderProfile(
            id = id,
            label = "On-device",
            modelName = model?.displayName.orEmpty(),
            ready = model != null,
            supportsImage = true,
        )
    }

    override suspend fun generate(prompt: String): GenerationResult {
        val model = requireModel()
        return runtime.generate(model, prompt)
    }

    override suspend fun generateWithImage(prompt: String, imagePath: String): GenerationResult {
        val model = requireModel()
        return runtime.generateWithImage(model, prompt, imagePath)
    }

    private fun requireModel(): RegisteredModel =
        modelStore.current() ?: throw IllegalStateException("No model is imported in Android LLM Service.")
}
