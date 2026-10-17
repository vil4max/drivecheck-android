package io.github.vil4max.drivecheck.refresh

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// What the polling interval depends on (REQ-REFRESH-002). Android reads it from the system
// (AndroidRefreshEnvironment); tests pass it directly.
data class RefreshEnvironment(
    val isAlarmActive: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val isThermalSevere: Boolean = false,
    val isDataSaverConstrained: Boolean = false,
) {
    val isConstrained: Boolean get() = isPowerSaveMode || isThermalSevere || isDataSaverConstrained
}

object RefreshPolicy {
    // Above the provider's 3 s server cache, inside which a refetch returns the same data
    // (REQ-REFRESH-010).
    val fetchFloor: Duration = 10.seconds
    val maxRateLimitHold: Duration = 5.minutes

    // Android-only (REQ-ANDROID-001): the floor does not hold after a failure, and a button has
    // no pull gesture to pace it, so any two requests start at least this far apart.
    val minRequestGap: Duration = 2.seconds

    val baselineInterval: Duration = 60.seconds
    val alarmInterval: Duration = 30.seconds
    val constrainedInterval: Duration = 300.seconds
    private const val JITTER_FRACTION = 0.10

    fun baseInterval(environment: RefreshEnvironment): Duration = when {
        environment.isConstrained -> constrainedInterval
        environment.isAlarmActive -> alarmInterval
        else -> baselineInterval
    }

    // jitterUnit in [0, 1] maps to -10 % .. +10 % of the base interval.
    fun interval(environment: RefreshEnvironment, jitterUnit: Double): Duration {
        val signed = jitterUnit.coerceIn(0.0, 1.0) * 2 - 1
        return baseInterval(environment) * (1 + signed * JITTER_FRACTION)
    }
}

// The holds on a fetch (REQ-REFRESH-005, REQ-REFRESH-010, REQ-ANDROID-001), apart from the
// controller so they can be tested with plain instants.
class FetchGate(rateLimitedUntil: Instant? = null) {
    var rateLimitedUntil: Instant? = rateLimitedUntil
        private set
    var lastSuccessAt: Instant? = null
        private set
    private var lastStartAt: Instant? = null

    fun isRateLimited(now: Instant): Boolean = rateLimitedUntil?.let { now < it } ?: false

    fun recordStart(now: Instant) {
        lastStartAt = now
    }

    // A rate-limit window holds scheduled polls only; the floor holds every trigger. The floor
    // counts from the last success, so the first fetch and a retry after a failure go through,
    // spaced only by the minimum gap.
    fun isHeld(isScheduled: Boolean, now: Instant): Boolean {
        if (isScheduled && isRateLimited(now)) return true
        lastStartAt?.let { if (now - it < RefreshPolicy.minRequestGap) return true }
        val last = lastSuccessAt ?: return false
        return now - last < RefreshPolicy.fetchFloor
    }

    // A success inside the window leaves it in place: one answered request does not show the
    // host's pause is over.
    fun recordSuccess(now: Instant) {
        lastSuccessAt = now
    }

    fun recordRateLimit(until: Instant, now: Instant): Instant {
        val capped = minOf(until, now + RefreshPolicy.maxRateLimitHold)
        rateLimitedUntil = capped
        return capped
    }
}
