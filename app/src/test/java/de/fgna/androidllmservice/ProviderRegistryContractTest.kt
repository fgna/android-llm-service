package de.fgna.androidllmservice

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryContractTest {
    private class BlockingConnection : HttpURLConnection(URL("http://localhost")) {
        val responseStarted = CountDownLatch(1)
        private val releaseResponse = CountDownLatch(1)
        @Volatile var wasDisconnected = false

        override fun getResponseCode(): Int {
            responseStarted.countDown()
            releaseResponse.await()
            throw IOException("connection disconnected")
        }

        override fun disconnect() {
            wasDisconnected = true
            releaseResponse.countDown()
        }

        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }

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
    fun cancellationDoesNotTriggerGpuFallback() {
        assertFalse(shouldFallbackToCpu(CancellationException("cancelled")))
        assertTrue(shouldFallbackToCpu(IllegalStateException("GPU failure")))
    }

    @Test
    fun cancellingLanHttpReadDisconnectsConnection() = runBlocking {
        val connection = BlockingConnection()
        val request = launch(Dispatchers.Default) { readLanResponse(connection) }
        assertTrue(connection.responseStarted.await(5, TimeUnit.SECONDS))
        request.cancelAndJoin()
        assertTrue(connection.wasDisconnected)
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
