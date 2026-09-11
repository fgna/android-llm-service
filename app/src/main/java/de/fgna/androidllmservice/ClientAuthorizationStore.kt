package de.fgna.androidllmservice

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

data class ClientAuthorization(
    val packageName: String,
    val label: String,
    val certificateSha256: String,
    val approved: Boolean,
    val sameSigner: Boolean,
)

class ClientAuthorizationStore(private val context: Context) {
    companion object {
        const val CLIENT_PERMISSION = "de.fgna.androidllmservice.permission.BIND_LLM_SERVICE"

        private const val PREFS_NAME = "client_authorizations"
        private const val KEY_APPROVED_CLIENTS = "approved_clients"
    }

    private val packageManager = context.packageManager
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun clients(): List<ClientAuthorization> {
        val approved = approvedEntries()
        return installedPackages()
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { requestsClientAccess(it) }
            .mapNotNull { info ->
                val fingerprint = signingFingerprint(info) ?: return@mapNotNull null
                val sameSigner = packageManager.checkSignatures(
                    context.packageName,
                    info.packageName,
                ) == PackageManager.SIGNATURE_MATCH
                val label = runCatching {
                    packageManager.getApplicationLabel(info.applicationInfo!!).toString()
                }.getOrDefault(info.packageName)

                ClientAuthorization(
                    packageName = info.packageName,
                    label = label,
                    certificateSha256 = fingerprint,
                    approved = sameSigner || clientKey(info.packageName, fingerprint) in approved,
                    sameSigner = sameSigner,
                )
            }
            .sortedWith(compareBy<ClientAuthorization> { !it.approved }.thenBy { it.label.lowercase() })
            .toList()
    }

    fun setApproved(packageName: String, approved: Boolean) {
        val info = getPackageInfo(packageName) ?: return
        if (!requestsClientAccess(info)) return
        val fingerprint = signingFingerprint(info) ?: return
        val key = clientKey(packageName, fingerprint)
        val updated = approvedEntries().toMutableSet().apply {
            if (approved) add(key) else remove(key)
        }
        preferences.edit().putStringSet(KEY_APPROVED_CLIENTS, updated).apply()
    }

    fun isUidAuthorized(uid: Int): Boolean {
        return packageManager.getPackagesForUid(uid).orEmpty().any { packageName ->
            if (packageName == context.packageName) return@any true
            if (packageManager.checkSignatures(context.packageName, packageName) == PackageManager.SIGNATURE_MATCH) {
                return@any true
            }

            val info = getPackageInfo(packageName) ?: return@any false
            if (!requestsClientAccess(info)) return@any false
            val fingerprint = signingFingerprint(info) ?: return@any false
            clientKey(packageName, fingerprint) in approvedEntries()
        }
    }

    private fun approvedEntries(): Set<String> =
        preferences.getStringSet(KEY_APPROVED_CLIENTS, emptySet())?.toSet().orEmpty()

    private fun clientKey(packageName: String, fingerprint: String): String = "$packageName|$fingerprint"

    private fun requestsClientAccess(info: PackageInfo): Boolean =
        info.requestedPermissions?.contains(CLIENT_PERMISSION) == true

    @Suppress("DEPRECATION")
    private fun installedPackages(): List<PackageInfo> {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getInstalledPackages(flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(packageName: String): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                packageManager.getPackageInfo(packageName, flags)
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun signingFingerprint(info: PackageInfo): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
        if (signatures.isEmpty()) return null

        return signatures
            .map { signature -> sha256(signature.toByteArray()) }
            .sorted()
            .joinToString(",")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02X".format(byte) }
}
