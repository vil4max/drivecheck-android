@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.provider.AlertsProvider
import io.github.vil4max.drivecheck.provider.ProviderFailure
import io.github.vil4max.drivecheck.provider.TransportFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// The iOS CarPlay refresh cycle on the car screen (REQ-REFRESH-004).
class CarRefreshCycleTest {
    private val epoch = Instant.parse("2026-10-17T10:00:00Z")

    private fun TestScope.clock() = object : Clock {
        override fun now(): Instant = epoch + testScheduler.currentTime.milliseconds
    }

    private class ScriptedProvider(private val answer: (Int) -> AlertsSnapshot) : AlertsProvider {
        val callTimes = mutableListOf<Long>()
        var scheduler: (() -> Long)? = null
        override suspend fun fetchAlerts(): AlertsSnapshot {
            callTimes += scheduler!!()
            return answer(callTimes.size)
        }
    }

    private fun TestScope.provider(answer: (Int) -> AlertsSnapshot) =
        ScriptedProvider(answer).also { it.scheduler = { testScheduler.currentTime } }

    private fun TestScope.snapshot() = AlertsSnapshot("test", null, clock().now(), mapOf(AlertRegion.KyivCity to AlertStatus.Quiet))

    private fun TestScope.started(provider: AlertsProvider) =
        StatusController(provider, clock(), backgroundScope, jitter = { 0.5 }).also { it.onSurfaceStarted(fetchNow = false) }

    private val lost = TransportFailure(TransportFailure.Kind.ConnectionLost)

    @Test
    fun REQ_REFRESH_004_the_cycle_tries_three_times_with_2_then_4_s() = runTest {
        val provider = provider { throw lost }
        val controller = started(provider)

        controller.startRefreshCycle()
        advanceTimeBy(30.seconds)
        runCurrent()

        assertEquals(listOf(0L, 2_000L, 6_000L), provider.callTimes)
    }

    @Test
    fun REQ_REFRESH_004_the_cycle_stops_at_the_first_success() = runTest {
        val provider = provider { call -> if (call == 1) throw lost else snapshot() }
        val controller = started(provider)

        controller.startRefreshCycle()
        advanceTimeBy(30.seconds)
        runCurrent()

        assertEquals(listOf(0L, 2_000L), provider.callTimes)
    }

    @Test
    fun REQ_REFRESH_004_the_cycle_stops_while_rate_limited() = runTest {
        val provider = provider { throw ProviderFailure.RateLimited(epoch + 5.minutes) }
        val controller = started(provider)

        controller.startRefreshCycle()
        advanceTimeBy(30.seconds)
        runCurrent()

        assertEquals(listOf(0L), provider.callTimes)
    }

    @Test
    fun REQ_REFRESH_004_a_new_cycle_supersedes_the_old_one() = runTest {
        // Each request takes 1 s, so the two cycles' attempts fall on different moments and a
        // cycle left running would add a request of its own.
        val provider = provider { throw lost }
        val slow = AlertsProvider {
            delay(1.seconds)
            provider.fetchAlerts()
        }
        val controller = started(slow)

        controller.startRefreshCycle()
        advanceTimeBy(3_500.milliseconds)
        runCurrent()
        controller.startRefreshCycle()
        advanceTimeBy(30.seconds)
        runCurrent()

        // Requests start at 0 s and 3 s (old cycle), then 6 s and 11 s (the new one joins the 3 s
        // request and backs off 2 s, then 4 s, after each failure ends); each is recorded as it
        // ends, 1 s later. The old cycle's 8 s attempt never comes.
        assertEquals(listOf(1_000L, 4_000L, 7_000L, 12_000L), provider.callTimes)
    }
}
