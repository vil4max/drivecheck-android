@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.vil4max.drivecheck.region

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.provider.AlertsProvider
import io.github.vil4max.drivecheck.status.StatusController
import io.github.vil4max.drivecheck.store.InMemoryStatusStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RegionCoordinatorTest {
    private val epoch = Instant.parse("2026-10-17T10:00:00Z")

    private fun TestScope.clock() = object : Clock {
        override fun now(): Instant = epoch + (testScheduler.currentTime / 1000.0).seconds
    }

    private class Geocoder(var address: () -> GeoAddress?) : ReverseGeocoder {
        var calls = 0
        override suspend fun reverseGeocode(latitude: Double, longitude: Double): GeoAddress? {
            calls += 1
            return address()
        }
    }

    private fun TestScope.setUp(geocoder: Geocoder, store: InMemoryStatusStore = InMemoryStatusStore()): Pair<RegionCoordinator, StatusController> {
        val snapshot = AlertsSnapshot("t", null, epoch, mapOf(AlertRegion.Lviv to AlertStatus.Quiet))
        val controller = StatusController(AlertsProvider { snapshot }, clock(), backgroundScope, store = store)
        val coordinator = RegionCoordinator(RegionTracker(geocoder, clock()), controller, store, backgroundScope)
        return coordinator to controller
    }

    private fun TestScope.fix() = LocationFix(49.84, 24.03, 100.0, clock().now())

    @Test
    fun REQ_REGION_007_a_committed_region_raises_a_dismissible_notice() = runTest {
        val (coordinator, controller) = setUp(Geocoder { GeoAddress("UA", "Львів", "Львівська область") })
        coordinator.onPermission(granted = true)
        coordinator.completeOnboarding()

        coordinator.onFix(fix())
        runCurrent()

        assertEquals(AlertRegion.Lviv, controller.region.value)
        assertEquals(AlertRegion.Lviv, coordinator.notice.value)
        coordinator.dismissNotice()
        assertNull(coordinator.notice.value)
    }

    @Test
    fun REQ_REGION_005_a_deferred_fix_is_evaluated_again_when_the_wait_ends() = runTest {
        var failing = true
        val geocoder = Geocoder { if (failing) error("down") else GeoAddress("UA", "Львів", "Львівська область") }
        val (coordinator, controller) = setUp(geocoder)
        coordinator.onPermission(granted = true)
        coordinator.completeOnboarding()

        coordinator.onFix(fix())
        runCurrent()
        failing = false
        advanceTimeBy(59.seconds)
        runCurrent()
        assertEquals(1, geocoder.calls)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(2, geocoder.calls)
        assertEquals(AlertRegion.Lviv, controller.region.value)
    }

    @Test
    fun REQ_REGION_008_the_sheet_shows_once_and_waits_for_onboarding() = runTest {
        val (coordinator, controller) = setUp(Geocoder { GeoAddress("PL", "Kraków", null) })
        coordinator.onPermission(granted = true)

        coordinator.onFix(fix())
        runCurrent()
        assertFalse(coordinator.showOutsideSheet.value, "no fixes are used before onboarding")

        coordinator.completeOnboarding()
        coordinator.onFix(fix())
        runCurrent()
        assertTrue(coordinator.showOutsideSheet.value)
        assertEquals(AlertRegion.KyivCity, controller.region.value)

        coordinator.dismissOutsideSheet()
        advanceTimeBy(61.seconds)
        coordinator.onFix(fix())
        runCurrent()
        assertFalse(coordinator.showOutsideSheet.value, "still outside: not again")
    }

    @Test
    fun REQ_REGION_010_location_starts_only_after_get_started_and_permission() = runTest {
        val (coordinator, _) = setUp(Geocoder { null })

        assertFalse(coordinator.shouldTrackLocation.value)
        assertFalse(coordinator.shouldRequestPermission.value)
        coordinator.completeOnboarding()
        assertTrue(coordinator.shouldRequestPermission.value, "Get Started asks for location")
        assertFalse(coordinator.shouldTrackLocation.value)
        coordinator.onPermission(granted = true)
        assertTrue(coordinator.shouldTrackLocation.value)
    }

    @Test
    fun REQ_REGION_009_the_prompt_is_asked_once_and_a_later_launch_reads_the_answer() = runTest {
        val store = InMemoryStatusStore()
        val (first, _) = setUp(Geocoder { null }, store)
        first.completeOnboarding()
        assertTrue(first.shouldRequestPermission.value)
        first.onPermission(granted = false)

        val (next, _) = setUp(Geocoder { null }, store)

        assertFalse(next.shouldRequestPermission.value, "iOS asks once")
        assertTrue(next.isLocationDenied.value)
    }

    @Test
    fun REQ_REGION_009_a_one_time_grant_is_not_remembered_so_the_next_launch_asks_again() = runTest {
        val store = InMemoryStatusStore()
        val (first, _) = setUp(Geocoder { null }, store)
        first.completeOnboarding()
        first.onPermission(granted = true)

        val (next, _) = setUp(Geocoder { null }, store)

        assertTrue(next.shouldRequestPermission.value, "Android's 'Only this time', like iOS Allow Once")
        assertFalse(next.isLocationDenied.value)
    }

    @Test
    fun REQ_REGION_009_a_granted_launch_never_shows_the_denied_card() = runTest {
        val store = InMemoryStatusStore().apply { setOnboardingDone() }
        val snapshot = AlertsSnapshot("t", null, epoch, emptyMap())
        val controller = StatusController(AlertsProvider { snapshot }, clock(), backgroundScope, store = store)

        val coordinator = RegionCoordinator(RegionTracker(Geocoder { null }, clock()), controller, store, backgroundScope, initiallyGranted = true)

        assertFalse(coordinator.isLocationDenied.value)
        assertTrue(coordinator.shouldTrackLocation.value)
    }

    @Test
    fun REQ_REGION_005_a_deferred_retry_stops_with_the_screen() = runTest {
        val geocoder = Geocoder { error("down") }
        val (coordinator, _) = setUp(geocoder)
        coordinator.completeOnboarding()
        coordinator.onPermission(granted = true)
        coordinator.onFix(fix())
        runCurrent()

        coordinator.onTrackingStopped()
        advanceTimeBy(120.seconds)
        runCurrent()

        assertEquals(1, geocoder.calls)
    }

    @Test
    fun REQ_REGION_009_denied_access_stops_tracking_and_keeps_the_last_region() = runTest {
        val store = InMemoryStatusStore().apply { saveRegion(AlertRegion.Odesa) }
        val (coordinator, controller) = setUp(Geocoder { null }, store)
        coordinator.completeOnboarding()

        coordinator.onPermission(granted = false)

        assertFalse(coordinator.shouldTrackLocation.value)
        assertTrue(coordinator.isLocationDenied.value)
        assertEquals(AlertRegion.Odesa, controller.region.value)
    }
}
