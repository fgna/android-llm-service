package de.fgna.androidllmservice

internal data class LocalApiConfig(
    val enabled: Boolean = false,
    val bindAddress: String = DEFAULT_BIND_ADDRESS,
    val port: Int = DEFAULT_PORT,
    val authToken: String = "",
) {
    val normalizedBindAddress: String = bindAddress.trim().lowercase()
    val exposesLan: Boolean = normalizedBindAddress !in LOOPBACK_ADDRESSES

    val isValid: Boolean
        get() = !enabled || (
            normalizedBindAddress.isNotBlank() &&
                port in 1..65535 &&
                (!exposesLan || authToken.isNotBlank())
            )

    companion object {
        const val DEFAULT_BIND_ADDRESS = "127.0.0.1"
        const val DEFAULT_PORT = 11435

        private val LOOPBACK_ADDRESSES = setOf(
            DEFAULT_BIND_ADDRESS,
            "::1",
            "localhost",
        )
    }
}
