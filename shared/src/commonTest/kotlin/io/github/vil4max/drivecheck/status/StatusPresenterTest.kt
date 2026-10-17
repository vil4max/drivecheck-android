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

    @Test
    fun REQ_REFRESH_006_a_stale_alarm_stays_alarm() {
        val stale = present(AlertStatus.Alarm, age = 600)

        assertEquals(DisplayPhase.Alarm, stale.phase)
        assertEquals(true, stale.isStale)
    }
}
