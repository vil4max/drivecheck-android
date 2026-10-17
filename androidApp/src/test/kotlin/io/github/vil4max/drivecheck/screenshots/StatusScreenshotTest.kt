package io.github.vil4max.drivecheck.screenshots

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.vil4max.drivecheck.StatusContent
import io.github.vil4max.drivecheck.status.StatusState
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

    private fun capture(name: String, state: StatusState, isChecking: Boolean = false) {
        compose.setContent {
            DriveCheckTheme {
                Surface { StatusContent(state = state, isChecking = isChecking, onRefresh = {}) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/status_$name.png")
    }

    @Test
    fun status_no_alert() = capture("no_alert", StatusState.Quiet(checkedAt))

    @Test
    fun status_air_raid_alert() = capture("air_raid_alert", StatusState.Alarm(checkedAt))

    @Test
    fun status_unavailable() = capture("unavailable", StatusState.Unavailable)

    @Test
    fun status_checking() = capture("checking", StatusState.Checking, isChecking = true)
}
