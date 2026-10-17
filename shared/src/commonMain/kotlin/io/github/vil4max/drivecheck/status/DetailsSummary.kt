package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.domain.NearbyRegionPolicy

sealed interface SummaryLine {
    data object RegionQuiet : SummaryLine
    data object RegionAlarm : SummaryLine
    data class RegionOther(val region: AlertRegion, val state: StatusState) : SummaryLine
    data class Nearby(val names: List<AlertRegion>, val more: Int) : SummaryLine
    data object CountryNoData : SummaryLine
    data class CountryAllClear(val total: Int) : SummaryLine
    data class CountryPartialClear(val clear: Int) : SummaryLine
    data class CountryAlerts(val alerts: Int, val total: Int) : SummaryLine
    data object Stale : SummaryLine
}

// The iOS DeterministicStatusDetailsProvider, the only summary on Android (REQ-ANDROID-004):
// stale data says only that; otherwise the region line, the nearby warning and the country line.
object DetailsSummary {
    fun lines(state: StatusState, snapshot: AlertsSnapshot?, region: AlertRegion, isStale: Boolean): List<SummaryLine> {
        if (isStale) return listOf(SummaryLine.Stale)
        val lines = mutableListOf<SummaryLine>()
        lines += when (state) {
            is StatusState.Quiet -> SummaryLine.RegionQuiet
            is StatusState.Alarm -> SummaryLine.RegionAlarm
            else -> SummaryLine.RegionOther(region, state)
        }
        val alarms = snapshot?.statuses?.filterValues { it == AlertStatus.Alarm }?.keys.orEmpty()
        if (state is StatusState.Quiet || state is StatusState.Alarm) {
            NearbyLine.of(NearbyRegionPolicy.activeAlerts(region, alarms))?.let { lines += SummaryLine.Nearby(it.names, it.more) }
        }
        val total = AlertRegion.entries.size
        lines += when {
            snapshot == null || snapshot.statuses.isEmpty() -> SummaryLine.CountryNoData
            alarms.isNotEmpty() -> SummaryLine.CountryAlerts(alarms.size, total)
            snapshot.statuses.size == total -> SummaryLine.CountryAllClear(total)
            else -> SummaryLine.CountryPartialClear(snapshot.statuses.size)
        }
        return lines
    }
}
