package io.github.vil4max.drivecheck.refresh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FetchGateTest {
    private val start = Instant.parse("2026-10-17T10:00:00Z")

    @Test
    fun REQ_REFRESH_010_every_trigger_is_held_within_ten_seconds_of_a_success() {
        val gate = FetchGate()
        gate.recordSuccess(start)

        assertTrue(gate.isHeld(isScheduled = false, now = start + 9.seconds))
        assertTrue(gate.isHeld(isScheduled = true, now = start + 9.seconds))
        assertFalse(gate.isHeld(isScheduled = false, now = start + 10.seconds))
        assertFalse(gate.isHeld(isScheduled = true, now = start + 10.seconds))
    }

    @Test
    fun REQ_REFRESH_010_the_first_fetch_and_a_retry_after_a_failure_are_not_held() {
        val gate = FetchGate()

        assertFalse(gate.isHeld(isScheduled = false, now = start))
        assertFalse(gate.isHeld(isScheduled = false, now = start + 1.seconds))
    }

    @Test
    fun REQ_REFRESH_005_the_window_holds_scheduled_polls_and_lets_a_manual_refresh_through() {
        val gate = FetchGate()
        gate.recordRateLimit(until = start + 45.seconds, now = start)

        assertTrue(gate.isHeld(isScheduled = true, now = start + 44.seconds))
        assertFalse(gate.isHeld(isScheduled = false, now = start + 44.seconds))
        assertFalse(gate.isHeld(isScheduled = true, now = start + 45.seconds))
    }

    @Test
    fun REQ_REFRESH_005_a_successful_manual_refresh_does_not_end_the_window() {
        val gate = FetchGate()
        gate.recordRateLimit(until = start + 2.minutes, now = start)
        gate.recordSuccess(start + 10.seconds)

        assertTrue(gate.isRateLimited(start + 30.seconds))
        assertTrue(gate.isHeld(isScheduled = true, now = start + 30.seconds))
        assertEquals(start + 5.minutes, gate.recordRateLimit(until = start + 1.hours(), now = start))
    }

    private fun Int.hours() = (this * 60).minutes
}
