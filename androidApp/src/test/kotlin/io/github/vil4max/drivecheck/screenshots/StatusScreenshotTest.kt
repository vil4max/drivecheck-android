package io.github.vil4max.drivecheck.screenshots

import androidx.compose.material3.Surface
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.vil4max.drivecheck.StatusContent
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertRegion.Cherkasy
import io.github.vil4max.drivecheck.domain.AlertRegion.Chernihiv
import io.github.vil4max.drivecheck.domain.AlertRegion.Dnipropetrovsk
import io.github.vil4max.drivecheck.domain.AlertRegion.Donetsk
import io.github.vil4max.drivecheck.domain.AlertRegion.Kharkiv
import io.github.vil4max.drivecheck.domain.AlertRegion.KyivCity
import io.github.vil4max.drivecheck.domain.AlertRegion.KyivOblast
import io.github.vil4max.drivecheck.domain.AlertRegion.Luhansk
import io.github.vil4max.drivecheck.domain.AlertRegion.Poltava
import io.github.vil4max.drivecheck.domain.AlertRegion.Sumy
import io.github.vil4max.drivecheck.nameRes
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.status.StatusPresentation
import io.github.vil4max.drivecheck.theme.DriveCheckTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import kotlin.time.Instant

// Renders the Status screen on the JVM in the scenes of the iOS App Store screenshots; ./gradlew
// check compares each image with the recorded one (record again with ./gradlew recordRoborazziDebug).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StatusScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val checkedAt = Instant.parse("2026-10-17T10:07:00Z")

    @Before
    fun fixTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
    }

    private fun capture(
        name: String,
        phase: DisplayPhase,
        region: AlertRegion = KyivCity,
        checkedAt: Instant? = this.checkedAt,
        isStale: Boolean = false,
        isChecking: Boolean = false,
        nearby: List<AlertRegion> = emptyList(),
        locale: String = "uk",
    ) {
        RuntimeEnvironment.setQualifiers("+$locale")
        compose.setContent {
            DriveCheckTheme {
                Surface {
                    StatusContent(
                        presentation = StatusPresentation(phase, checkedAt, isStale, nearbyAlerts = nearby),
                        regionName = stringResource(region.nameRes()),
                        isChecking = isChecking,
                        onRefresh = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/status_${name}_$locale.png")
    }

    private val kharkivNearby = listOf(Sumy, Poltava, Dnipropetrovsk, Donetsk, Luhansk)

    @Test
    fun status_no_alert() = capture("no_alert", DisplayPhase.NoAlert)

    @Test
    fun status_air_raid_alert() = capture("air_raid_alert", DisplayPhase.Alarm, Kharkiv, nearby = kharkivNearby)

    @Test
    fun status_air_raid_alert_en() = capture("air_raid_alert", DisplayPhase.Alarm, Kharkiv, nearby = kharkivNearby, locale = "en")

    @Test
    fun status_air_raid_alert_ru() = capture("air_raid_alert", DisplayPhase.Alarm, Kharkiv, nearby = kharkivNearby, locale = "ru")

    @Test
    fun status_stay_alert() = capture("stay_alert", DisplayPhase.StayAlert, Cherkasy, nearby = listOf(KyivOblast, Poltava, Chernihiv))

    @Test
    fun status_unavailable() = capture("unavailable", DisplayPhase.Unavailable, checkedAt = null)

    @Test
    fun status_checking() = capture("checking", DisplayPhase.Checking, checkedAt = null, isChecking = true)

    @Test
    fun status_stale() = capture("stale", DisplayPhase.Stale, isStale = true)

    @Test
    fun status_stale_alarm() = capture("stale_alarm", DisplayPhase.Alarm, Kharkiv, isStale = true)
}
