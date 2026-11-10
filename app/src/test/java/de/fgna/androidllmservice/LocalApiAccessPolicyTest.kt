package de.fgna.androidllmservice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalApiAccessPolicyTest {
    @Test
    fun disabledApiRejectsEveryRequest() {
        assertFalse(
            LocalApiAccessPolicy.isAuthorized(
                LocalApiConfig(enabled = false),
                authorizationHeader = null,
            ),
        )
    }

    @Test
    fun enabledLoopbackApiDoesNotRequireLanCredential() {
        assertTrue(
            LocalApiAccessPolicy.isAuthorized(
                LocalApiConfig(enabled = true),
                authorizationHeader = null,
            ),
        )
    }

    @Test
    fun invalidLanConfigurationRejectsBeforeCredentialCheck() {
        assertFalse(
            LocalApiAccessPolicy.isAuthorized(
                LocalApiConfig(enabled = true, bindAddress = "0.0.0.0"),
                authorizationHeader = "Bearer anything",
            ),
        )
    }

    @Test
    fun lanApiRequiresExactBearerCredential() {
        val config = LocalApiConfig(
            enabled = true,
            bindAddress = "0.0.0.0",
            authToken = "expected-secret",
        )

        assertFalse(LocalApiAccessPolicy.isAuthorized(config, null))
        assertFalse(LocalApiAccessPolicy.isAuthorized(config, "expected-secret"))
        assertFalse(LocalApiAccessPolicy.isAuthorized(config, "Bearer wrong-secret"))
        assertTrue(LocalApiAccessPolicy.isAuthorized(config, "Bearer expected-secret"))
        assertTrue(LocalApiAccessPolicy.isAuthorized(config, "bearer expected-secret"))
    }

    @Test
    fun blankBearerCredentialIsRejected() {
        val config = LocalApiConfig(
            enabled = true,
            bindAddress = "192.168.1.20",
            authToken = "expected-secret",
        )

        assertFalse(LocalApiAccessPolicy.isAuthorized(config, "Bearer"))
        assertFalse(LocalApiAccessPolicy.isAuthorized(config, "Bearer   "))
    }
}
