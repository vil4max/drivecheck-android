package io.github.vil4max.drivecheck.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.vil4max.drivecheck.OnboardingScreen
import io.github.vil4max.drivecheck.OutsideUkraineContent
import io.github.vil4max.drivecheck.RegionChangedNotice
import io.github.vil4max.drivecheck.StatusContent
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.nameRes
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.status.StatusPresentation
import io.github.vil4max.drivecheck.theme.DriveCheckColors
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class LocationScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
    }

    private fun capture(name: String, locale: String = "uk", content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("+$locale")
        compose.setContent { DriveCheckTheme { Surface { content() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_$locale.png")
    }

    @Test
    fun onboarding_uk() = capture("onboarding") { OnboardingScreen(onGetStarted = {}) }

    @Test
    fun onboarding_en() = capture("onboarding", "en") { OnboardingScreen(onGetStarted = {}) }

    @Test
    fun outside_ukraine_sheet() = capture("outside_ukraine") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(color = DriveCheckColors.buttonFill) { OutsideUkraineContent(onGotIt = {}) }
        }
    }

    @Test
    fun region_changed_notice() = capture("region_changed") {
        Box(Modifier.fillMaxSize()) {
            StatusContent(
                presentation = StatusPresentation(DisplayPhase.NoAlert, Instant.parse("2026-10-17T10:07:00Z"), false, emptyList()),
                regionName = stringResource(AlertRegion.Lviv.nameRes()),
                isChecking = false,
                onRefresh = {},
            )
            RegionChangedNotice(
                regionName = stringResource(AlertRegion.Lviv.nameRes()),
                onDismiss = {},
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            )
        }
    }

    @Test
    fun location_denied() = capture("location_denied") {
        StatusContent(
            presentation = StatusPresentation(DisplayPhase.NoAlert, Instant.parse("2026-10-17T10:07:00Z"), false, emptyList()),
            regionName = stringResource(AlertRegion.KyivCity.nameRes()),
            isChecking = false,
            onRefresh = {},
            locationDenied = true,
        )
    }
}
