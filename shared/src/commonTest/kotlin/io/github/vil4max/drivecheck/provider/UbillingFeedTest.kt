package io.github.vil4max.drivecheck.provider

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class UbillingFeedTest {
    private val clock = TestClock()

    private fun client(transport: HttpTransport, log: ProviderLog = RecordingLog()) =
        UbillingClient(transport, clock, userAgent = UbillingClient.userAgent("1.2.3"), log = log)

    @Test
    fun REQ_PROVIDER_001_requests_the_default_json_endpoint_without_cache() = runTest {
        val transport = FakeTransport.answering(jsonResponse(LIVE_FEED))

        client(transport).fetchAlerts()

        val request = transport.requests.single()
        assertEquals("https://ubilling.net.ua/aerialalerts/", request.url)
        assertTrue(request.bypassCache)
        assertEquals(15.seconds, request.timeout)
        assertEquals("DriveCheck/1.2.3 (Android)", request.headers["User-Agent"])
    }

    @Test
    fun REQ_PROVIDER_001_reads_alertnow_for_every_catalog_region() = runTest {
        val alarmKeys = LIVE_FEED_ALARM_KEYS.split(",").toSet()

        val snapshot = client(FakeTransport.answering(jsonResponse(LIVE_FEED))).fetchAlerts()

        assertEquals(AlertRegion.entries.toSet(), snapshot.statuses.keys)
        AlertRegion.entries.forEach { region ->
            val expected = if (region.apiKey in alarmKeys) AlertStatus.Alarm else AlertStatus.Quiet
            assertEquals(expected, snapshot.status(region), region.apiKey)
        }
        assertEquals("Vadym Klymenko API (default)", snapshot.source)
        assertEquals(clock.now, snapshot.fetchedAt)
    }

    @Test
    fun REQ_PROVIDER_001_reads_cachedat_in_kyiv_time_across_daylight_saving() = runTest {
        val winter = client(FakeTransport.answering(jsonResponse(feed(cachedAt = "2026-01-15 12:00:00")))).fetchAlerts()
        val summer = client(FakeTransport.answering(jsonResponse(feed(cachedAt = "2026-07-15 12:00:00")))).fetchAlerts()

        assertEquals(Instant.parse("2026-01-15T10:00:00Z"), winter.checkedAt)
        assertEquals(Instant.parse("2026-07-15T09:00:00Z"), summer.checkedAt)
    }

    @Test
    fun REQ_PROVIDER_001_an_unparseable_cachedat_falls_back_to_the_fetch_time() = runTest {
        val snapshot = client(FakeTransport.answering(jsonResponse(feed(cachedAt = "yesterday")))).fetchAlerts()

        assertEquals(null, snapshot.serverCachedAt)
        assertEquals(clock.now, snapshot.checkedAt)
    }

    @Test
    fun REQ_PROVIDER_001_non_2xx_non_json_and_undecodable_answers_fail() = runTest {
        val serverError = jsonResponse("""{"error":"x"}""", status = 503)
        val html = HttpResponse(200, mapOf("content-type" to "text/html"), "<html></html>".encodeToByteArray())
        val garbled = jsonResponse("""{"source":"x","states":""")

        val statusFailure = assertFailsWith<ProviderFailure.HttpStatus> {
            client(FakeTransport.answering(serverError)).fetchAlerts()
        }
        assertEquals(503, statusFailure.code)
        assertFailsWith<ProviderFailure.NotJson> { client(FakeTransport.answering(html)).fetchAlerts() }
        assertFailsWith<ProviderFailure.Undecodable> { client(FakeTransport.answering(garbled)).fetchAlerts() }
    }

    @Test
    fun REQ_REGION_001_unknown_keys_are_ignored_and_each_is_logged_once() = runTest {
        val log = RecordingLog()
        val client = client(FakeTransport.answering(jsonResponse(LIVE_FEED), jsonResponse(LIVE_FEED)), log)

        val first = client.fetchAlerts()
        client.fetchAlerts()

        assertEquals(25, first.statuses.size)
        assertEquals(1, log.lines.count { "Севастополь" in it }, log.lines.toString())
    }
}
