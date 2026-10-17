package io.github.vil4max.drivecheck

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Which fetch each entry point asks for (REQ-PROVIDER-002 as amended by spec 003), and that the
// app, the timed redraw and the tile reach the surfaces.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SurfaceWiringTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val refreshes = mutableListOf<Pair<Boolean, Boolean>>()
    private var updates = 0

    @Before
    fun setUp() {
        Surfaces.refresher = { pending, _, isScheduled, mayPost ->
            refreshes += isScheduled to mayPost
            pending?.finish()
        }
        Surfaces.updater = { _, _ -> updates += 1 }
    }

    @After
    fun tearDown() = Surfaces.resetForTests()

    private fun send(intent: Intent) {
        app.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun widgetIntent(action: String) = Intent(action).setComponent(ComponentName(app, StatusWidgetProvider::class.java))

    @Test
    fun REQ_PROVIDER_002_the_widget_update_is_a_scheduled_fetch_that_may_post() {
        send(widgetIntent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(7)))
        assertEquals(listOf(true to true), refreshes)
    }

    @Test
    fun REQ_PROVIDER_002_the_widget_refresh_is_an_unscheduled_fetch() {
        send(widgetIntent(StatusWidgetProvider.ACTION_REFRESH))
        assertEquals(listOf(false to true), refreshes)
    }

    @Test
    fun REQ_SURF_009_the_notification_refresh_never_posts() {
        send(Intent(app, NotificationRefreshReceiver::class.java))
        assertEquals(listOf(false to false), refreshes)
    }

    @Test
    fun REQ_REFRESH_009_the_timed_redraw_updates_without_a_fetch() {
        send(widgetIntent(StatusWidgetProvider.ACTION_REDRAW))
        assertEquals(emptyList(), refreshes)
        assertEquals(1, updates)
    }

    @Test
    fun REQ_REFRESH_009_the_app_updates_the_surfaces() {
        StatusViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(updates >= 1)
    }

    @Test
    fun REQ_ANDROID_007_the_tile_shows_the_store_and_a_tap_opens_the_app() {
        val service = Robolectric.setupService(StatusTileService::class.java)
        service.onStartListening()
        assertEquals("No Current Data", service.qsTile.label.toString())

        service.onClick()
        assertEquals(ComponentName(app, MainActivity::class.java), shadowOf(app).nextStartedActivity.component)
    }
}
