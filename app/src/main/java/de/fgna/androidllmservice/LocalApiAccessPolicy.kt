package de.fgna.androidllmservice

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object LocalApiAccessPolicy {
    fun isAuthorized(config: LocalApiConfig, authorizationHeader: String?): Boolean {
        if (!config.enabled || !config.isValid) return false
        if (!config.exposesLan) return true

        val presentedToken = bearerToken(authorizationHeader) ?: return false
        return MessageDigest.isEqual(
            config.authToken.toByteArray(StandardCharsets.UTF_8),
            presentedToken.toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun bearerToken(header: String?): String? {
        val value = header?.trim().orEmpty()
        val separator = value.indexOf(' ')
        if (separator <= 0 || !value.substring(0, separator).equals("Bearer", ignoreCase = true)) {
            return null
        }
        return value.substring(separator + 1).trim().takeIf(String::isNotEmpty)
    }
}
