package de.fgna.androidllmservice

import android.content.Context
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ModelDiagnosticResult(
    val declaredSizeBytes: Long?,
    val descriptorSizeBytes: Long,
    val firstBytesHex: String,
    val firstMiBSha256: String,
    val procFdReadable: Boolean,
    val procFdFirstBytesMatch: Boolean,
)

internal class ModelDiagnostics(private val context: Context) {
    suspend fun inspect(model: RegisteredModel): ModelDiagnosticResult = withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(model.uri, "r")
            ?: error("Model file is no longer accessible.")
        descriptor.use { pfd ->
            val uriPrefix = FileInputStream(pfd.fileDescriptor).use { input -> readPrefix(input) }
            val procPath = "/proc/self/fd/${pfd.fd}"
            val procPrefix = runCatching {
                FileInputStream(procPath).use { input -> readPrefix(input) }
            }.getOrNull()

            ModelDiagnosticResult(
                declaredSizeBytes = model.sizeBytes,
                descriptorSizeBytes = pfd.statSize,
                firstBytesHex = uriPrefix.take(32).joinToString(" ") { "%02x".format(it.toInt() and 0xff) },
                firstMiBSha256 = sha256(uriPrefix),
                procFdReadable = procPrefix != null,
                procFdFirstBytesMatch = procPrefix?.contentEquals(uriPrefix) == true,
            )
        }
    }

    private fun readPrefix(input: FileInputStream): ByteArray {
        val limit = 1024 * 1024
        val output = ByteArray(limit)
        var offset = 0
        while (offset < limit) {
            val count = input.read(output, offset, limit - offset)
            if (count <= 0) break
            offset += count
        }
        return output.copyOf(offset)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
