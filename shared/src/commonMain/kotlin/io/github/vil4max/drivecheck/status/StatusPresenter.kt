package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import kotlin.time.Duration
import kotlin.time.Instant

// The traffic light the hero shows: green no alert, yellow stay alert, red alert, grey for old
// or missing data (REQ-SURF-010, REQ-REFRESH-006).
enum class DisplayPhase { Checking, NoAlert, StayAlert, Alarm, Stale, Unavailable, RegionUnavailable }

data class StatusPresentation(
    val phase: DisplayPhase,
    val checkedAt: Instant?,
    val isStale: Boolean,
    val nearbyAlerts: List<AlertRegion>,
)

object StatusPresenter {
    fun present(
        state: StatusState,
        snapshot: AlertsSnapshot?,
        region: AlertRegion,
        now: Instant,
        baseInterval: Duration,
    ): StatusPresentation {
        val checkedAt = when (state) {
            is StatusState.Quiet -> state.checkedAt
            is StatusState.Alarm -> state.checkedAt
            else -> null
        }
        val isStale = checkedAt != null && now - checkedAt > baseInterval * 2
        val phase = when (state) {
            StatusState.Checking -> DisplayPhase.Checking
            StatusState.Unavailable -> DisplayPhase.Unavailable
            StatusState.RegionUnavailable -> DisplayPhase.RegionUnavailable
            is StatusState.Alarm -> DisplayPhase.Alarm
            is StatusState.Quiet -> if (isStale) DisplayPhase.Stale else DisplayPhase.NoAlert
        }
        return StatusPresentation(phase, checkedAt, isStale, nearbyAlerts = emptyList())
    }
}
