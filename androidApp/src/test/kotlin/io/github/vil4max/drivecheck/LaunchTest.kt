package io.github.vil4max.drivecheck

import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.status.StatusPresentation
import io.github.vil4max.drivecheck.theme.DriveCheckTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun REQ_LAUNCH_001_the_launch_window_and_splash_are_the_neutral_background() {
        val base = File("src/main/res/values/themes.xml").readText()
        val v31 = File("src/main/res/values-v31/themes.xml").readText()

        assertTrue("""<color name="drivecheck_background">#0C0E11</color>""" in base)
        assertTrue("""android:windowBackground">@color/drivecheck_background""" in base)
        assertTrue("""android:windowSplashScreenBackground">@color/drivecheck_background""" in v31)
        assertTrue("Theme.DriveCheck" in File("src/main/AndroidManifest.xml").readText())
    }

    @Test
    fun REQ_LAUNCH_005_talkback_hears_the_hero_as_one_polite_announcement() {
        compose.setContent {
            DriveCheckTheme {
                Surface {
                    StatusContent(
                        presentation = StatusPresentation(DisplayPhase.Alarm, Instant.parse("2026-10-17T10:07:00Z"), false, emptyList()),
                        regionName = "Kyiv",
                        isChecking = false,
                        onRefresh = {},
                    )
                }
            }
        }

        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertCountEquals(1)
    }
}
