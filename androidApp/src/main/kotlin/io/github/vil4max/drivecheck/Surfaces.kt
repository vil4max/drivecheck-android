package io.github.vil4max.drivecheck

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.util.Log
import io.github.vil4max.drivecheck.surface.BackgroundRefresh
import io.github.vil4max.drivecheck.surface.SurfaceSchedule
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock

// Everything outside the app that reads the stored snapshot, refreshed together after any fetch.
object Surfaces {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error -> Log.w("Surfaces", "background update failed", error) },
    )

    // Replaced in tests to observe which entry point asks for what.
    internal var refresher: (BroadcastReceiver.PendingResult?, Context, Boolean, Boolean) -> Unit = ::launchRefresh
    internal var updater: (Context, Boolean) -> Unit = ::updateEverything

    internal fun resetForTests() {
        refresher = ::launchRefresh
        updater = ::updateEverything
    }

    // The notification's own Refresh passes mayPostNotification = false (REQ-SURF-009).
    fun updateAll(context: Context, mayPostNotification: Boolean = true) = updater(context, mayPostNotification)

    // A broadcast receiver gets a few seconds; the fetch is bounded at 8 s inside BackgroundRefresh.
    fun refreshInBackground(pending: BroadcastReceiver.PendingResult?, context: Context, isScheduled: Boolean, mayPostNotification: Boolean) =
        refresher(pending, context, isScheduled, mayPostNotification)

    private fun updateEverything(context: Context, mayPostNotification: Boolean) {
        val app = context.applicationContext
        StatusWidgetProvider.render(app)
        AlertNotifier.sync(app, mayPostNotification)
        TileService.requestListeningState(app, ComponentName(app, StatusTileService::class.java))
        scheduleRedraw(app)
    }

    // With the app closed nothing else redraws: wake at the data's next age boundary, inexact and
    // non-waking, and redraw from the store without a fetch (REQ-REFRESH-009, REQ-SURF-003).
    private fun scheduleRedraw(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val redraw = PendingIntent.getBroadcast(
            context,
            3,
            Intent(context, StatusWidgetProvider::class.java).setAction(StatusWidgetProvider.ACTION_REDRAW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val checkedAt = AppGraph.store(context).loadSnapshot()?.checkedAt
        val next = checkedAt?.let { SurfaceSchedule.nextRedraw(it, Clock.System.now()) }
        if (next == null) alarms.cancel(redraw) else alarms.set(AlarmManager.RTC, next.toEpochMilliseconds(), redraw)
    }

    private fun launchRefresh(pending: BroadcastReceiver.PendingResult?, context: Context, isScheduled: Boolean, mayPostNotification: Boolean) {
        val app = context.applicationContext
        scope.launch {
            try {
                BackgroundRefresh(AppGraph.client, AppGraph.store(app), Clock.System).run(isScheduled)
                updateAll(app, mayPostNotification)
            } finally {
                pending?.finish()
            }
        }
    }
}
