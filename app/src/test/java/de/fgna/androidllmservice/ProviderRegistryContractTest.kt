package de.fgna.androidllmservice

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
    fun lanConfigurationRequiresHttpEndpointAndModel() {
        assertFalse(LanProviderConfig(baseUrl = "", model = "model").isValid)
        assertFalse(LanProviderConfig(baseUrl = "http://server:1234", model = "").isValid)
        assertFalse(LanProviderConfig(baseUrl = "ftp://server", model = "model").isValid)
        assertTrue(LanProviderConfig(baseUrl = "http://server:1234/", model = "model").isValid)
        assertTrue(LanProviderConfig(baseUrl = "https://server.example", model = "model").isValid)
    }
}
