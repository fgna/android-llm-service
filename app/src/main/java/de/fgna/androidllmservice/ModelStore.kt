package de.fgna.androidllmservice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

internal data class RegisteredModel(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
)

internal class ModelStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("model_store", Context.MODE_PRIVATE)

    fun register(uri: Uri): RegisteredModel {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val model = inspect(uri)
        preferences.edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_NAME, model.displayName)
            .apply()
        return model
    }

    fun current(): RegisteredModel? {
        val uri = preferences.getString(KEY_URI, null)?.let(Uri::parse) ?: return null
        return runCatching { inspect(uri) }.getOrNull()
    }

    fun clear() {
        val uri = preferences.getString(KEY_URI, null)?.let(Uri::parse)
        if (uri != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        preferences.edit().clear().apply()
    }

    private fun inspect(uri: Uri): RegisteredModel {
        context.contentResolver.openFileDescriptor(uri, "r").use { descriptor ->
            requireNotNull(descriptor) { "Model file is not accessible." }
        }

        var name: String? = null
        var size: Long? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }

        val displayName = name ?: uri.lastPathSegment ?: "model.litertlm"
        require(displayName.endsWith(".litertlm", ignoreCase = true)) {
            "Please select a .litertlm model file."
        }
        return RegisteredModel(uri = uri, displayName = displayName, sizeBytes = size)
    }

    private companion object {
        const val KEY_URI = "model_uri"
        const val KEY_NAME = "model_name"
    }
}
