package io.github.vil4max.drivecheck.region

import io.github.vil4max.drivecheck.domain.AlertRegion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class LocationFix(val latitude: Double, val longitude: Double, val accuracyMeters: Double, val timestamp: Instant)

data class GeoAddress(val countryCode: String?, val cityName: String?, val administrativeArea: String?)

// Throws when the lookup fails; returns null when the place has no address.
fun interface ReverseGeocoder {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeoAddress?
}

sealed interface RegionOutcome {
    data object Ignored : RegionOutcome
    data object Unchanged : RegionOutcome
    data class Candidate(val region: AlertRegion) : RegionOutcome
    data class Committed(val region: AlertRegion) : RegionOutcome
    data object OutsideUkraine : RegionOutcome

    // The fix needs a lookup the throttle or a failed lookup postponed; evaluate it again after
    // this long, even if no newer fix arrives.
    data class Deferred(val retryAfter: Duration) : RegionOutcome
}

// The iOS RegionTracker (REQ-REGION-005, REQ-REGION-006, REQ-REGION-008). One instance per process,
// so "the first resolve of a session" is the first of the process.
class RegionTracker(private val geocoder: ReverseGeocoder, private val clock: Clock) {
    private var lastGeocodeAt: Instant? = null
    private var lastGeocodeAt2: Pair<Double, Double>? = null
    private var hasResolved = false
    private var lastResolveProducedRegion = false
    private var candidate: AlertRegion? = null
    private var candidateSince: Instant? = null
    private var candidateOrigin: Pair<Double, Double>? = null

    var isOutsideUkraine = false
        private set

    // Android-only: with approximate access only, fixes are about 2–3 km accurate, so the iOS 1 km
    // filter would drop every one; 5 km still resolves an oblast (spec 002, REQ-REGION-005).
    var approximateOnly = false

    // isRetry re-evaluates a deferred fix: a parked device may deliver no newer fix, so the deferred
    // one stays the best evidence even once it is older than 60 s.
    suspend fun evaluate(fix: LocationFix, current: AlertRegion, isRetry: Boolean = false): RegionOutcome {
        val now = clock.now()
        if (!isAcceptable(fix, now, checksAge = !isRetry)) return RegionOutcome.Ignored
        return when (val decision = geocodeDecision(fix, now)) {
            GeocodeDecision.Skip -> RegionOutcome.Ignored
            is GeocodeDecision.Wait -> RegionOutcome.Deferred(decision.remaining)
            GeocodeDecision.Geocode -> resolve(fix, current, now)
        }
    }

    private suspend fun resolve(fix: LocationFix, current: AlertRegion, now: Instant): RegionOutcome {
        lastGeocodeAt = now
        lastGeocodeAt2 = fix.latitude to fix.longitude
        lastResolveProducedRegion = false
        val address = try {
            withTimeout(GEOCODE_TIMEOUT) { geocoder.reverseGeocode(fix.latitude, fix.longitude) }
        } catch (timeout: TimeoutCancellationException) {
            return RegionOutcome.Deferred(GEOCODE_MIN_INTERVAL)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return RegionOutcome.Deferred(GEOCODE_MIN_INTERVAL)
        } ?: return RegionOutcome.Unchanged

        if (address.countryCode != "UA") {
            isOutsideUkraine = true
            clearCandidate()
            return RegionOutcome.OutsideUkraine
        }
        isOutsideUkraine = false
        val resolved = AlertRegionResolver.resolve(address.cityName, address.administrativeArea)
            ?: return RegionOutcome.Unchanged
        lastResolveProducedRegion = true
        return consider(resolved, fix.latitude to fix.longitude, now, current)
    }

    private fun consider(resolved: AlertRegion, at: Pair<Double, Double>, now: Instant, current: AlertRegion): RegionOutcome {
        val isFirst = !hasResolved
        hasResolved = true
        if (resolved == current) {
            clearCandidate()
            return RegionOutcome.Unchanged
        }
        // The stored region can be days old, and a process's first resolve is the only fresh
        // evidence, so it commits without hysteresis.
        if (isFirst) {
            clearCandidate()
            return RegionOutcome.Committed(resolved)
        }
        val since = candidateSince
        val origin = candidateOrigin
        if (candidate != resolved || since == null || origin == null) {
            candidate = resolved
            candidateSince = now
            candidateOrigin = at
            return RegionOutcome.Candidate(resolved)
        }
        if (now - since >= HYSTERESIS_MIN_DURATION || distanceMeters(origin, at) >= HYSTERESIS_MIN_DISTANCE_M) {
            clearCandidate()
            return RegionOutcome.Committed(resolved)
        }
        return RegionOutcome.Candidate(resolved)
    }

    private fun isAcceptable(fix: LocationFix, now: Instant, checksAge: Boolean): Boolean =
        fix.accuracyMeters >= 0 &&
            fix.accuracyMeters <= (if (approximateOnly) APPROXIMATE_MAX_ACCURACY_M else MAX_ACCURACY_M) &&
            (!checksAge || now - fix.timestamp <= MAX_FIX_AGE)

    // The interval bounds the lookup rate; it never decides that a needed lookup is dropped. A
    // pending candidate or a resolve without a region is not a settled answer, so 60 s alone applies.
    private fun geocodeDecision(fix: LocationFix, now: Instant): GeocodeDecision {
        val lastAt = lastGeocodeAt ?: return GeocodeDecision.Geocode
        val lastAtPlace = lastGeocodeAt2 ?: return GeocodeDecision.Geocode
        val needsLookup = candidate != null || !lastResolveProducedRegion ||
            distanceMeters(lastAtPlace, fix.latitude to fix.longitude) >= GEOCODE_MIN_DISTANCE_M
        if (!needsLookup) return GeocodeDecision.Skip
        val remaining = GEOCODE_MIN_INTERVAL - (now - lastAt)
        return if (remaining > Duration.ZERO) GeocodeDecision.Wait(remaining) else GeocodeDecision.Geocode
    }

    private fun clearCandidate() {
        candidate = null
        candidateSince = null
        candidateOrigin = null
    }

    private sealed interface GeocodeDecision {
        data object Geocode : GeocodeDecision
        data object Skip : GeocodeDecision
        data class Wait(val remaining: Duration) : GeocodeDecision
    }

    companion object {
        const val MAX_ACCURACY_M = 1000.0
        const val APPROXIMATE_MAX_ACCURACY_M = 5000.0
        val MAX_FIX_AGE = 60.seconds
        val GEOCODE_MIN_INTERVAL = 60.seconds
        const val GEOCODE_MIN_DISTANCE_M = 5000.0
        val HYSTERESIS_MIN_DURATION = 90.seconds
        const val HYSTERESIS_MIN_DISTANCE_M = 5000.0
        val GEOCODE_TIMEOUT = 15.seconds

        // Haversine distance; precise enough for kilometre thresholds.
        fun distanceMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            fun rad(degrees: Double) = degrees * kotlin.math.PI / 180
            val dLat = rad(b.first - a.first)
            val dLon = rad(b.second - a.second)
            val h = sin(dLat / 2).pow(2) + cos(rad(a.first)) * cos(rad(b.first)) * sin(dLon / 2).pow(2)
            return 2 * 6_371_000.0 * asin(sqrt(h))
        }
    }
}

// The outside-Ukraine sheet shows on leaving Ukraine, or at launch abroad, once per stay outside
// (REQ-REGION-008).
class OutsideUkraineNotice {
    private var wasOutside = false

    fun shouldShow(isOutside: Boolean): Boolean {
        val show = isOutside && !wasOutside
        wasOutside = isOutside
        return show
    }
}
