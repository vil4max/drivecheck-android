package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.domain.NearbyRegionPolicy
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

// The nearby line names at most two regions and counts the rest, as on iOS (REQ-SURF-005).
data class NearbyLine(val names: List<AlertRegion>, val more: Int) {
    companion object {
        private const val MAX_NAMES = 2

        fun of(regions: List<AlertRegion>): NearbyLine? =
            if (regions.isEmpty()) null else NearbyLine(regions.take(MAX_NAMES), (regions.size - MAX_NAMES).coerceAtLeast(0))
    }
}

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
        val alarms = snapshot?.statuses?.filterValues { it == AlertStatus.Alarm }?.keys.orEmpty()
        val nearby = if (checkedAt != null) NearbyRegionPolicy.activeAlerts(region, alarms) else emptyList()
        val phase = when (state) {
            StatusState.Checking -> DisplayPhase.Checking
            StatusState.Unavailable -> DisplayPhase.Unavailable
            StatusState.RegionUnavailable -> DisplayPhase.RegionUnavailable
            is StatusState.Alarm -> DisplayPhase.Alarm
            is StatusState.Quiet -> when {
                isStale -> DisplayPhase.Stale
                NearbyRegionPolicy.isSurrounded(region, alarms) -> DisplayPhase.StayAlert
                else -> DisplayPhase.NoAlert
            }
        }
        return StatusPresentation(phase, checkedAt, isStale, nearby)
    }
}
