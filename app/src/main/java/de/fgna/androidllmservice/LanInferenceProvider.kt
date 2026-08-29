package de.fgna.androidllmservice

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
        val config = LanProviderConfig(baseUrl, model)
        require(config.isValid) { "LAN provider requires an http(s) base URL and non-blank model." }
        preferences.edit()
            .putString(KEY_BASE_URL, config.normalizedBaseUrl)
            .putString(KEY_MODEL, config.normalizedModel)
            .apply()
    }

    override suspend fun generate(prompt: String): GenerationResult = withContext(Dispatchers.IO) {
        val config = currentConfig()
        check(config.isValid) { "LAN provider is not configured with a valid http(s) base URL and model." }

        val request = JSONObject()
            .put("model", config.normalizedModel)
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

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(request.toString()) }
            val status = connection.responseCode
            val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            check(status in 200..299) {
                "LAN provider returned HTTP $status${responseBody.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
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
                modelName = config.normalizedModel,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun currentConfig() = LanProviderConfig(
        baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
        model = preferences.getString(KEY_MODEL, "").orEmpty(),
    )

    private fun chatCompletionsUrl(baseUrl: String): String = when {
        baseUrl.endsWith("/v1/chat/completions") -> baseUrl
        baseUrl.endsWith("/v1") -> "$baseUrl/chat/completions"
        else -> "$baseUrl/v1/chat/completions"
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000

    private companion object {
        const val KEY_BASE_URL = "base-url"
        const val KEY_MODEL = "model"
    }
}
