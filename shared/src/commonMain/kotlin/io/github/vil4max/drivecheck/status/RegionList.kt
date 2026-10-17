package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot

data class RegionRow(val region: AlertRegion, val status: AlertStatus?)

// The iOS region list (REQ-ANDROID-003): the current region, then the regions under alert, then
// the rest, each section sorted by the localized name.
data class RegionList(val current: RegionRow, val alarms: List<RegionRow>, val others: List<RegionRow>) {
    companion object {
        fun of(snapshot: AlertsSnapshot?, current: AlertRegion, name: (AlertRegion) -> String): RegionList? {
            if (snapshot == null) return null
            val rows = AlertRegion.entries.map { RegionRow(it, snapshot.status(it)) }.sortedBy { name(it.region) }
            return RegionList(
                current = RegionRow(current, snapshot.status(current)),
                alarms = rows.filter { it.status == AlertStatus.Alarm },
                others = rows.filter { it.status != AlertStatus.Alarm },
            )
        }
    }
}
