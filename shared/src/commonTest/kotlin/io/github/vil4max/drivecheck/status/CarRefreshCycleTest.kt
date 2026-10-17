@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.provider.AlertsProvider
import io.github.vil4max.drivecheck.provider.ProviderFailure
import io.github.vil4max.drivecheck.provider.TransportFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val provider = provider { throw lost }
        val controller = started(provider)

        controller.startRefreshCycle()
        advanceTimeBy(3.seconds)
        runCurrent()
        controller.startRefreshCycle()
        advanceTimeBy(30.seconds)
        runCurrent()

        // Old cycle: 0 and 2 s. The new one starts at 3 s, held by the 2 s gap until it may run,
        // then 2 s and 4 s later; the old cycle's 6 s attempt never comes.
        assertEquals(3, provider.callTimes.count { it >= 3_000L }, "times: ${provider.callTimes}")
        assertEquals(5, provider.callTimes.size, "times: ${provider.callTimes}")
    }
}
