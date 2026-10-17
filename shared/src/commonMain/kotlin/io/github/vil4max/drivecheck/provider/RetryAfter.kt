package io.github.vil4max.drivecheck.provider

import kotlinx.datetime.format.DateTimeComponents
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal object RetryAfter {
    // The longest any 429 holds requests back (REQ-REFRESH-005), so a broken or hostile header
    // cannot silence the app for hours.
    val maxHold: Duration = 5.minutes
    private val backoffStart: Duration = 30.seconds

    fun deadline(header: String?, now: Instant, attempt: Int): Instant {
        val requested = header?.let { requestedDeadline(it.trim(), now) }
        if (requested != null) {
            return requested.coerceIn(now, now + maxHold)
        }
        val backoff = backoffStart * 2.0.pow(maxOf(0, attempt - 1))
        return now + minOf(backoff, maxHold)
    }

    private fun requestedDeadline(header: String, now: Instant): Instant? {
        header.toDoubleOrNull()?.let { seconds ->
            return if (seconds.isFinite()) now + seconds.seconds else null
        }
        return runCatching {
            DateTimeComponents.Formats.RFC_1123.parse(header).toInstantUsingOffset()
        }.getOrNull()
    }
}
