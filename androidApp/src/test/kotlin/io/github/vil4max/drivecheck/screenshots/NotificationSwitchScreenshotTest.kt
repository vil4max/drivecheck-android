package io.github.vil4max.drivecheck.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.vil4max.drivecheck.NotificationSwitchCard
import io.github.vil4max.drivecheck.NotificationSwitchState
import io.github.vil4max.drivecheck.theme.DriveCheckTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

// The Details notification switch in its three states (REQ-SURF-008, SC-002).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class NotificationSwitchScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val changes = mutableListOf<Boolean>()

    private fun capture(name: String, state: NotificationSwitchState) {
        RuntimeEnvironment.setQualifiers("+uk")
        compose.setContent {
            DriveCheckTheme {
                Surface { NotificationSwitchCard(state, onChange = { changes += it }, onOpenSettings = {}) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/notification_switch_${name}_uk.png")
    }

    private val switch get() = compose.onNodeWithText("Сповіщення під час тривоги")

    @Test
    fun REQ_SURF_008_switch_on() {
        capture("on", NotificationSwitchState(isOn = true, isBlocked = false))
        switch.assertIsOn().performClick()
        assertEquals(listOf(false), changes)
    }

    @Test
    fun REQ_SURF_008_switch_off_can_be_turned_on() {
        capture("off", NotificationSwitchState(isOn = false, isBlocked = false))
        switch.assertIsOff().performClick()
        assertEquals(listOf(true), changes)
    }

    @Test
    fun REQ_SURF_008_switch_blocked_reads_off_and_offers_settings() {
        capture("blocked", NotificationSwitchState(isOn = false, isBlocked = true))
        switch.assertIsOff().assertIsNotEnabled()
        compose.onNodeWithText("Відкрити Параметри").assertExists()
    }
}
