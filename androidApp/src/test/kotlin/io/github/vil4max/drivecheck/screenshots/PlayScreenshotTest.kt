package io.github.vil4max.drivecheck.screenshots

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.vil4max.drivecheck.DetailsScreen
import io.github.vil4max.drivecheck.MapCard
import io.github.vil4max.drivecheck.MapCardState
import io.github.vil4max.drivecheck.NotificationSwitchState
import io.github.vil4max.drivecheck.RegionListScreen
import io.github.vil4max.drivecheck.StatusContent
import io.github.vil4max.drivecheck.Tab
import io.github.vil4max.drivecheck.TabBar
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertRegion.Poltava
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.localizedRegionList
import io.github.vil4max.drivecheck.nameRes
import io.github.vil4max.drivecheck.status.DetailsSummary
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.status.StatusPresentation
import io.github.vil4max.drivecheck.status.StatusState
import io.github.vil4max.drivecheck.theme.DriveCheckTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import kotlin.time.Instant

// The Google Play phone screenshots, after the iOS App Store set 02–06. Play accepts at most a 2:1
// ratio, so the device is 1080 × 2160 rather than the Pixel 7 of the other screenshot tests.
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h720dp-xxhdpi")
class PlayScreenshotTest(private val locale: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "uk", "ru")
    }

    @get:Rule
    val compose = createComposeRule()

    private val at = Instant.parse("2026-10-17T10:07:00Z")
    // The regions red on the map fixture, so each screen agrees with the picture under it.
    private val alarms = listOf(
        AlertRegion.Chernihiv, AlertRegion.Dnipropetrovsk, AlertRegion.Donetsk, AlertRegion.Kharkiv,
        AlertRegion.Kirovohrad, AlertRegion.Luhansk, AlertRegion.Mykolaiv, AlertRegion.Sumy, AlertRegion.Zaporizhzhia,
    )
    private val snapshot = AlertsSnapshot("t", at, at, AlertRegion.entries.associateWith { if (it in alarms) AlertStatus.Alarm else AlertStatus.Quiet })

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
        RuntimeEnvironment.setQualifiers("+$locale")
    }

    // The real upstream raster, as in ShellScreenshotTest.
    private fun map() = MapCardState.Loaded(
        javaClass.getResourceAsStream("/ubilling-map-nightmode.png")!!.use { BitmapFactory.decodeStream(it) }.asImageBitmap(),
        alarms,
        minutes = 1,
    )

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { DriveCheckTheme { Surface { content() } } }
        compose.onRoot().captureRoboImage("../release/play/screenshots/$locale/$name.png")
    }

    @Composable
    private fun Status(phase: DisplayPhase, region: AlertRegion, nearby: List<AlertRegion>) {
        Box(Modifier.fillMaxSize()) {
            StatusContent(
                presentation = StatusPresentation(phase, at, false, nearby),
                regionName = stringResource(region.nameRes()),
                isChecking = false,
                onRefresh = {},
                map = { modifier -> MapCard(map(), onOpen = {}, onRetry = {}, modifier = modifier) },
            )
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { TabBar(Tab.Status, onSelect = {}) }
        }
    }

    @Test
    fun play_01_status_stay_alert() = capture("01-status-stay-alert") {
        Status(DisplayPhase.StayAlert, Poltava, listOf(AlertRegion.Sumy, AlertRegion.Kharkiv, AlertRegion.Dnipropetrovsk, AlertRegion.Kirovohrad))
    }

    @Test
    fun play_02_status_no_alert() = capture("02-status-no-alert") { Status(DisplayPhase.NoAlert, AlertRegion.Lviv, emptyList()) }

    @Test
    fun play_03_status_alert() = capture("03-status-alert") {
        Status(DisplayPhase.Alarm, AlertRegion.Kharkiv, listOf(AlertRegion.Sumy, AlertRegion.Luhansk, AlertRegion.Donetsk, AlertRegion.Dnipropetrovsk))
    }

    @Test
    fun play_04_region_list() = capture("04-region-list") {
        RegionListScreen(localizedRegionList(snapshot, Poltava, LocalResources.current)!!, onBack = {})
    }

    @Test
    fun play_05_details() = capture("05-details") {
        Box(Modifier.fillMaxSize()) {
            DetailsScreen(
                lines = DetailsSummary.lines(StatusState.Quiet(at), snapshot, Poltava, isStale = false),
                countryStatuses = AlertRegion.entries.map { snapshot.status(it) },
                alertRegions = alarms,
                locationDenied = false,
                onOpenSettings = {},
                onOpenSource = {},
                versionName = "1.0.0",
                versionCode = 1,
                notification = NotificationSwitchState(isOn = true, isBlocked = false),
            )
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { TabBar(Tab.Details, onSelect = {}) }
        }
    }
}
