package de.fgna.androidllmservice

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class LanInferenceProvider(context: Context) : InferenceProvider {
    override val id: String = "lan"
    private val preferences = context.getSharedPreferences("lan-provider", Context.MODE_PRIVATE)

    override fun profile(): ProviderProfile {
        val baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty().trim()
        val model = preferences.getString(KEY_MODEL, "").orEmpty().trim()
        return ProviderProfile(
            id = id,
            label = "LAN",
            modelName = model,
            ready = baseUrl.isNotBlank() && model.isNotBlank(),
            supportsImage = false,
        )
    }

    fun configure(baseUrl: String, model: String) {
        preferences.edit()
            .putString(KEY_BASE_URL, baseUrl.trim())
            .putString(KEY_MODEL, model.trim())
            .apply()
    }

    override suspend fun generate(prompt: String): GenerationResult = withContext(Dispatchers.IO) {
        val baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty().trim()
        val model = preferences.getString(KEY_MODEL, "").orEmpty().trim()
        check(baseUrl.isNotBlank()) { "LAN provider base URL is not configured." }
        check(model.isNotBlank()) { "LAN provider model is not configured." }

        val request = JSONObject()
            .put("model", model)
            .put("stream", false)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", prompt)),
            )

        val started = System.nanoTime()
        val connection = (URL(chatCompletionsUrl(baseUrl)).openConnection() as HttpURLConnection).apply {
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
            check(status in 200..299) { "LAN provider returned HTTP $status${responseBody.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}" }

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
                modelName = model,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun chatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return when {
            trimmed.endsWith("/v1/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000

    private companion object {
        const val KEY_BASE_URL = "base-url"
        const val KEY_MODEL = "model"
    }
}
