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
    fun providerFailuresExposeStableErrorCodes() {
        assertEquals(
            ProviderErrorCodes.NETWORK_FAILURE,
            IOException("offline").toProviderError().code,
        )
        assertEquals(
            ProviderErrorCodes.MODEL_CAPABILITY_MISMATCH,
            UnsupportedOperationException("text only").toProviderError().code,
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
    fun lanConfigurationRequiresHttpEndpointAndModel() {
        assertFalse(LanProviderConfig(baseUrl = "", model = "model").isValid)
        assertFalse(LanProviderConfig(baseUrl = "http://server:1234", model = "").isValid)
        assertFalse(LanProviderConfig(baseUrl = "ftp://server", model = "model").isValid)
        assertTrue(LanProviderConfig(baseUrl = "http://server:1234/", model = "model").isValid)
        assertTrue(LanProviderConfig(baseUrl = "https://server.example", model = "model").isValid)
    }
}
