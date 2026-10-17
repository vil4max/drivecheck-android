package io.github.vil4max.drivecheck.car

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.PaneTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.SessionController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.provider.AlertsProvider
import io.github.vil4max.drivecheck.provider.TransportFailure
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.status.StatusController
import io.github.vil4max.drivecheck.status.StatusPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

// The iOS CarPlay screen as an Android Auto pane (REQ-SURF-006, REQ-SURF-005, REQ-REFRESH-007),
// within the PaneTemplate quota, and the session's refresh wiring (REQ-REFRESH-004).
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

    private fun status(phase: DisplayPhase, nearby: List<AlertRegion> = emptyList(), isStale: Boolean = false, denied: Boolean = false, checking: Boolean = false) =
        CarStatus(StatusPresentation(phase, at, isStale, nearby), AlertRegion.Kharkiv, isChecking = checking, isLocationDenied = denied)

    private fun template(status: CarStatus): PaneTemplate {
        val controller = ScreenController(CarStatusScreen(carContext, MutableStateFlow(status)) { refreshes += 1 })
        controller.moveToState(Lifecycle.State.RESUMED)
        return controller.screen.onGetTemplate() as PaneTemplate
    }

    private fun PaneTemplate.rows() = pane!!.rows.map { row -> listOf(row.title.toString()) + row.texts.map { it.toString() } }

    // What the host compares to tell a refresh from a new template.
    private fun PaneTemplate.shape() = listOf(header!!.title.toString()) + pane!!.rows.map { it.title.toString() }

    @Test
    fun REQ_SURF_006_one_screen_with_the_status_the_region_its_time_and_refresh() {
        val pane = template(status(DisplayPhase.Alarm))

        assertEquals("Drive Check", pane.header!!.title.toString())
        assertEquals(listOf("Status", "Air Raid Alert", "Kharkiv Oblast · Updated 1:07 PM"), pane.rows()[0])
        assertTrue(pane.pane!!.rows[0].image != null, "the alarm row carries its icon")
        val action = pane.pane!!.actions.single()
        assertEquals("Refresh", action.title.toString())
        action.onClickDelegate!!.sendClick(object : OnDoneCallback {})
        assertEquals(1, refreshes)
    }

    @Test
    fun REQ_SURF_005_the_car_names_nearby_alerts_in_a_fixed_row() {
        val nearby = template(status(DisplayPhase.StayAlert, listOf(AlertRegion.Sumy, AlertRegion.Poltava, AlertRegion.Donetsk)))
        assertEquals(listOf("Nearby", "Sumy Oblast, Poltava Oblast +1"), nearby.rows()[1])
        assertEquals(listOf("Nearby", "No alerts nearby"), template(status(DisplayPhase.NoAlert)).rows()[1])
    }

    @Test
    fun REQ_SURF_006_status_changes_are_refreshes_within_the_template_quota() {
        val sequence = listOf(
            status(DisplayPhase.NoAlert),
            status(DisplayPhase.StayAlert, listOf(AlertRegion.Sumy)),
            status(DisplayPhase.Alarm, listOf(AlertRegion.Sumy, AlertRegion.Poltava, AlertRegion.Donetsk)),
            status(DisplayPhase.Alarm, isStale = true, denied = true),
            status(DisplayPhase.Checking, checking = true),
            status(DisplayPhase.Unavailable),
        )
        val shapes = sequence.map { template(it).shape() }.distinct()
        assertEquals(listOf(listOf("Drive Check", "Status", "Nearby")), shapes)
    }

    @Test
    fun REQ_REFRESH_007_stale_data_reads_no_current_data_with_its_age_and_no_marker() {
        val stale = template(status(DisplayPhase.Alarm, isStale = true, denied = true))
        assertEquals(listOf("Status", "No Current Data", "Kharkiv Oblast · Updated 1:07 PM"), stale.rows()[0])
        assertNull(stale.pane!!.rows[0].image, "no status marker on stale data")
        assertEquals(listOf("Nearby", "No alerts nearby", "No location access"), stale.rows()[1])
    }

    @Test
    fun REQ_SURF_006_a_new_status_redraws_the_screen() {
        val flow = MutableStateFlow(status(DisplayPhase.NoAlert))
        val controller = ScreenController(CarStatusScreen(carContext, flow) {})
        controller.moveToState(Lifecycle.State.RESUMED)
        flow.value = status(DisplayPhase.Alarm)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Air Raid Alert", (controller.templatesReturned.last() as PaneTemplate).rows()[0][1])
    }

    @Test
    fun REQ_SURF_006_the_car_app_is_declared_in_the_weather_category() {
        val manager = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager
        val service = manager.queryIntentServices(Intent("androidx.car.app.CarAppService"), PackageManager.GET_RESOLVED_FILTER).single()
        assertEquals(DriveCheckCarAppService::class.java.name, service.serviceInfo.name)
        assertTrue(service.filter.hasCategory("androidx.car.app.category.WEATHER"))
    }

    @Test
    fun REQ_REFRESH_004_the_session_starts_the_cycle_and_its_stop_releases_the_surface() {
        var calls = 0
        val failing = AlertsProvider {
            calls += 1
            throw TransportFailure(TransportFailure.Kind.ConnectionLost)
        }
        // Robolectric's clock, so the 2 s gap passes as the looper idles.
        val looperClock = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(android.os.SystemClock.uptimeMillis())
        }
        val controller = StatusController(failing, looperClock, CoroutineScope(SupervisorJob() + Dispatchers.Main))
        val session = SessionController(DriveCheckCarSession { controller }, carContext, Intent())
        val looper = shadowOf(Looper.getMainLooper())

        session.moveToState(Lifecycle.State.STARTED)
        looper.idle()
        assertEquals(1, calls, "connecting runs the cycle's first attempt")

        session.moveToState(Lifecycle.State.CREATED)
        looper.idleFor(Duration.ofSeconds(30))
        assertEquals(1, calls, "a stopped car stops its cycle and the timer")
    }
}
