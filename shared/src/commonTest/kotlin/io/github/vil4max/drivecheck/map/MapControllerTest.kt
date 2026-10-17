@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.vil4max.drivecheck.map

import io.github.vil4max.drivecheck.provider.FakeTransport
import io.github.vil4max.drivecheck.provider.HttpRequest
import io.github.vil4max.drivecheck.provider.HttpResponse
import io.github.vil4max.drivecheck.provider.TransportFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MapControllerTest {
    private val epoch = Instant.parse("2026-10-17T10:00:00Z")

    private fun TestScope.clock() = object : Clock {
        override fun now(): Instant = epoch + (testScheduler.currentTime / 1000.0).seconds
    }

    private val png = HttpResponse(200, mapOf("content-type" to "image/png"), byteArrayOf(1, 2, 3))
    private val failure: suspend (HttpRequest) -> HttpResponse = { throw TransportFailure(TransportFailure.Kind.Timeout) }

    private fun TestScope.controller(vararg answers: suspend (HttpRequest) -> HttpResponse): Pair<MapController, FakeTransport> {
        val transport = FakeTransport(answers.toList())
        return MapController(transport, clock(), backgroundScope, userAgent = "test") to transport
    }

    @Test
    fun REQ_ANDROID_002_the_map_loads_once_3_s_after_the_status_settles() = runTest {
        val (map, transport) = controller({ png })

        map.onCardShown()
        map.onStatusSettled()
        advanceTimeBy(2.9.seconds)
        runCurrent()
        assertEquals(0, transport.requests.size)

        advanceTimeBy(0.1.seconds)
        runCurrent()
        assertEquals(1, transport.requests.size)
        assertEquals("https://ubilling.net.ua/aerialalerts/?map=nightmode", transport.requests.single().url)
        val loaded = assertIs<MapState.Loaded>(map.state.value)
        assertEquals(clock().now(), loaded.fetchedAt)

        map.onStatusSettled()
        map.onCardShown()
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(1, transport.requests.size, "no polling and no reload on refresh")
    }

    @Test
    fun REQ_ANDROID_002_a_failure_shows_refresh_map_and_never_an_old_image() = runTest {
        val (map, transport) = controller({ png }, failure, { png })
        map.onCardShown()
        map.onStatusSettled()
        advanceTimeBy(3.seconds)
        runCurrent()

        map.onNewSession()
        assertEquals(MapState.Idle, map.state.value, "a new session clears the image")
        map.onCardShown()
        map.onStatusSettled()
        advanceTimeBy(3.seconds)
        runCurrent()
        assertEquals(MapState.Failed, map.state.value)

        map.retry()
        runCurrent()
        assertIs<MapState.Loaded>(map.state.value)
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun REQ_ANDROID_002_a_non_image_answer_is_a_failure() = runTest {
        val html = HttpResponse(200, mapOf("content-type" to "text/html"), "<html>".encodeToByteArray())
        val (map, _) = controller({ html })

        map.onCardShown()
        map.onStatusSettled()
        advanceTimeBy(3.seconds)
        runCurrent()

        assertEquals(MapState.Failed, map.state.value)
    }

    @Test
    fun REQ_ANDROID_002_the_caption_counts_regions_under_alert_and_ages_the_image() {
        assertEquals(MapCaption(alerts = 11, total = 25, minutes = 1), MapCaption.of(alerts = 11, fetchedAt = epoch, now = epoch + 61.seconds))
        assertEquals(0, MapCaption.of(alerts = 0, fetchedAt = epoch, now = epoch + 30.seconds).minutes)
    }
}
