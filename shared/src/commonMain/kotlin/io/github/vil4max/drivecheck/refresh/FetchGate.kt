package io.github.vil4max.drivecheck.refresh

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

object RefreshPolicy {
    // Above the provider's 3 s server cache, inside which a refetch returns the same data
    // (REQ-REFRESH-010).
    val fetchFloor: Duration = 10.seconds
    val maxRateLimitHold: Duration = 5.minutes

    // Android-only (REQ-ANDROID-001): the floor does not hold after a failure, and a button has
    // no pull gesture to pace it, so any two requests start at least this far apart.
    val minRequestGap: Duration = 2.seconds
}

// The two holds on a fetch (REQ-REFRESH-005, REQ-REFRESH-010), apart from the controller so they
// can be tested with plain instants.
class FetchGate {
    var rateLimitedUntil: Instant? = null
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
