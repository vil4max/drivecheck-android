package io.github.vil4max.drivecheck.car

import android.content.Intent
import android.content.pm.PackageManager
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.PaneTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.status.StatusPresentation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

// The iOS CarPlay screen as an Android Auto pane (REQ-SURF-006, REQ-SURF-005 as amended by spec 004).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarStatusScreenTest {
    private val at = Instant.parse("2026-10-17T10:07:00Z")
    private val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
    private var refreshes = 0

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
        RuntimeEnvironment.setQualifiers("en")
    }

    private fun status(phase: DisplayPhase, nearby: List<AlertRegion> = emptyList(), isStale: Boolean = false, denied: Boolean = false) =
        CarStatus(StatusPresentation(phase, at, isStale, nearby), AlertRegion.Kharkiv, isChecking = false, isLocationDenied = denied)

    private fun template(status: CarStatus): PaneTemplate {
        val controller = ScreenController(CarStatusScreen(carContext, MutableStateFlow(status)) { refreshes += 1 })
        controller.moveToState(Lifecycle.State.RESUMED)
        return controller.screen.onGetTemplate() as PaneTemplate
    }

    private fun PaneTemplate.rows() = pane!!.rows.map { row -> listOf(row.title.toString()) + row.texts.map { it.toString() } }

    @Test
    fun REQ_SURF_006_one_screen_with_the_status_the_region_its_time_and_refresh() {
        val pane = template(status(DisplayPhase.Alarm))

        assertEquals("Air Raid Alert", pane.header!!.title.toString())
        assertEquals(listOf(listOf("Kharkiv Oblast", "Updated 1:07 PM")), pane.rows())
        val action = pane.pane!!.actions.single()
        assertEquals("Refresh", action.title.toString())
        action.onClickDelegate!!.sendClick(object : OnDoneCallback {})
        assertEquals(1, refreshes)
    }

    @Test
    fun REQ_SURF_005_the_car_shows_nearby_alerts_only_when_there_are_any() {
        val nearby = template(status(DisplayPhase.StayAlert, listOf(AlertRegion.Sumy, AlertRegion.Poltava, AlertRegion.Donetsk)))
        assertEquals("Stay Alert", nearby.header!!.title.toString())
        assertEquals(listOf("Nearby: Sumy Oblast, Poltava Oblast +1"), nearby.rows()[1])

        assertEquals(1, template(status(DisplayPhase.NoAlert)).rows().size, "nothing nearby: no row")
    }

    @Test
    fun REQ_SURF_006_old_data_reads_as_last_known_and_a_denied_location_says_so() {
        val stale = template(status(DisplayPhase.Stale, isStale = true, denied = true))
        assertEquals("No Current Data", stale.header!!.title.toString())
        assertEquals(listOf("Kharkiv Oblast", "Last known: No Alert · 1:07 PM"), stale.rows()[0])
        assertEquals(listOf("No location access"), stale.rows()[1])
    }

    @Test
    fun REQ_SURF_006_a_new_status_redraws_the_screen() {
        val flow = MutableStateFlow(status(DisplayPhase.NoAlert))
        val controller = ScreenController(CarStatusScreen(carContext, flow) {})
        controller.moveToState(Lifecycle.State.RESUMED)
        flow.value = status(DisplayPhase.Alarm)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("Air Raid Alert", (controller.templatesReturned.last() as PaneTemplate).header!!.title.toString())
    }

    @Test
    fun REQ_SURF_006_the_car_app_is_declared_in_the_weather_category() {
        val manager = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager
        val services = manager.queryIntentServices(Intent("androidx.car.app.CarAppService"), PackageManager.GET_RESOLVED_FILTER)
        val service = services.single()
        assertEquals(DriveCheckCarAppService::class.java.name, service.serviceInfo.name)
        assertTrue(service.filter.hasCategory("androidx.car.app.category.WEATHER"))
    }
}
