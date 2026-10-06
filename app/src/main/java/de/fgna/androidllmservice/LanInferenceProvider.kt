package de.fgna.androidllmservice

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun classifyLanHttpFailure(responseBody: String): String {
    val message = responseBody.lowercase()
    val modelOrCapabilityMismatch = listOf(
        "model not found",
        "unknown model",
        "unsupported model",
        "does not support",
        "unsupported capability",
        "capability mismatch",
    ).any(message::contains)
    return if (modelOrCapabilityMismatch) {
        ProviderErrorCodes.MODEL_CAPABILITY_MISMATCH
    } else {
        ProviderErrorCodes.GENERATION_FAILURE
    }
}

internal data class LanHttpResponse(val status: Int, val body: String)

internal suspend fun readLanResponse(
    connection: HttpURLConnection,
    requestBody: String? = null,
): LanHttpResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { connection.disconnect() }
    try {
        if (requestBody != null) {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(requestBody) }
        }
        val status = connection.responseCode
        val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        if (continuation.isActive) continuation.resume(LanHttpResponse(status, body))
    } catch (failure: Throwable) {
        if (continuation.isActive) continuation.resumeWithException(failure)
    } finally {
        connection.disconnect()
    }
}

internal class LanInferenceProvider(context: Context) : InferenceProvider {
    override val id: String = ProviderIds.LAN
    private val preferences = context.getSharedPreferences("lan-provider", Context.MODE_PRIVATE)

    override fun profile(): ProviderProfile {
        val config = currentConfig()
        return ProviderProfile(
            id = id,
            label = "LAN",
            modelName = config.normalizedModel,
            ready = config.isValid,
            supportsImage = false,
        )
    }

    fun configure(baseUrl: String, model: String) {
        val config = LanProviderConfig(baseUrl, model.ifBlank { AUTO_MODEL })
        require(config.isValid) { "LAN provider requires an http(s) base URL and non-blank model." }
        preferences.edit()
            .putString(KEY_BASE_URL, config.normalizedBaseUrl)
            .putString(KEY_MODEL, config.normalizedModel)
            .apply()
    }

    override suspend fun generate(prompt: String): GenerationResult = withContext(Dispatchers.IO) {
        try {
            val config = currentConfig()
        if (!config.isValid) {
            throw ProviderException(
                ProviderErrorCodes.PROVIDER_NOT_READY,
                "LAN provider is not configured with a valid http(s) base URL and model.",
            )
        }
        val resolvedModel = resolveModel(config)

        val request = JSONObject()
            .put("model", resolvedModel)
            .put("stream", false)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", prompt)),
            )

        val started = System.nanoTime()
        val connection = (URL(chatCompletionsUrl(config.normalizedBaseUrl)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        val (status, responseBody) = readLanResponse(connection, request.toString())
        if (status !in 200..299) {
                throw ProviderException(
                    classifyLanHttpFailure(responseBody),
                    "LAN provider returned HTTP $status${responseBody.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}",
                )
            }

            val text = JSONObject(responseBody)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

        GenerationResult(
            text = text,
            initializationMillis = 0L,
            generationMillis = elapsedMillis(started),
            coldStart = false,
            providerId = id,
            modelName = resolvedModel,
        )
        } catch (failure: IOException) {
            throw ProviderException(
                ProviderErrorCodes.NETWORK_FAILURE,
                failure.message ?: "LAN provider network request failed.",
                failure,
            )
        }
    }

    private suspend fun resolveModel(config: LanProviderConfig): String {
        if (!config.normalizedModel.equals(AUTO_MODEL, ignoreCase = true)) return config.normalizedModel

        val connection = (URL(modelsUrl(config.normalizedBaseUrl)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Accept", "application/json")
        }
        val (status, responseBody) = readLanResponse(connection)
        if (status !in 200..299) {
                throw ProviderException(
                    ProviderErrorCodes.GENERATION_FAILURE,
                    "LAN model discovery returned HTTP $status",
                )
            }
            val models = JSONObject(responseBody).optJSONArray("data")
            val model = models?.optJSONObject(0)?.optString("id").orEmpty().trim()
            if (model.isBlank()) {
                throw ProviderException(
                    ProviderErrorCodes.MODEL_CAPABILITY_MISMATCH,
                    "LAN model discovery returned no models.",
                )
            }
        return model
    }

    private fun currentConfig(): LanProviderConfig {
        // Editor.apply() publishes a complete in-memory update. Reading getAll() once
        // gives generation and profile calls one matching endpoint/model snapshot.
        val snapshot = preferences.all
        return LanProviderConfig(
            baseUrl = snapshot[KEY_BASE_URL] as? String ?: "",
            model = snapshot[KEY_MODEL] as? String ?: "",
        )
    }

    private fun chatCompletionsUrl(baseUrl: String): String = when {
        baseUrl.endsWith("/v1/chat/completions") -> baseUrl
        baseUrl.endsWith("/v1") -> "$baseUrl/chat/completions"
        else -> "$baseUrl/v1/chat/completions"
    }

    private fun modelsUrl(baseUrl: String): String = when {
        baseUrl.endsWith("/v1/chat/completions") -> baseUrl.removeSuffix("/chat/completions") + "/models"
        baseUrl.endsWith("/v1") -> "$baseUrl/models"
        else -> "$baseUrl/v1/models"
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000

    private companion object {
        const val KEY_BASE_URL = "base-url"
        const val KEY_MODEL = "model"
        const val AUTO_MODEL = "auto"
    }
}
