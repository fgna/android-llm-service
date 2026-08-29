package de.fgna.androidllmservice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class ProviderRegistry(context: Context, modelStore: ModelStore) {
    private val onDevice = OnDeviceInferenceProvider(context, modelStore)
    private val lan = LanInferenceProvider(context)

    fun provider(id: String): InferenceProvider = when (id) {
        onDevice.id -> onDevice
        lan.id -> lan
        else -> throw IllegalArgumentException("Unknown provider profile '$id'.")
    }

    fun configureLan(baseUrl: String, model: String) {
        lan.configure(baseUrl, model)
    }

    fun profilesJson(): String {
        val array = JSONArray()
        listOf(onDevice.profile(), lan.profile()).forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("label", profile.label)
                    .put("modelName", profile.modelName)
                    .put("ready", profile.ready)
                    .put("supportsText", profile.supportsText)
                    .put("supportsImage", profile.supportsImage),
            )
        }
        return array.toString()
    }
}
