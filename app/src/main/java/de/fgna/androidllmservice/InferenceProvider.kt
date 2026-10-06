package de.fgna.androidllmservice

internal object ProviderIds {
    const val ON_DEVICE = "on-device"
    const val LAN = "lan"
}

internal data class LanProviderConfig(
    val baseUrl: String,
    val model: String,
) {
    val normalizedBaseUrl: String = baseUrl.trim().trimEnd('/')
    val normalizedModel: String = model.trim()

    val isValid: Boolean
        get() = normalizedModel.isNotBlank() &&
            (normalizedBaseUrl.startsWith("http://") || normalizedBaseUrl.startsWith("https://"))
}

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

internal object ProviderErrorCodes {
    const val UNKNOWN_PROVIDER = "UNKNOWN_PROVIDER"
    const val PROVIDER_NOT_READY = "PROVIDER_NOT_READY"
    const val NETWORK_FAILURE = "NETWORK_FAILURE"
    const val MODEL_CAPABILITY_MISMATCH = "MODEL_CAPABILITY_MISMATCH"
    const val GENERATION_FAILURE = "GENERATION_FAILURE"
    const val REQUEST_CANCELLED = "REQUEST_CANCELLED"
    const val REQUEST_ALREADY_ACTIVE = "REQUEST_ALREADY_ACTIVE"
}

internal data class ProviderError(val code: String, val message: String)

internal class ProviderException(
    val errorCode: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal fun Throwable.toProviderError(): ProviderError = when (this) {
    is ProviderException -> ProviderError(errorCode, message ?: errorCode)
    is UnsupportedOperationException -> ProviderError(
        ProviderErrorCodes.MODEL_CAPABILITY_MISMATCH,
        message ?: "Provider does not support the requested capability.",
    )
    else -> ProviderError(
        ProviderErrorCodes.GENERATION_FAILURE,
        message ?: this::class.java.simpleName,
    )
}

internal interface InferenceProvider {
    val id: String

    fun profile(): ProviderProfile

    suspend fun generate(prompt: String): GenerationResult

    suspend fun generateWithImage(prompt: String, imagePath: String): GenerationResult =
        throw ProviderException(
            ProviderErrorCodes.MODEL_CAPABILITY_MISMATCH,
            "Provider '$id' does not support image input.",
        )
}
