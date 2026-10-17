package io.github.vil4max.drivecheck

import androidx.test.core.app.ApplicationProvider
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalizedRegionListTest {
    @Test
    fun REQ_ANDROID_003_the_app_list_sorts_ukrainian_names_in_alphabet_order() {
        RuntimeEnvironment.setQualifiers("+uk")
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val at = Instant.parse("2026-10-17T10:00:00Z")
        val alarms = setOf(AlertRegion.IvanoFrankivsk, AlertRegion.Vinnytsia, AlertRegion.Kharkiv)
        val snapshot = AlertsSnapshot("t", at, at, AlertRegion.entries.associateWith { if (it in alarms) AlertStatus.Alarm else AlertStatus.Quiet })

        val list = localizedRegionList(snapshot, AlertRegion.Cherkasy, resources)!!

        assertEquals(listOf(AlertRegion.Vinnytsia, AlertRegion.IvanoFrankivsk, AlertRegion.Kharkiv), list.alarms.map { it.region })
    }
}
