package io.github.vil4max.drivecheck

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import io.github.vil4max.drivecheck.surface.BackgroundRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock

// Everything outside the app that reads the stored snapshot, refreshed together after any fetch.
object Surfaces {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The notification's own Refresh passes mayPostNotification = false (REQ-SURF-009).
    fun updateAll(context: Context, mayPostNotification: Boolean = true) {
        StatusWidgetProvider.render(context)
        AlertNotifier.sync(context, mayPostNotification)
        TileService.requestListeningState(context, ComponentName(context, StatusTileService::class.java))
    }

    // A broadcast receiver gets a few seconds; the fetch is bounded at 8 s inside BackgroundRefresh.
    fun refreshInBackground(pending: BroadcastReceiver.PendingResult, context: Context, isScheduled: Boolean, mayPostNotification: Boolean) {
        val app = context.applicationContext
        scope.launch {
            try {
                BackgroundRefresh(AppGraph.client, AppGraph.store(app), Clock.System).run(isScheduled)
                updateAll(app, mayPostNotification)
            } finally {
                pending.finish()
            }
        }
    }
}
