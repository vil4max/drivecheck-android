@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.vil4max.drivecheck.provider

import io.github.vil4max.drivecheck.provider.TransportFailure.Kind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class UbillingRetryTest {
    private val clock = TestClock(Instant.parse("2026-10-17T10:00:00Z"))

    private fun client(transport: HttpTransport) = UbillingClient(transport, clock, userAgent = "test")

    private fun failing(kind: Kind): suspend (HttpRequest) -> HttpResponse = { throw TransportFailure(kind) }

    private fun ok(): suspend (HttpRequest) -> HttpResponse = { jsonResponse(feed()) }

    private fun tooMany(retryAfter: String? = null) = jsonResponse(
        "",
        status = 429,
        headers = if (retryAfter == null) emptyMap() else mapOf("retry-after" to retryAfter),
    )

    private suspend fun TestScope.rateLimitedUntil(client: UbillingClient): Instant =
        assertFailsWith<ProviderFailure.RateLimited> { client.fetchAlerts() }.until

    @Test
    fun REQ_REFRESH_003_a_transient_failure_is_retried_once_after_two_seconds() = runTest {
        for (kind in listOf(Kind.Timeout, Kind.ConnectionLost, Kind.CannotConnect, Kind.HostLookup)) {
            val transport = FakeTransport(listOf(failing(kind), ok()))
            val start = currentTime

            client(transport).fetchAlerts()

            assertEquals(2, transport.requests.size, kind.name)
            assertEquals(2.seconds.inWholeMilliseconds, currentTime - start, kind.name)
        }
    }

    @Test
    fun REQ_REFRESH_003_a_second_transient_failure_is_final() = runTest {
        val transport = FakeTransport(listOf(failing(Kind.Timeout), failing(Kind.Timeout), ok()))

        val failure = assertFailsWith<TransportFailure> { client(transport).fetchAlerts() }

        assertEquals(Kind.Timeout, failure.kind)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun REQ_REFRESH_003_http_errors_429_and_bad_bodies_are_not_retried() = runTest {
        val firstAnswers: List<suspend (HttpRequest) -> HttpResponse> = listOf(
            { jsonResponse("{}", status = 503) },
            { tooMany("45") },
            { jsonResponse("""{"source":""") },
            failing(Kind.Other),
        )
        for (first in firstAnswers) {
            val transport = FakeTransport(listOf(first, ok()))

            assertFailsWith<Exception> { client(transport).fetchAlerts() }

            assertEquals(1, transport.requests.size)
        }
    }

    @Test
    fun REQ_REFRESH_005_a_429_waits_out_retry_after_in_seconds() = runTest {
        val until = rateLimitedUntil(client(FakeTransport.answering(tooMany("45"))))

        assertEquals(clock.now + 45.seconds, until)
    }

    @Test
    fun REQ_REFRESH_005_a_429_waits_out_retry_after_as_an_http_date() = runTest {
        val until = rateLimitedUntil(client(FakeTransport.answering(tooMany("Sat, 17 Oct 2026 10:02:00 GMT"))))

        assertEquals(Instant.parse("2026-10-17T10:02:00Z"), until)
    }

    @Test
    fun REQ_REFRESH_005_retry_after_beyond_five_minutes_is_capped() = runTest {
        val bySeconds = rateLimitedUntil(client(FakeTransport.answering(tooMany("3600"))))
        val byDate = rateLimitedUntil(client(FakeTransport.answering(tooMany("Sat, 17 Oct 2026 12:00:00 GMT"))))

        assertEquals(clock.now + 5.minutes, bySeconds)
        assertEquals(clock.now + 5.minutes, byDate)
    }

    @Test
    fun REQ_REFRESH_005_a_past_or_negative_retry_after_counts_as_now() = runTest {
        val negative = rateLimitedUntil(client(FakeTransport.answering(tooMany("-5"))))
        val past = rateLimitedUntil(client(FakeTransport.answering(tooMany("Sat, 17 Oct 2026 09:00:00 GMT"))))

        assertEquals(clock.now, negative)
        assertEquals(clock.now, past)
    }

    @Test
    fun REQ_REFRESH_005_an_unusable_retry_after_falls_back_to_the_backoff() = runTest {
        for (header in listOf("soon", "NaN", "Infinity", "")) {
            val until = rateLimitedUntil(client(FakeTransport.answering(tooMany(header))))

            assertEquals(clock.now + 30.seconds, until, header)
        }
    }

    @Test
    fun REQ_REFRESH_005_consecutive_429s_escalate_and_a_success_resets_the_backoff() = runTest {
        val client = client(
            FakeTransport.answering(
                tooMany(), tooMany(), tooMany(), tooMany(), tooMany(),
                jsonResponse(feed()),
                tooMany(),
            ),
        )

        val holds = List(5) { rateLimitedUntil(client) - clock.now }
        client.fetchAlerts()
        val afterSuccess = rateLimitedUntil(client) - clock.now

        assertEquals(listOf(30.seconds, 60.seconds, 120.seconds, 240.seconds, 5.minutes), holds)
        assertEquals(30.seconds, afterSuccess)
    }
}
