package io.github.vil4max.drivecheck.provider

import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

class UrlConnectionTransportTest {
    @Test
    fun REQ_PROVIDER_001_url_connection_disables_caches_and_sets_timeouts_and_user_agent() {
        val request = HttpRequest(
            UbillingClient.ENDPOINT,
            timeout = 15.seconds,
            bypassCache = true,
            headers = mapOf("User-Agent" to "DriveCheck/1.2.3 (Android)"),
        )
        val connection = URI(request.url).toURL().openConnection() as HttpURLConnection

        UrlConnectionTransport.configure(connection, request)

        assertEquals("GET", connection.requestMethod)
        assertFalse(connection.useCaches)
        assertEquals(15_000, connection.connectTimeout)
        assertEquals(15_000, connection.readTimeout)
        assertEquals("DriveCheck/1.2.3 (Android)", connection.getRequestProperty("User-Agent"))
        assertEquals("no-cache", connection.getRequestProperty("Cache-Control"))
    }
}
