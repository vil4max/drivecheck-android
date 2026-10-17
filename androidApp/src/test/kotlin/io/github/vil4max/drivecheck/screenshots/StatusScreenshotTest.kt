package io.github.vil4max.drivecheck.screenshots

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.vil4max.drivecheck.StatusContent
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.status.StatusPresentation
import io.github.vil4max.drivecheck.theme.DriveCheckTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import kotlin.time.Instant

// Renders the Status screen on the JVM; ./gradlew check compares each image with the recorded one
// in src/test/screenshots (record again with ./gradlew recordRoborazziDebug).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StatusScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val checkedAt = Instant.parse("2026-10-17T10:38:00Z")

    @Before
    fun fixTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
    }

    private fun capture(
        name: String,
        phase: DisplayPhase,
        checkedAt: Instant? = this.checkedAt,
        isStale: Boolean = false,
        isChecking: Boolean = false,
        nearby: List<AlertRegion> = emptyList(),
    ) {
        compose.setContent {
            DriveCheckTheme {
                Surface {
                    StatusContent(
                        presentation = StatusPresentation(phase, checkedAt, isStale, nearbyAlerts = nearby),
                        regionName = "Kyiv",
                        isChecking = isChecking,
                        onRefresh = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/status_$name.png")
    }

    @Test
    fun status_no_alert() = capture("no_alert", DisplayPhase.NoAlert)

    @Test
    fun status_air_raid_alert() = capture("air_raid_alert", DisplayPhase.Alarm)

    @Test
    fun status_unavailable() = capture("unavailable", DisplayPhase.Unavailable, checkedAt = null)

    @Test
    fun status_checking() = capture("checking", DisplayPhase.Checking, checkedAt = null, isChecking = true)

    @Test
    fun status_stale() = capture("stale", DisplayPhase.Stale, isStale = true)

    @Test
    fun status_stale_alarm() = capture("stale_alarm", DisplayPhase.Alarm, isStale = true)

    @Test
    fun status_stay_alert() = capture("stay_alert", DisplayPhase.StayAlert, nearby = listOf(AlertRegion.KyivOblast))

    @Test
    fun status_alarm_with_nearby() = capture(
        "alarm_nearby",
        DisplayPhase.Alarm,
        nearby = listOf(AlertRegion.KyivOblast, AlertRegion.Chernihiv, AlertRegion.Poltava),
    )
}
