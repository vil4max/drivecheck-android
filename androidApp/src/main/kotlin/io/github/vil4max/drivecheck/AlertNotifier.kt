package io.github.vil4max.drivecheck

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.compose.ui.graphics.toArgb
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.surface.AlertNotificationPolicy
import io.github.vil4max.drivecheck.surface.NotificationAction
import kotlin.time.Clock
import kotlin.time.Instant

// The Live Activity counterpart: an ongoing notification while the current region is in alarm
// (REQ-SURF-009, REQ-SURF-003).
object AlertNotifier {
    const val CHANNEL_ID = "alert"
    private const val ID = 1

    fun sync(context: Context, mayPost: Boolean, now: Instant = Clock.System.now()) {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java)
        val store = AppGraph.store(app)
        val snapshot = store.loadSnapshot()
        val region = store.loadRegion() ?: AlertRegion.KyivCity
        val isPosted = manager.activeNotifications.any { it.id == ID }
        val isStale = snapshot == null || AlertNotificationPolicy.isStale(snapshot.checkedAt, now)
        val allowed = NotificationSettings(app).isAllowed()
        when (AlertNotificationPolicy.decide(isPosted, snapshot?.status(region), confirmed = !isStale, allowed = allowed)) {
            NotificationAction.End -> manager.cancel(ID)
            NotificationAction.Post -> if (mayPost && snapshot != null) post(app, manager, region, snapshot, isStale)
            NotificationAction.Update -> if (snapshot != null) post(app, manager, region, snapshot, isStale)
            // An unconfirmed answer keeps the alarm up and marks its age.
            NotificationAction.None -> if (isPosted && allowed && snapshot != null) post(app, manager, region, snapshot, isStale)
        }
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun post(context: Context, manager: NotificationManager, region: AlertRegion, snapshot: AlertsSnapshot, isStale: Boolean) {
        ensureChannel(context)
        val line = context.getString(region.nameRes()) + " · " + updatedLine(context, snapshot.checkedAt)
        val text = if (isStale) line + "\n" + context.getString(R.string.notification_may_be_outdated) else line
        val refresh = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, NotificationRefreshReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setColor(DisplayPhase.Alarm.color().toArgb())
            .setContentTitle(context.getString(R.string.status_alarm))
            .setContentText(line)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setShowWhen(false)
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_alert), context.getString(R.string.action_refresh), refresh).build())
            .build()
        manager.notify(ID, notification)
    }
}

// The notification's Refresh: one bounded fetch, applied like any refresh, never a new post.
class NotificationRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Surfaces.refreshInBackground(goAsync(), context, isScheduled = false, mayPostNotification = false)
    }
}
