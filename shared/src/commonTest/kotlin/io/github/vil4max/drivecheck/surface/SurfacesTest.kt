@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.vil4max.drivecheck.surface

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.provider.AlertsProvider
import io.github.vil4max.drivecheck.provider.ProviderFailure
import io.github.vil4max.drivecheck.provider.TestClock
import io.github.vil4max.drivecheck.provider.TransportFailure
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.store.InMemoryStatusStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SurfacesTest {
    private val now = Instant.parse("2026-10-17T10:00:00Z")

    private fun snapshot(ageMinutes: Int, kyiv: AlertStatus, alarms: Set<AlertRegion> = emptySet()): AlertsSnapshot {
        val at = now - ageMinutes.minutes
        return AlertsSnapshot("t", at, at, AlertRegion.entries.associateWith { if (it in alarms) AlertStatus.Alarm else AlertStatus.Quiet } + (AlertRegion.KyivCity to kyiv))
    }

    private fun widget(ageMinutes: Int, kyiv: AlertStatus, alarms: Set<AlertRegion> = emptySet()) =
        WidgetPresenter.present(snapshot(ageMinutes, kyiv, alarms), AlertRegion.KyivCity, now)

    @Test
    fun REQ_REFRESH_009_tiers_at_3_and_10_minutes_keep_the_real_status() {
        assertEquals(FreshnessTier.Fresh, widget(2, AlertStatus.Quiet).tier)
        assertEquals(DisplayPhase.NoAlert, widget(2, AlertStatus.Quiet).phase)
        assertEquals(FreshnessTier.Aging, widget(3, AlertStatus.Quiet).tier)
        assertEquals(DisplayPhase.Stale, widget(3, AlertStatus.Quiet).phase, "aging quiet turns light grey")
        assertEquals(FreshnessTier.Expired, widget(10, AlertStatus.Alarm).tier)
        assertEquals(DisplayPhase.Alarm, widget(10, AlertStatus.Alarm).phase, "a known alarm stays red")
    }

    @Test
    fun REQ_SURF_003_the_widget_warns_with_age_and_asks_to_open_the_app_when_expired() {
        assertEquals(false, widget(2, AlertStatus.Quiet).showsWarning)
        assertEquals(true, widget(5, AlertStatus.Quiet).showsWarning)
        assertEquals(false, widget(5, AlertStatus.Quiet).asksToOpenApp)
        assertEquals(true, widget(10, AlertStatus.Alarm).asksToOpenApp)
        assertEquals(WidgetPresentation.Missing, WidgetPresenter.present(null, AlertRegion.KyivCity, now))
    }

    @Test
    fun REQ_SURF_010_the_widget_and_tile_show_stay_alert_only_on_fresh_data() {
        val surrounded = setOf(AlertRegion.KyivOblast)
        assertEquals(DisplayPhase.StayAlert, widget(1, AlertStatus.Quiet, surrounded).phase)
        assertEquals(DisplayPhase.Stale, widget(4, AlertStatus.Quiet, surrounded).phase)
        assertEquals(TileState.Active, TilePresenter.present(widget(1, AlertStatus.Quiet, surrounded)).state)
    }

    @Test
    fun REQ_ANDROID_007_the_tile_labels_and_states_follow_the_status() {
        assertEquals(TileLabel(DisplayPhase.Alarm, TileState.Active), TilePresenter.present(widget(1, AlertStatus.Alarm)))
        assertEquals(TileLabel(DisplayPhase.NoAlert, TileState.Inactive), TilePresenter.present(widget(1, AlertStatus.Quiet)))
        assertEquals(TileLabel(DisplayPhase.Stale, TileState.Inactive), TilePresenter.present(widget(20, AlertStatus.Quiet)))
        assertEquals(TileLabel(DisplayPhase.Stale, TileState.Unavailable), TilePresenter.present(WidgetPresentation.Missing))
    }

    @Test
    fun REQ_SURF_009_the_notification_follows_confirmed_alarms_only() {
        assertEquals(NotificationAction.Post, AlertNotificationPolicy.decide(isPosted = false, AlertStatus.Alarm, confirmed = true, allowed = true))
        assertEquals(NotificationAction.Update, AlertNotificationPolicy.decide(isPosted = true, AlertStatus.Alarm, confirmed = true, allowed = true))
        assertEquals(NotificationAction.End, AlertNotificationPolicy.decide(isPosted = true, AlertStatus.Quiet, confirmed = true, allowed = true))
        assertEquals(NotificationAction.None, AlertNotificationPolicy.decide(isPosted = false, AlertStatus.Quiet, confirmed = true, allowed = true))
        assertEquals(NotificationAction.None, AlertNotificationPolicy.decide(isPosted = true, AlertStatus.Quiet, confirmed = false, allowed = true), "a failed refresh never ends it")
        assertEquals(NotificationAction.None, AlertNotificationPolicy.decide(isPosted = true, null, confirmed = true, allowed = true), "unknown never ends it")
        assertEquals(NotificationAction.End, AlertNotificationPolicy.decide(isPosted = true, AlertStatus.Alarm, confirmed = true, allowed = false))
        assertEquals(true, AlertNotificationPolicy.isStale(checkedAt = now - 16.minutes, now = now))
        assertEquals(false, AlertNotificationPolicy.isStale(checkedAt = now - 14.minutes, now = now))
    }

    @Test
    fun REQ_REFRESH_008_a_widget_update_saves_a_success_keeps_the_last_good_and_honours_the_window() = runTest {
        val clock = TestClock(now)
        val store = InMemoryStatusStore()
        val good = snapshot(0, AlertStatus.Alarm)

        assertEquals(BackgroundOutcome.Fetched, BackgroundRefresh(AlertsProvider { good }, store, clock).run(isScheduled = true))
        assertEquals(good, store.loadSnapshot())

        val failing = BackgroundRefresh(AlertsProvider { throw TransportFailure(TransportFailure.Kind.Other) }, store, clock)
        assertEquals(BackgroundOutcome.Failed, failing.run(isScheduled = true))
        assertEquals(good, store.loadSnapshot(), "the last known good snapshot stays")

        val limited = BackgroundRefresh(AlertsProvider { throw ProviderFailure.RateLimited(now + 90.seconds) }, store, clock)
        assertEquals(BackgroundOutcome.Failed, limited.run(isScheduled = false))
        assertEquals(BackgroundOutcome.Held, BackgroundRefresh(AlertsProvider { good }, store, clock).run(isScheduled = true), "inside the stored window")
        assertEquals(BackgroundOutcome.Fetched, BackgroundRefresh(AlertsProvider { good }, store, clock).run(isScheduled = false), "a tap still fetches")

        val slow = BackgroundRefresh(AlertsProvider { awaitCancellation() }, InMemoryStatusStore(), clock)
        assertEquals(BackgroundOutcome.Failed, slow.run(isScheduled = false), "bounded at 8 s")
    }
}
