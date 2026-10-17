package io.github.vil4max.drivecheck.refresh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RefreshPolicyTest {
    private val calm = RefreshEnvironment()

    @Test
    fun REQ_REFRESH_002_base_interval_is_60_30_or_300_seconds() {
        assertEquals(60.seconds, RefreshPolicy.baseInterval(calm))
        assertEquals(30.seconds, RefreshPolicy.baseInterval(calm.copy(isAlarmActive = true)))
        assertEquals(300.seconds, RefreshPolicy.baseInterval(calm.copy(isPowerSaveMode = true)))
        assertEquals(300.seconds, RefreshPolicy.baseInterval(calm.copy(isThermalSevere = true)))
        assertEquals(300.seconds, RefreshPolicy.baseInterval(calm.copy(isDataSaverConstrained = true)))
        assertEquals(
            300.seconds,
            RefreshPolicy.baseInterval(calm.copy(isAlarmActive = true, isPowerSaveMode = true)),
            "a constrained context wins over alarm",
        )
    }

    @Test
    fun REQ_REFRESH_002_jitter_stays_within_ten_percent() {
        assertEquals(54.seconds, RefreshPolicy.interval(calm, jitterUnit = 0.0))
        assertEquals(60.seconds, RefreshPolicy.interval(calm, jitterUnit = 0.5))
        assertEquals(66.seconds, RefreshPolicy.interval(calm, jitterUnit = 1.0))
        assertEquals(66.seconds, RefreshPolicy.interval(calm, jitterUnit = 7.0), "out-of-range input is clamped")
    }
}
