package io.github.vil4max.drivecheck

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.status.RegionList
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RegionSortTest {
    private val ukrainian = mapOf(
        AlertRegion.IvanoFrankivsk to "Івано-Франківська область",
        AlertRegion.Vinnytsia to "Вінницька область",
        AlertRegion.Volyn to "Волинська область",
        AlertRegion.KyivOblast to "Київська область",
        AlertRegion.Cherkasy to "Черкаська область",
    )

    @Test
    fun REQ_ANDROID_003_ukrainian_names_sort_in_alphabet_order_not_by_code_point() {
        val at = Instant.parse("2026-10-17T10:00:00Z")
        val snapshot = AlertsSnapshot("t", at, at, ukrainian.keys.associateWith { AlertStatus.Quiet })

        val list = RegionList.of(snapshot, AlertRegion.Cherkasy, name = { ukrainian[it] ?: "Я" }, comparator = regionNameOrder(Locale.forLanguageTag("uk")))!!

        assertEquals(
            listOf("Вінницька область", "Волинська область", "Івано-Франківська область", "Київська область", "Черкаська область"),
            list.others.take(5).map { ukrainian.getValue(it.region) },
        )
    }
}
