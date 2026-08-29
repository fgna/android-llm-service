package de.fgna.androidllmservice

internal data class GenerationResult(
    val text: String,
    val initializationMillis: Long,
    val generationMillis: Long,
    val coldStart: Boolean,
    val providerId: String,
    val modelName: String,
)

internal data class ProviderProfile(
    val id: String,
    val label: String,
    val modelName: String,
    val ready: Boolean,
    val supportsText: Boolean = true,
    val supportsImage: Boolean = false,
)

internal interface InferenceProvider {
    val id: String

    fun profile(): ProviderProfile

    suspend fun generate(prompt: String): GenerationResult

    suspend fun generateWithImage(prompt: String, imagePath: String): GenerationResult =
        throw UnsupportedOperationException("Provider '$id' does not support image input.")
}
