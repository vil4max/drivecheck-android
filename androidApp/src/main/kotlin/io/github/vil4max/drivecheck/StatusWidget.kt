package io.github.vil4max.drivecheck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.surface.FreshnessTier
import io.github.vil4max.drivecheck.surface.WidgetPresentation
import io.github.vil4max.drivecheck.surface.WidgetPresenter
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Instant

// The iOS Status widget as RemoteViews (REQ-REFRESH-009, REQ-SURF-003, REQ-SURF-010).
object WidgetRenderer {
    fun render(context: Context, presentation: WidgetPresentation): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_status)
        val phase = presentation.let { if (it is WidgetPresentation.Shown) it.phase else DisplayPhase.Stale }
        val color = phase.color().toArgb()
        views.setTextColor(R.id.widget_dot, color)
        views.setTextColor(R.id.widget_status, color)
        views.setTextViewText(R.id.widget_status, context.getString(phase.shortTitle()))
        when (presentation) {
            WidgetPresentation.Missing -> {
                views.setTextViewText(R.id.widget_region, "")
                views.setTextViewText(R.id.widget_time, context.getString(R.string.widget_open_app))
            }
            is WidgetPresentation.Shown -> {
                views.setTextViewText(R.id.widget_region, context.getString(presentation.region.nameRes()))
                val updated = updatedLine(context, presentation.checkedAt)
                val line = when (presentation.tier) {
                    FreshnessTier.Fresh -> updated
                    FreshnessTier.Aging -> context.getString(R.string.widget_warning_time, updated)
                    FreshnessTier.Expired -> context.getString(R.string.widget_warning_time, updated) + "\n" + context.getString(R.string.widget_open_app)
                }
                views.setTextViewText(R.id.widget_time, line)
            }
        }
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, StatusWidgetProvider::class.java).setAction(StatusWidgetProvider.ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return views
    }
}

// Widgets and the tile use the short form (REQ-SURF-001); old quiet data reads "No Current Data".
internal fun DisplayPhase.shortTitle(): Int = when (this) {
    DisplayPhase.Alarm -> R.string.status_short_alarm
    DisplayPhase.NoAlert -> R.string.status_quiet
    DisplayPhase.StayAlert -> R.string.status_stay_alert
    DisplayPhase.RegionUnavailable -> R.string.status_region_unavailable
    else -> R.string.status_no_current_data
}

internal fun updatedLine(context: Context, checkedAt: Instant): String =
    context.getString(R.string.status_updated, DateFormat.getTimeFormat(context).format(Date(checkedAt.toEpochMilliseconds())))

class StatusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refreshAndRender(context, isScheduled = true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> refreshAndRender(context, isScheduled = false)
            ACTION_REDRAW -> Surfaces.updateAll(context)
            else -> super.onReceive(context, intent)
        }
    }

    private fun refreshAndRender(context: Context, isScheduled: Boolean) =
        Surfaces.refreshInBackground(goAsync(), context, isScheduled, mayPostNotification = true)

    companion object {
        const val ACTION_REFRESH = "io.github.vil4max.drivecheck.action.WIDGET_REFRESH"
        const val ACTION_REDRAW = "io.github.vil4max.drivecheck.action.SURFACES_REDRAW"

        fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StatusWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val store = AppGraph.store(context)
            val region = store.loadRegion() ?: io.github.vil4max.drivecheck.domain.AlertRegion.KyivCity
            val presentation = WidgetPresenter.present(store.loadSnapshot(), region, Clock.System.now())
            manager.updateAppWidget(ids, WidgetRenderer.render(context, presentation))
        }
    }
}
