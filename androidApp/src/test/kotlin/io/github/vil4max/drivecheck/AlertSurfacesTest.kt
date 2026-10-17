package io.github.vil4max.drivecheck

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.service.quicksettings.Tile
import androidx.test.core.app.ApplicationProvider
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// The ongoing notification, the tile and the Details switch, against the stored snapshot.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertSurfacesTest {
    private val now = Instant.parse("2026-10-17T10:07:00Z")
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
        RuntimeEnvironment.setQualifiers("en")
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun store(ageMinutes: Int, kyiv: AlertStatus, alarms: Set<AlertRegion> = emptySet()) {
        val at = now - ageMinutes.minutes
        val snapshot = AlertsSnapshot("t", at, at, AlertRegion.entries.associateWith { if (it in alarms) AlertStatus.Alarm else AlertStatus.Quiet } + (AlertRegion.KyivCity to kyiv))
        AppGraph.store(app).saveSnapshot(snapshot)
        AppGraph.store(app).saveRegion(AlertRegion.KyivCity)
    }

    private fun posted(): Notification? = manager.activeNotifications.singleOrNull()?.notification

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        ?: extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun REQ_SURF_009_an_alarm_posts_an_ongoing_notification_with_region_and_time() {
        store(1, AlertStatus.Alarm)
        AlertNotifier.sync(app, mayPost = true, now = now)

        val notification = posted()!!
        assertEquals("Air Raid Alert", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Kyiv · Updated 1:06 PM", notification.text())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Refresh", notification.actions.single().title.toString())
    }

    @Test
    fun REQ_SURF_009_a_confirmed_all_clear_ends_it_and_no_alarm_posts_nothing() {
        store(1, AlertStatus.Alarm)
        AlertNotifier.sync(app, mayPost = true, now = now)
        store(0, AlertStatus.Quiet)
        AlertNotifier.sync(app, mayPost = true, now = now)
        assertNull(posted())

        AlertNotifier.sync(app, mayPost = true, now = now)
        assertNull(posted())
    }

    @Test
    fun REQ_SURF_003_a_stale_answer_never_ends_it_and_says_it_may_be_outdated() {
        store(1, AlertStatus.Alarm)
        AlertNotifier.sync(app, mayPost = true, now = now)
        store(20, AlertStatus.Quiet)
        AlertNotifier.sync(app, mayPost = true, now = now)

        assertEquals("Kyiv · Updated 12:47 PM\nMay be outdated. Open the app.", posted()!!.text())
    }

    @Test
    fun REQ_SURF_009_the_refresh_action_never_posts_and_the_switch_off_ends_it() {
        store(1, AlertStatus.Alarm)
        AlertNotifier.sync(app, mayPost = false, now = now)
        assertNull(posted())

        AlertNotifier.sync(app, mayPost = true, now = now)
        NotificationSettings(app).isChosen = false
        AlertNotifier.sync(app, mayPost = true, now = now)
        assertNull(posted())
    }

    @Test
    fun REQ_SURF_008_the_switch_reads_off_while_android_blocks_and_keeps_the_choice() {
        val settings = NotificationSettings(app)
        assertEquals(NotificationSwitchState(isOn = true, isBlocked = false), settings.state())

        shadowOf(manager).setNotificationsEnabled(false)
        assertEquals(NotificationSwitchState(isOn = false, isBlocked = true), settings.state())
        assertTrue(settings.isChosen, "the driver's choice is kept")

        shadowOf(manager).setNotificationsEnabled(true)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(NotificationSwitchState(isOn = false, isBlocked = false), settings.state(), "a first tap may still ask")
        assertTrue(settings.needsPermission())
        settings.onPermissionRefused()
        assertEquals(NotificationSwitchState(isOn = false, isBlocked = true), settings.state())
        assertFalse(settings.isAllowed())
    }

    @Test
    fun REQ_ANDROID_007_the_tile_shows_the_short_status_and_the_region() {
        store(1, AlertStatus.Alarm)
        assertEquals(TileContent("Alert", "Kyiv", Tile.STATE_ACTIVE), TileContent.read(app, now))
        store(1, AlertStatus.Quiet, setOf(AlertRegion.KyivOblast))
        assertEquals(TileContent("Stay Alert", "Kyiv", Tile.STATE_ACTIVE), TileContent.read(app, now))
        store(1, AlertStatus.Quiet)
        assertEquals(TileContent("No Alert", "Kyiv", Tile.STATE_INACTIVE), TileContent.read(app, now))
        store(5, AlertStatus.Quiet)
        assertEquals(TileContent("No Current Data", "Kyiv", Tile.STATE_INACTIVE), TileContent.read(app, now))
    }
}
