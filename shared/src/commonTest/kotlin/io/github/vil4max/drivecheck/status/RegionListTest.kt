package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class RegionListTest {
    private val now = Instant.parse("2026-10-17T10:00:00Z")
    private val alarms = setOf(AlertRegion.Kharkiv, AlertRegion.Chernihiv, AlertRegion.KyivCity)
    private val snapshot = AlertsSnapshot(
        "t",
        null,
        now,
        AlertRegion.entries.associateWith { if (it in alarms) AlertStatus.Alarm else AlertStatus.Quiet },
    )

    @Test
    fun REQ_ANDROID_003_alerts_come_first_and_each_section_is_sorted_by_name() {
        val list = RegionList.of(snapshot, current = AlertRegion.Cherkasy, name = { it.name })!!

        assertEquals(RegionRow(AlertRegion.Cherkasy, AlertStatus.Quiet), list.current)
        assertEquals(listOf(AlertRegion.Chernihiv, AlertRegion.Kharkiv, AlertRegion.KyivCity), list.alarms.map { it.region })
        assertEquals(22, list.others.size)
        assertEquals(list.others.map { it.region.name }.sorted(), list.others.map { it.region.name })
    }

    @Test
    fun REQ_ANDROID_003_without_a_snapshot_there_is_no_list() {
        assertNull(RegionList.of(null, AlertRegion.KyivCity, name = { it.name }))
    }
}
