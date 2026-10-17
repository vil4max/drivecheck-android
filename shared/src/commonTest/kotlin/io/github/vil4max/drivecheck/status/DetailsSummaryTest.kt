package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DetailsSummaryTest {
    private val at = Instant.parse("2026-10-17T10:00:00Z")

    private fun snapshot(alarms: Set<AlertRegion>, missing: Set<AlertRegion> = emptySet()) = AlertsSnapshot(
        "t",
        at,
        at,
        (AlertRegion.entries - missing).associateWith { if (it in alarms) AlertStatus.Alarm else AlertStatus.Quiet },
    )

    @Test
    fun REQ_ANDROID_004_a_quiet_region_with_alerts_nearby_reads_as_ios() {
        val alarms = setOf(AlertRegion.KyivOblast, AlertRegion.Poltava, AlertRegion.Kirovohrad, AlertRegion.Kharkiv)
        val lines = DetailsSummary.lines(StatusState.Quiet(at), snapshot(alarms), AlertRegion.Cherkasy, isStale = false)

        assertEquals(
            listOf(
                SummaryLine.RegionQuiet,
                SummaryLine.Nearby(listOf(AlertRegion.KyivOblast, AlertRegion.Poltava), more = 1),
                SummaryLine.CountryAlerts(alerts = 4, total = 25),
            ),
            lines,
        )
    }

    @Test
    fun REQ_ANDROID_004_all_clear_partial_clear_no_data_and_stale() {
        assertEquals(
            listOf(SummaryLine.RegionQuiet, SummaryLine.CountryAllClear(25)),
            DetailsSummary.lines(StatusState.Quiet(at), snapshot(emptySet()), AlertRegion.Lviv, isStale = false),
        )
        assertEquals(
            SummaryLine.CountryPartialClear(24),
            DetailsSummary.lines(StatusState.Quiet(at), snapshot(emptySet(), missing = setOf(AlertRegion.Luhansk)), AlertRegion.Lviv, false).last(),
        )
        assertEquals(
            listOf(SummaryLine.RegionOther(AlertRegion.Lviv, StatusState.Unavailable), SummaryLine.CountryNoData),
            DetailsSummary.lines(StatusState.Unavailable, null, AlertRegion.Lviv, isStale = false),
        )
        assertEquals(listOf(SummaryLine.Stale), DetailsSummary.lines(StatusState.Quiet(at), snapshot(emptySet()), AlertRegion.Lviv, isStale = true))
    }
}
