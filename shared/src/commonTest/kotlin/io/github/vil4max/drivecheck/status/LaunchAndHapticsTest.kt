@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.provider.AlertsProvider
import io.github.vil4max.drivecheck.provider.TestClock
import io.github.vil4max.drivecheck.provider.TransportFailure
import io.github.vil4max.drivecheck.store.InMemoryStatusStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LaunchAndHapticsTest {
    private val clock = TestClock(Instant.parse("2026-10-17T10:00:00Z"))
    private fun snapshot(at: Instant, kyiv: AlertStatus) = AlertsSnapshot("t", at, at, mapOf(AlertRegion.KyivCity to kyiv))

    @Test
    fun REQ_LAUNCH_001_no_status_colour_before_a_status_is_known() {
        val presentation = StatusPresenter.present(StatusState.Checking, null, AlertRegion.KyivCity, clock.now, 60.seconds)

        assertEquals(DisplayPhase.Checking, presentation.phase)
    }

    @Test
    fun REQ_LAUNCH_002_a_stored_status_is_the_first_state_with_no_wait() = runTest {
        val store = InMemoryStatusStore().apply { saveSnapshot(snapshot(clock.now, AlertStatus.Alarm)) }

        val controller = StatusController(AlertsProvider { error("not called") }, clock, backgroundScope, store = store)

        assertEquals(StatusState.Alarm(clock.now), controller.state.value)
    }

    @Test
    fun REQ_LAUNCH_003_a_cached_status_shows_at_once_and_the_refresh_only_marks_checking() = runTest {
        val store = InMemoryStatusStore().apply { saveSnapshot(snapshot(clock.now - 10.minutes, AlertStatus.Quiet)) }
        val slow = CompletableDeferred<Unit>()
        val controller = StatusController(AlertsProvider { slow.await(); snapshot(clock.now, AlertStatus.Quiet) }, clock, backgroundScope, store = store)

        controller.onSurfaceStarted()
        runCurrent()

        assertEquals(StatusState.Quiet(clock.now - 10.minutes), controller.state.value)
        assertEquals(true, controller.isChecking.value)
    }

    @Test
    fun REQ_LAUNCH_004_a_stale_cache_at_launch_is_never_green_and_a_stale_alarm_stays_red() {
        val old = clock.now - 10.minutes
        val quiet = StatusPresenter.present(StatusState.Quiet(old), snapshot(old, AlertStatus.Quiet), AlertRegion.KyivCity, clock.now, 60.seconds)
        val alarm = StatusPresenter.present(StatusState.Alarm(old), snapshot(old, AlertStatus.Alarm), AlertRegion.KyivCity, clock.now, 60.seconds)

        assertEquals(DisplayPhase.Stale, quiet.phase)
        assertEquals(DisplayPhase.Alarm, alarm.phase)
    }

    @Test
    fun REQ_ANDROID_001_the_pull_indicator_shows_only_for_the_drivers_refresh() = runTest {
        val slow = CompletableDeferred<Unit>()
        var calls = 0
        val controller = StatusController(
            AlertsProvider {
                calls += 1
                if (calls == 2) slow.await()
                snapshot(clock.now, AlertStatus.Quiet)
            },
            clock,
            backgroundScope,
        )
        controller.onSurfaceStarted()
        runCurrent()
        assertEquals(false, controller.isDriverRefreshing.value, "the start is not a pull")

        clock.now += 11.seconds
        controller.refresh()
        runCurrent()
        assertEquals(true, controller.isDriverRefreshing.value)
        slow.complete(Unit)
        runCurrent()
        assertEquals(false, controller.isDriverRefreshing.value)
    }

    @Test
    fun REQ_REFRESH_011_a_driver_refresh_ends_with_confirm_or_reject_and_automatic_ones_with_nothing() = runTest {
        var fail = false
        val controller = StatusController(
            AlertsProvider { if (fail) throw TransportFailure(TransportFailure.Kind.Other) else snapshot(clock.now, AlertStatus.Quiet) },
            clock,
            backgroundScope,
        )
        val outcomes = mutableListOf<RefreshOutcome>()
        backgroundScope.launch { controller.refreshOutcomes.collect { outcomes += it } }
        runCurrent()

        controller.onSurfaceStarted()
        runCurrent()
        assertEquals(emptyList(), outcomes, "the start is not the driver's refresh")

        controller.refresh()
        runCurrent()
        assertEquals(listOf(RefreshOutcome.Confirm), outcomes, "held by the floor still confirms")

        clock.now += 11.seconds
        fail = true
        controller.refresh()
        runCurrent()
        assertEquals(listOf(RefreshOutcome.Confirm, RefreshOutcome.Reject), outcomes)

        clock.now += 11.seconds
        fail = false
        controller.refresh()
        runCurrent()
        assertEquals(listOf(RefreshOutcome.Confirm, RefreshOutcome.Reject, RefreshOutcome.Confirm), outcomes)
    }
}
