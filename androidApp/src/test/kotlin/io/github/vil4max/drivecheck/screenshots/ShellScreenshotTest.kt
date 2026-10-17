package io.github.vil4max.drivecheck.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.vil4max.drivecheck.DetailsScreen
import io.github.vil4max.drivecheck.MapCard
import io.github.vil4max.drivecheck.MapCardState
import io.github.vil4max.drivecheck.RegionListScreen
import io.github.vil4max.drivecheck.StatusContent
import io.github.vil4max.drivecheck.Tab
import io.github.vil4max.drivecheck.TabBar
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertRegion.Cherkasy
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.nameRes
import io.github.vil4max.drivecheck.localizedRegionList
import androidx.compose.ui.platform.LocalResources
import io.github.vil4max.drivecheck.status.DetailsSummary
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.status.RegionList
import io.github.vil4max.drivecheck.status.StatusPresentation
import io.github.vil4max.drivecheck.status.StatusState
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

// The iOS screenshots 02–06 as Android scenes: Status with the map and the tab bar, the region
// list and Details.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ShellScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val at = Instant.parse("2026-10-17T10:07:00Z")
    private val alarms = listOf(
        AlertRegion.Chernihiv, AlertRegion.Dnipropetrovsk, AlertRegion.Donetsk, AlertRegion.Kharkiv,
        AlertRegion.Kirovohrad, AlertRegion.KyivCity, AlertRegion.KyivOblast, AlertRegion.Luhansk,
        AlertRegion.Poltava, AlertRegion.Sumy, AlertRegion.Zaporizhzhia,
    )
    private val snapshot = AlertsSnapshot("t", at, at, AlertRegion.entries.associateWith { if (it in alarms) AlertStatus.Alarm else AlertStatus.Quiet })

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
    }

    // The real upstream raster: https://ubilling.net.ua/aerialalerts/?map=nightmode, fetched once
    // on 2026-10-17 (src/test/resources/ubilling-map-nightmode.png).
    private fun fakeMap(): ImageBitmap {
        val stream = javaClass.getResourceAsStream("/ubilling-map-nightmode.png") ?: error("map fixture missing")
        return stream.use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }

    private fun capture(name: String, locale: String = "uk", content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("+$locale")
        compose.setContent { DriveCheckTheme { Surface { content() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_$locale.png")
    }

    @Composable
    private fun StatusWithMap(mapState: MapCardState, phase: DisplayPhase = DisplayPhase.StayAlert) {
        Box(Modifier.fillMaxSize()) {
            StatusContent(
                presentation = StatusPresentation(phase, at, false, listOf(AlertRegion.KyivOblast, AlertRegion.Poltava, AlertRegion.Kirovohrad)),
                regionName = stringResource(Cherkasy.nameRes()),
                isChecking = false,
                onRefresh = {},
                map = { modifier -> MapCard(mapState, onOpen = {}, onRetry = {}, modifier = modifier) },
            )
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { TabBar(Tab.Status, onSelect = {}) }
        }
    }

    @Test
    fun REQ_ANDROID_005_the_shell_has_status_and_details_tabs() {
        capture("shell_status") { StatusWithMap(MapCardState.Loaded(fakeMap(), alarms, minutes = 1)) }
        compose.onNodeWithText("Статус").assertExists()
        compose.onNodeWithText("Деталі").assertExists()
    }

    @Test
    fun REQ_SURF_012_the_status_title_area_has_no_bar_behind_it() =
        capture("shell_status", "en") { StatusWithMap(MapCardState.Loaded(fakeMap(), alarms, minutes = 1)) }

    @Test
    fun REQ_ANDROID_002_map_loading_and_failed() {
        capture("map_loading") { StatusWithMap(MapCardState.Loading, DisplayPhase.NoAlert) }
    }

    @Test
    fun REQ_ANDROID_002_map_failed() = capture("map_failed") { StatusWithMap(MapCardState.Failed, DisplayPhase.NoAlert) }

    // As the app builds it: localized names in the locale's alphabet order.
    @Composable
    private fun localizedList(): RegionList = localizedRegionList(snapshot, Cherkasy, LocalResources.current)!!

    @Test
    fun REQ_ANDROID_003_region_list() = capture("region_list", "en") { RegionListScreen(localizedList(), onBack = {}) }

    @Test
    fun REQ_ANDROID_003_region_list_uk() = capture("region_list") { RegionListScreen(localizedList(), onBack = {}) }

    @Test
    fun REQ_ANDROID_004_details() = capture("details", "en") {
        Box(Modifier.fillMaxSize()) {
            DetailsScreen(
                lines = DetailsSummary.lines(StatusState.Quiet(at), snapshot, Cherkasy, isStale = false),
                countryStatuses = AlertRegion.entries.map { snapshot.status(it) },
                alertRegions = alarms,
                locationDenied = false,
                onOpenSettings = {},
                onOpenSource = {},
                versionName = "0.1.0",
                versionCode = 1,
            )
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { TabBar(Tab.Details, onSelect = {}) }
        }
    }

    @Test
    fun REQ_ANDROID_004_details_uk() = capture("details") {
        DetailsScreen(
            lines = DetailsSummary.lines(StatusState.Quiet(at), snapshot, Cherkasy, isStale = false),
            countryStatuses = AlertRegion.entries.map { snapshot.status(it) },
            alertRegions = alarms,
            locationDenied = true,
            onOpenSettings = {},
            onOpenSource = {},
            versionName = "0.1.0",
            versionCode = 1,
        )
    }
}
