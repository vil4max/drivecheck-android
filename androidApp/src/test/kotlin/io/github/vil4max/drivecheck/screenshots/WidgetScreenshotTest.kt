package io.github.vil4max.drivecheck.screenshots

import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import org.robolectric.Robolectric
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.vil4max.drivecheck.WidgetRenderer
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.surface.WidgetPresenter
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// The widget's RemoteViews applied to a real view and captured (REQ-REFRESH-009, REQ-SURF-003).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w180dp-h180dp-xxhdpi")
class WidgetScreenshotTest {
    private val now = Instant.parse("2026-10-17T10:07:00Z")

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
        RuntimeEnvironment.setQualifiers("+uk")
    }

    private fun render(name: String, ageMinutes: Int, kyiv: AlertStatus, alarms: Set<AlertRegion> = emptySet()): View {
        val at = now - ageMinutes.minutes
        val snapshot = AlertsSnapshot("t", at, at, AlertRegion.entries.associateWith { if (it in alarms) AlertStatus.Alarm else AlertStatus.Quiet } + (AlertRegion.KyivCity to kyiv))
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val parent = FrameLayout(activity).apply { setBackgroundColor(android.graphics.Color.rgb(40, 43, 49)) }
        val view = WidgetRenderer.render(activity, WidgetPresenter.present(snapshot, AlertRegion.KyivCity, now)).apply(activity, parent)
        parent.addView(view, FrameLayout.LayoutParams(540, 540))
        activity.setContentView(parent, android.view.ViewGroup.LayoutParams(540, 540))
        parent.captureRoboImage("src/test/screenshots/widget_${name}_uk.png")
        return view
    }

    private fun View.text(id: Int) = (findViewById<android.widget.TextView>(id)).text.toString()

    @Test
    fun REQ_REFRESH_009_widget_fresh_quiet() {
        val view = render("fresh_quiet", 1, AlertStatus.Quiet)
        assertEquals("Тривоги немає", view.text(io.github.vil4max.drivecheck.R.id.widget_status))
        assertEquals("Оновлено 13:06", view.text(io.github.vil4max.drivecheck.R.id.widget_time))
    }

    @Test
    fun REQ_REFRESH_009_widget_aging_quiet_is_grey_with_a_warning() {
        val view = render("aging_quiet", 5, AlertStatus.Quiet)
        assertEquals("⚠ Оновлено 13:02", view.text(io.github.vil4max.drivecheck.R.id.widget_time))
    }

    @Test
    fun REQ_SURF_003_widget_expired_alarm_stays_red_and_asks_to_open_the_app() {
        val view = render("expired_alarm", 12, AlertStatus.Alarm)
        assertEquals("Тривога", view.text(io.github.vil4max.drivecheck.R.id.widget_status))
        assertEquals("⚠ Оновлено 12:55\nВідкрийте застосунок, щоб оновити", view.text(io.github.vil4max.drivecheck.R.id.widget_time))
    }

    @Test
    fun REQ_SURF_010_widget_stay_alert() {
        val view = render("stay_alert", 1, AlertStatus.Quiet, setOf(AlertRegion.KyivOblast))
        assertEquals("Будьте уважні", view.text(io.github.vil4max.drivecheck.R.id.widget_status))
    }
}
