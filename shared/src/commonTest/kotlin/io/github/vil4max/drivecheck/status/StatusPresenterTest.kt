package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StatusPresenterTest {
    private val checkedAt = Instant.parse("2026-10-17T10:00:00Z")

    private fun snapshot(kyiv: AlertStatus) = AlertsSnapshot("test", checkedAt, checkedAt, mapOf(AlertRegion.KyivCity to kyiv))

    private fun present(kyiv: AlertStatus, age: Int) = StatusPresenter.present(
        state = if (kyiv == AlertStatus.Alarm) StatusState.Alarm(checkedAt) else StatusState.Quiet(checkedAt),
        snapshot = snapshot(kyiv),
        region = AlertRegion.KyivCity,
        now = checkedAt + age.seconds,
        baseInterval = 60.seconds,
    )

    @Test
    fun REQ_REFRESH_006_a_quiet_snapshot_older_than_twice_the_base_interval_is_stale() {
        assertEquals(DisplayPhase.NoAlert, present(AlertStatus.Quiet, age = 120).phase)
        assertEquals(DisplayPhase.Stale, present(AlertStatus.Quiet, age = 121).phase)
    }

    private fun presentWith(kyiv: AlertStatus, alarms: List<AlertRegion>, age: Int = 0): StatusPresentation {
        val statuses = AlertRegion.entries.associateWith { if (it in alarms) AlertStatus.Alarm else AlertStatus.Quiet } +
            (AlertRegion.KyivCity to kyiv)
        return StatusPresenter.present(
            state = if (kyiv == AlertStatus.Alarm) StatusState.Alarm(checkedAt) else StatusState.Quiet(checkedAt),
            snapshot = AlertsSnapshot("test", checkedAt, checkedAt, statuses),
            region = AlertRegion.KyivCity,
            now = checkedAt + age.seconds,
            baseInterval = 60.seconds,
        )
    }

    @Test
    fun REQ_SURF_010_a_surrounded_quiet_region_shows_stay_alert_and_keeps_the_nearby_line() {
        val presentation = presentWith(AlertStatus.Quiet, alarms = listOf(AlertRegion.KyivOblast))

        assertEquals(DisplayPhase.StayAlert, presentation.phase)
        assertEquals(listOf(AlertRegion.KyivOblast), presentation.nearbyAlerts)
    }

    @Test
    fun REQ_SURF_010_stale_data_never_turns_yellow() {
        assertEquals(DisplayPhase.Stale, presentWith(AlertStatus.Quiet, listOf(AlertRegion.KyivOblast), age = 600).phase)
    }

    @Test
    fun REQ_SURF_005_nearby_alerts_show_in_alarm_and_quiet_and_not_when_none() {
        assertEquals(listOf(AlertRegion.Poltava), presentWith(AlertStatus.Alarm, listOf(AlertRegion.Poltava)).nearbyAlerts)
        val quietOneNear = presentWith(AlertStatus.Quiet, listOf(AlertRegion.Poltava))
        assertEquals(DisplayPhase.NoAlert, quietOneNear.phase, "1 of 5 neighbours is not surrounded")
        assertEquals(listOf(AlertRegion.Poltava), quietOneNear.nearbyAlerts)
        assertEquals(emptyList(), presentWith(AlertStatus.Quiet, listOf(AlertRegion.Lviv)).nearbyAlerts)
    }

    @Test
    fun REQ_SURF_005_the_line_names_two_regions_and_counts_the_rest() {
        val three = listOf(AlertRegion.KyivOblast, AlertRegion.Chernihiv, AlertRegion.Poltava)

        assertEquals(NearbyLine(listOf(AlertRegion.KyivOblast, AlertRegion.Chernihiv), more = 1), NearbyLine.of(three))
        assertEquals(NearbyLine(listOf(AlertRegion.KyivOblast), more = 0), NearbyLine.of(three.take(1)))
        assertEquals(null, NearbyLine.of(emptyList()))
    }

    @Test
    fun REQ_REFRESH_006_a_stale_alarm_stays_alarm() {
        val stale = present(AlertStatus.Alarm, age = 600)

        assertEquals(DisplayPhase.Alarm, stale.phase)
        assertEquals(true, stale.isStale)
    }
}
