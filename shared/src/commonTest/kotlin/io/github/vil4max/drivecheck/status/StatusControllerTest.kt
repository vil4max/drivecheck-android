@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.provider.AlertsProvider
import io.github.vil4max.drivecheck.provider.ProviderFailure
import io.github.vil4max.drivecheck.provider.TestClock
import io.github.vil4max.drivecheck.provider.TransportFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StatusControllerTest {
    private val clock = TestClock(Instant.parse("2026-10-17T10:00:00Z"))
    private val cachedAt = Instant.parse("2026-10-17T09:59:30Z")

    private fun snapshot(kyiv: AlertStatus? = AlertStatus.Quiet) = AlertsSnapshot(
        source = "test",
        serverCachedAt = cachedAt,
        fetchedAt = clock.now,
        statuses = buildMap {
            put(AlertRegion.Lviv, AlertStatus.Alarm)
            if (kyiv != null) put(AlertRegion.KyivCity, kyiv)
        },
    )

    private class ScriptedProvider(answers: List<suspend () -> AlertsSnapshot>) : AlertsProvider {
        private val queue = ArrayDeque(answers)
        var calls = 0

        override suspend fun fetchAlerts(): AlertsSnapshot {
            calls += 1
            val next = queue.removeFirstOrNull() ?: error("no answer left for call $calls")
            return next()
        }
    }

    private fun TestScope.controller(provider: AlertsProvider, scope: CoroutineScope = backgroundScope) =
        StatusController(provider, clock, scope)

    private fun answer(snapshot: AlertsSnapshot): suspend () -> AlertsSnapshot = { snapshot }

    private fun failure(error: Exception): suspend () -> AlertsSnapshot = { throw error }

    @Test
    fun REQ_REFRESH_001_a_started_surface_fetches_at_once() = runTest {
        val provider = ScriptedProvider(listOf(answer(snapshot(AlertStatus.Alarm))))
        val controller = controller(provider)
        assertEquals(StatusState.Checking, controller.state.value)

        controller.onSurfaceStarted()
        runCurrent()

        assertEquals(1, provider.calls)
        assertEquals(StatusState.Alarm(cachedAt), controller.state.value)
    }

    @Test
    fun REQ_REFRESH_001_refresh_fetches_at_once() = runTest {
        val provider = ScriptedProvider(listOf(answer(snapshot()), answer(snapshot(AlertStatus.Alarm))))
        val controller = controller(provider)
        controller.onSurfaceStarted()
        runCurrent()
        clock.now += 11.seconds

        controller.refresh()
        runCurrent()

        assertEquals(2, provider.calls)
        assertEquals(StatusState.Alarm(cachedAt), controller.state.value)
    }

    @Test
    fun REQ_REFRESH_001_a_stopped_surface_sends_no_request() = runTest {
        val provider = ScriptedProvider(listOf(answer(snapshot())))
        val controller = controller(provider)

        controller.refresh()
        runCurrent()
        assertEquals(0, provider.calls)

        controller.onSurfaceStarted()
        runCurrent()
        controller.onSurfaceStopped()
        clock.now += 60.seconds
        controller.refresh()
        runCurrent()

        assertEquals(1, provider.calls)
    }

    @Test
    fun REQ_REFRESH_001_a_request_running_when_the_surface_stops_completes() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = ScriptedProvider(listOf({ gate.await(); snapshot(AlertStatus.Alarm) }))
        val controller = controller(provider)

        controller.onSurfaceStarted()
        runCurrent()
        controller.onSurfaceStopped()
        gate.complete(Unit)
        runCurrent()

        assertEquals(StatusState.Alarm(cachedAt), controller.state.value)
    }

    @Test
    fun REQ_REFRESH_001_a_cancelled_request_is_not_a_failure() = runTest {
        val owner = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val never = CompletableDeferred<Unit>()
        val provider = ScriptedProvider(listOf({ never.await(); snapshot() }))
        val controller = controller(provider, owner)
        controller.onSurfaceStarted()
        runCurrent()

        owner.cancel()
        runCurrent()

        assertEquals(1, provider.calls)
        assertEquals(StatusState.Checking, controller.state.value)
    }

    @Test
    fun REQ_REGION_001_a_feed_without_kyiv_city_shows_region_unavailable() = runTest {
        val controller = controller(ScriptedProvider(listOf(answer(snapshot(kyiv = null)))))

        controller.onSurfaceStarted()
        runCurrent()

        assertEquals(StatusState.RegionUnavailable, controller.state.value)
    }

    @Test
    fun REQ_REFRESH_006_a_failed_refresh_keeps_the_last_status_and_its_time() = runTest {
        val provider = ScriptedProvider(
            listOf(
                answer(snapshot(AlertStatus.Alarm)),
                failure(TransportFailure(TransportFailure.Kind.Other)),
                failure(ProviderFailure.HttpStatus(503, "")),
            ),
        )
        val controller = controller(provider)
        controller.onSurfaceStarted()
        runCurrent()

        repeat(2) {
            clock.now += 11.seconds
            controller.refresh()
            runCurrent()
        }

        assertEquals(3, provider.calls)
        assertEquals(StatusState.Alarm(cachedAt), controller.state.value)
    }

    @Test
    fun REQ_REFRESH_006_a_failure_without_a_snapshot_shows_unavailable() = runTest {
        val controller = controller(ScriptedProvider(listOf(failure(TransportFailure(TransportFailure.Kind.Timeout)))))

        controller.onSurfaceStarted()
        runCurrent()

        assertEquals(StatusState.Unavailable, controller.state.value)
    }

    @Test
    fun REQ_REFRESH_005_a_429_without_a_snapshot_shows_unavailable_and_opens_the_window() = runTest {
        val until = clock.now + 45.seconds
        val controller = controller(ScriptedProvider(listOf(failure(ProviderFailure.RateLimited(until)))))

        controller.onSurfaceStarted()
        runCurrent()

        assertEquals(StatusState.Unavailable, controller.state.value)
        assertEquals(true, controller.isRateLimited())
        clock.now += 45.seconds
        assertEquals(false, controller.isRateLimited())
    }

    @Test
    fun REQ_REFRESH_010_a_held_refresh_keeps_the_snapshot_and_records_no_failure() = runTest {
        val provider = ScriptedProvider(listOf(answer(snapshot(AlertStatus.Alarm))))
        val controller = controller(provider)
        controller.onSurfaceStarted()
        runCurrent()

        clock.now += 9.seconds
        controller.refresh()
        controller.onSurfaceStopped()
        controller.onSurfaceStarted()
        runCurrent()

        assertEquals(1, provider.calls)
        assertEquals(StatusState.Alarm(cachedAt), controller.state.value)
    }

    @Test
    fun REQ_PROVIDER_002_requests_equal_the_triggers_not_held() = runTest {
        val slow = CompletableDeferred<Unit>()
        val provider = ScriptedProvider(listOf(answer(snapshot()), { slow.await(); snapshot() }))
        val controller = controller(provider)

        controller.onSurfaceStarted() // sent
        runCurrent()
        clock.now += 5.seconds
        controller.refresh() // held by the floor
        runCurrent()
        clock.now += 6.seconds
        controller.refresh() // sent
        runCurrent()
        controller.refresh() // joins the request in flight
        runCurrent()
        slow.complete(Unit)
        runCurrent()

        assertEquals(2, provider.calls)
    }

    @Test
    fun REQ_ANDROID_001_requests_are_at_least_two_seconds_apart() = runTest {
        val provider = ScriptedProvider(
            listOf(
                failure(TransportFailure(TransportFailure.Kind.Other)),
                failure(ProviderFailure.RateLimited(clock.now + 30.seconds)),
                answer(snapshot()),
            ),
        )
        val controller = controller(provider)
        controller.onSurfaceStarted()
        runCurrent()

        clock.now += 1.seconds
        controller.refresh()
        runCurrent()
        assertEquals(1, provider.calls)

        clock.now += 1.seconds
        controller.refresh()
        runCurrent()
        assertEquals(2, provider.calls)

        clock.now += 1.9.seconds
        controller.refresh()
        runCurrent()
        assertEquals(2, provider.calls)

        clock.now += 0.1.seconds
        controller.refresh()
        runCurrent()
        assertEquals(3, provider.calls)
    }

    @Test
    fun REQ_ANDROID_001_checking_is_shown_while_a_request_runs() = runTest {
        val slow = CompletableDeferred<Unit>()
        val provider = ScriptedProvider(
            listOf({ slow.await(); throw TransportFailure(TransportFailure.Kind.Other) }),
        )
        val controller = controller(provider)
        assertEquals(false, controller.isChecking.value)

        controller.onSurfaceStarted()
        runCurrent()
        assertEquals(true, controller.isChecking.value)

        slow.complete(Unit)
        runCurrent()
        assertEquals(false, controller.isChecking.value)
        assertEquals(StatusState.Unavailable, controller.state.value)
    }

    @Test
    fun REQ_PROVIDER_002_a_trigger_during_a_request_joins_it() = runTest {
        val slow = CompletableDeferred<Unit>()
        val provider = ScriptedProvider(listOf({ slow.await(); snapshot(AlertStatus.Alarm) }))
        val controller = controller(provider)

        controller.onSurfaceStarted()
        runCurrent()
        repeat(3) { controller.refresh() }
        controller.onSurfaceStopped()
        controller.onSurfaceStarted()
        runCurrent()
        slow.complete(Unit)
        runCurrent()

        assertEquals(1, provider.calls)
        assertEquals(StatusState.Alarm(cachedAt), controller.state.value)
    }
}
