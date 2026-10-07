package de.fgna.androidllmservice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalApiConfigTest {
    @Test
    fun apiIsDisabledAndLoopbackOnlyByDefault() {
        val config = LocalApiConfig()

        assertFalse(config.enabled)
        assertFalse(config.exposesLan)
        assertTrue(config.isValid)
    }

    @Test
    fun enabledLoopbackApiDoesNotRequireLanCredential() {
        assertTrue(
            LocalApiConfig(
                enabled = true,
                bindAddress = "localhost",
            ).isValid,
        )
        assertTrue(
            LocalApiConfig(
                enabled = true,
                bindAddress = "::1",
            ).isValid,
        )
    }

    @Test
    fun lanExposureRequiresAuthentication() {
        assertFalse(
            LocalApiConfig(
                enabled = true,
                bindAddress = "0.0.0.0",
            ).isValid,
        )
        assertTrue(
            LocalApiConfig(
                enabled = true,
                bindAddress = "0.0.0.0",
                authToken = "explicit-secret",
            ).isValid,
        )
    }

    @Test
    fun enabledApiRequiresUsableAddressAndPort() {
        assertFalse(LocalApiConfig(enabled = true, bindAddress = "").isValid)
        assertFalse(LocalApiConfig(enabled = true, port = 0).isValid)
        assertFalse(LocalApiConfig(enabled = true, port = 65_536).isValid)
    }

    @Test
    fun disabledInvalidDraftDoesNotExposeAListener() {
        assertTrue(
            LocalApiConfig(
                enabled = false,
                bindAddress = "0.0.0.0",
                port = 0,
            ).isValid,
        )
    }
}
