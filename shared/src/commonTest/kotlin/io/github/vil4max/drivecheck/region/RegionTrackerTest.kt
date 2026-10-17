@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.vil4max.drivecheck.region

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.provider.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RegionTrackerTest {
    private val clock = TestClock()

    // About 5.6 km per 0.05 degrees of latitude.
    private val kyiv = 50.45 to 30.52
    private fun fix(at: Pair<Double, Double> = kyiv, accuracy: Double = 100.0, ageSeconds: Int = 0) =
        LocationFix(at.first, at.second, accuracy, clock.now - ageSeconds.seconds)

    private class ScriptedGeocoder(private val answers: MutableList<suspend () -> GeoAddress?>) : ReverseGeocoder {
        var calls = 0
        override suspend fun reverseGeocode(latitude: Double, longitude: Double): GeoAddress? {
            calls += 1
            return answers.removeAt(0)()
        }
    }

    private fun ua(city: String?, area: String?) = GeoAddress("UA", city, area)

    private fun geocoder(vararg answers: suspend () -> GeoAddress?) = ScriptedGeocoder(answers.toMutableList())

    @Test
    fun REQ_REGION_005_inaccurate_or_old_fixes_are_dropped() = runTest {
        val geocoder = geocoder()
        val tracker = RegionTracker(geocoder, clock)

        assertEquals(RegionOutcome.Ignored, tracker.evaluate(fix(accuracy = 1500.0), AlertRegion.KyivCity))
        assertEquals(RegionOutcome.Ignored, tracker.evaluate(fix(accuracy = -1.0), AlertRegion.KyivCity))
        assertEquals(RegionOutcome.Ignored, tracker.evaluate(fix(ageSeconds = 61), AlertRegion.KyivCity))
        assertEquals(0, geocoder.calls)
    }

    @Test
    fun REQ_REGION_005_a_settled_answer_needs_60_s_and_5_km_before_the_next_lookup() = runTest {
        val geocoder = geocoder({ ua("Київ", null) }, { ua("Бровари", "Київська область") })
        val tracker = RegionTracker(geocoder, clock)
        tracker.evaluate(fix(), AlertRegion.KyivCity)

        clock.now += 120.seconds
        assertEquals(RegionOutcome.Ignored, tracker.evaluate(fix(), AlertRegion.KyivCity), "no movement")
        assertEquals(1, geocoder.calls)

        clock.now += 1.seconds
        val moved = 50.50 to 30.52
        assertEquals(RegionOutcome.Candidate(AlertRegion.KyivOblast), tracker.evaluate(fix(moved), AlertRegion.KyivCity))
        assertEquals(2, geocoder.calls)
    }

    @Test
    fun REQ_REGION_005_a_lookup_too_soon_is_deferred_until_the_interval_ends() = runTest {
        val geocoder = geocoder({ ua("Київ", null) })
        val tracker = RegionTracker(geocoder, clock)
        tracker.evaluate(fix(), AlertRegion.KyivCity)

        clock.now += 20.seconds
        assertEquals(RegionOutcome.Deferred(40.seconds), tracker.evaluate(fix(50.50 to 30.52), AlertRegion.KyivCity))
    }

    @Test
    fun REQ_REGION_005_a_failed_or_slow_lookup_is_deferred_and_a_retry_accepts_an_old_fix() = runTest {
        val geocoder = geocoder({ error("geocoder down") }, { awaitCancellation() }, { ua("Київ", null) })
        val tracker = RegionTracker(geocoder, clock)
        val first = fix()

        assertEquals(RegionOutcome.Deferred(60.seconds), tracker.evaluate(first, AlertRegion.Lviv))
        clock.now += 60.seconds
        assertEquals(RegionOutcome.Deferred(60.seconds), tracker.evaluate(first, AlertRegion.Lviv, isRetry = true), "15 s timeout")
        clock.now += 60.seconds
        assertEquals(RegionOutcome.Committed(AlertRegion.KyivCity), tracker.evaluate(first, AlertRegion.Lviv, isRetry = true))
    }

    @Test
    fun REQ_REGION_006_the_first_resolve_commits_at_once() = runTest {
        val tracker = RegionTracker(geocoder({ ua("Львів", "Львівська область") }), clock)

        assertEquals(RegionOutcome.Committed(AlertRegion.Lviv), tracker.evaluate(fix(), AlertRegion.KyivCity))
    }

    @Test
    fun REQ_REGION_006_a_switch_commits_after_90_s_of_agreeing_resolves() = runTest {
        val oblast = { ua("Бровари", "Київська область") }
        val tracker = RegionTracker(geocoder({ ua("Київ", null) }, oblast, oblast, oblast), clock)
        val brovary = 50.51 to 30.79
        tracker.evaluate(fix(), AlertRegion.KyivCity)

        clock.now += 61.seconds
        assertEquals(RegionOutcome.Candidate(AlertRegion.KyivOblast), tracker.evaluate(fix(brovary), AlertRegion.KyivCity))
        clock.now += 61.seconds
        assertEquals(RegionOutcome.Candidate(AlertRegion.KyivOblast), tracker.evaluate(fix(brovary), AlertRegion.KyivCity), "61 s, 0 km")
        clock.now += 30.seconds
        assertEquals(RegionOutcome.Deferred(30.seconds), tracker.evaluate(fix(brovary), AlertRegion.KyivCity), "lookups stay 60 s apart")
        clock.now += 30.seconds
        assertEquals(RegionOutcome.Committed(AlertRegion.KyivOblast), tracker.evaluate(fix(brovary), AlertRegion.KyivCity), "122 s")
    }

    @Test
    fun REQ_REGION_006_a_disagreeing_resolve_resets_the_candidate() = runTest {
        val tracker = RegionTracker(
            geocoder({ ua("Київ", null) }, { ua(null, "Київська область") }, { ua(null, "Чернігівська область") }, { ua(null, "Київська область") }),
            clock,
        )
        tracker.evaluate(fix(), AlertRegion.KyivCity)
        clock.now += 61.seconds
        assertEquals(RegionOutcome.Candidate(AlertRegion.KyivOblast), tracker.evaluate(fix(50.51 to 30.79), AlertRegion.KyivCity))
        clock.now += 61.seconds
        assertEquals(RegionOutcome.Candidate(AlertRegion.Chernihiv), tracker.evaluate(fix(50.51 to 30.79), AlertRegion.KyivCity))
        clock.now += 61.seconds
        assertEquals(RegionOutcome.Candidate(AlertRegion.KyivOblast), tracker.evaluate(fix(), AlertRegion.KyivCity))
    }

    @Test
    fun REQ_REGION_008_outside_ukraine_is_reported_and_keeps_no_candidate() = runTest {
        val tracker = RegionTracker(geocoder({ GeoAddress("PL", "Warszawa", "Mazowieckie") }, { GeoAddress(null, null, null) }), clock)

        assertEquals(RegionOutcome.OutsideUkraine, tracker.evaluate(fix(), AlertRegion.Lviv))
        clock.now += 61.seconds
        assertEquals(RegionOutcome.OutsideUkraine, tracker.evaluate(fix(), AlertRegion.Lviv), "no country code counts as outside")
    }

    @Test
    fun REQ_REGION_008_the_sheet_shows_on_leaving_ukraine_and_at_launch_abroad_once_per_stay() {
        val notice = OutsideUkraineNotice()

        assertEquals(true, notice.shouldShow(isOutside = true), "launch abroad")
        assertEquals(false, notice.shouldShow(isOutside = true), "still outside")
        assertEquals(false, notice.shouldShow(isOutside = false))
        assertEquals(true, notice.shouldShow(isOutside = true), "left again")
    }
}
