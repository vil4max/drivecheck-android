@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.provider.AlertsProvider
import io.github.vil4max.drivecheck.provider.ProviderFailure
import io.github.vil4max.drivecheck.refresh.RefreshEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PeriodicRefreshTest {
    private val epoch = Instant.parse("2026-10-17T10:00:00Z")

    // The virtual clock of the test scheduler, so delays and the floor see the same time.
    private fun TestScope.clock() = object : Clock {
        override fun now(): Instant = epoch + testScheduler.currentTime.let { it / 1000.0 }.seconds
    }

    private class CountingProvider(private val answer: (Int) -> AlertsSnapshot) : AlertsProvider {
        var calls = 0
        override suspend fun fetchAlerts(): AlertsSnapshot {
            calls += 1
            return answer(calls)
        }
    }

    private fun TestScope.snapshot(kyiv: AlertStatus) =
        AlertsSnapshot("test", null, clock().now(), mapOf(AlertRegion.KyivCity to kyiv))

    private fun TestScope.controller(provider: AlertsProvider, environment: () -> RefreshEnvironment = { RefreshEnvironment() }) =
        StatusController(provider, clock(), backgroundScope, environment = environment, jitter = { 0.5 })

    @Test
    fun REQ_REFRESH_002_a_started_surface_polls_on_the_adaptive_interval() = runTest {
        val provider = CountingProvider { call -> snapshot(if (call >= 2) AlertStatus.Alarm else AlertStatus.Quiet) }
        val controller = controller(provider)

        controller.onSurfaceStarted()
        runCurrent()
        advanceTimeBy(59.seconds)
        runCurrent()
        assertEquals(1, provider.calls)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(2, provider.calls, "quiet: 60 s")

        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(3, provider.calls, "alarm: 30 s")
    }

    @Test
    fun REQ_REFRESH_002_power_saver_polls_every_five_minutes() = runTest {
        val provider = CountingProvider { snapshot(AlertStatus.Alarm) }
        val controller = controller(provider) { RefreshEnvironment(isPowerSaveMode = true) }

        controller.onSurfaceStarted()
        runCurrent()
        advanceTimeBy(4.minutes)
        runCurrent()
        assertEquals(1, provider.calls)

        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(2, provider.calls)
    }

    @Test
    fun REQ_REFRESH_002_a_scheduled_poll_is_skipped_inside_the_rate_limit_window() = runTest {
        var limitedUntil: Instant? = null
        val provider = CountingProvider { call ->
            if (call == 1) {
                limitedUntil = clock().now() + 90.seconds
                throw ProviderFailure.RateLimited(limitedUntil!!)
            }
            snapshot(AlertStatus.Quiet)
        }
        val controller = controller(provider)

        controller.onSurfaceStarted()
        runCurrent()
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(1, provider.calls, "the poll at 60 s is inside the 90 s window")

        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(2, provider.calls)
    }

    @Test
    fun REQ_REFRESH_002_stopping_the_surface_stops_polling() = runTest {
        val provider = CountingProvider { snapshot(AlertStatus.Quiet) }
        val controller = controller(provider)

        controller.onSurfaceStarted()
        runCurrent()
        controller.onSurfaceStopped()
        advanceTimeBy(10.minutes)
        runCurrent()

        assertEquals(1, provider.calls)
    }
}
