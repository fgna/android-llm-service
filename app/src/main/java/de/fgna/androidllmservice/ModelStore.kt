package de.fgna.androidllmservice

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

internal data class RegisteredModel(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    val localPath: String,
)

internal class ModelStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("model_store", Context.MODE_PRIVATE)
    private val modelFile = File(context.noBackupFilesDir, "models/model.litertlm")

    fun register(uri: Uri): RegisteredModel {
        val source = inspectSource(uri)
        modelFile.parentFile?.mkdirs()
        val temporary = File(modelFile.parentFile, "model.litertlm.part")
        temporary.delete()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Model file is not accessible." }
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        check(temporary.length() > 0L) { "Model file is empty." }
        source.sizeBytes?.let { expected ->
            check(temporary.length() == expected) {
                "Imported model size differs from source: ${temporary.length()} != $expected"
            }
        }
        modelFile.delete()
        check(temporary.renameTo(modelFile)) { "Imported model could not be activated." }
        preferences.edit().putString(KEY_NAME, source.displayName).apply()
        return RegisteredModel(Uri.fromFile(modelFile), source.displayName, modelFile.length(), modelFile.absolutePath)
    }

    fun current(): RegisteredModel? {
        if (!modelFile.isFile || modelFile.length() <= 0L) return null
        val name = preferences.getString(KEY_NAME, null) ?: "model.litertlm"
        return RegisteredModel(Uri.fromFile(modelFile), name, modelFile.length(), modelFile.absolutePath)
    }

    fun clear() {
        File(modelFile.parentFile, "model.litertlm.part").delete()
        modelFile.delete()
        preferences.edit().clear().apply()
    }

    private fun inspectSource(uri: Uri): SourceModel {
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
        require(displayName.endsWith(".litertlm", ignoreCase = true)) { "Please select a .litertlm model file." }
        return SourceModel(displayName, size)
    }

    private data class SourceModel(val displayName: String, val sizeBytes: Long?)

    private companion object { const val KEY_NAME = "model_name" }
}
