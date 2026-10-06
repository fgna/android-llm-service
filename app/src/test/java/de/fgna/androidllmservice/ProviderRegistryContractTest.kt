package de.fgna.androidllmservice

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryContractTest {
    @Test
    fun providerProfileIdsAreStable() {
        assertEquals("on-device", ProviderIds.ON_DEVICE)
        assertEquals("lan", ProviderIds.LAN)
    }

    @Test
    fun requestLifecycleErrorCodesAreStable() {
        assertEquals("REQUEST_CANCELLED", ProviderErrorCodes.REQUEST_CANCELLED)
        assertEquals("REQUEST_ALREADY_ACTIVE", ProviderErrorCodes.REQUEST_ALREADY_ACTIVE)
    }

    @Test
    fun providerFailuresExposeStableErrorCodes() {
        assertEquals(
            ProviderErrorCodes.GENERATION_FAILURE,
            IOException("local file failure").toProviderError().code,
        )
        assertEquals(
            ProviderErrorCodes.MODEL_CAPABILITY_MISMATCH,
            UnsupportedOperationException("text only").toProviderError().code,
        )
        assertEquals(
            ProviderErrorCodes.NETWORK_FAILURE,
            ProviderException(
                ProviderErrorCodes.NETWORK_FAILURE,
                "LAN offline",
            ).toProviderError().code,
        )
        assertEquals(
            ProviderErrorCodes.MODEL_CAPABILITY_MISMATCH,
            ProviderException(
                ProviderErrorCodes.MODEL_CAPABILITY_MISMATCH,
                "unknown model",
            ).toProviderError().code,
        )
        assertEquals(
            ProviderErrorCodes.GENERATION_FAILURE,
            IllegalStateException("bad response").toProviderError().code,
        )
    }

    @Test
    fun lanHttpFailureRequiresModelOrCapabilityEvidence() {
        assertEquals(
            ProviderErrorCodes.GENERATION_FAILURE,
            classifyLanHttpFailure("""{"error":"route not found"}"""),
        )
        assertEquals(
            ProviderErrorCodes.MODEL_CAPABILITY_MISMATCH,
            classifyLanHttpFailure("""{"error":"model not found"}"""),
        )
        assertEquals(
            ProviderErrorCodes.MODEL_CAPABILITY_MISMATCH,
            classifyLanHttpFailure("""{"error":"model does not support images"}"""),
        )
    }

    @Test
    fun lanConfigurationRequiresHttpEndpointAndModel() {
        assertFalse(LanProviderConfig(baseUrl = "", model = "model").isValid)
        assertFalse(LanProviderConfig(baseUrl = "http://server:1234", model = "").isValid)
        assertFalse(LanProviderConfig(baseUrl = "ftp://server", model = "model").isValid)
        assertTrue(LanProviderConfig(baseUrl = "http://server:1234/", model = "model").isValid)
        assertTrue(LanProviderConfig(baseUrl = "https://server.example", model = "model").isValid)
    }
}
