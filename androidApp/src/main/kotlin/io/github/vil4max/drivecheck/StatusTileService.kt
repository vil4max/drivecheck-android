package io.github.vil4max.drivecheck

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.surface.TilePresenter
import io.github.vil4max.drivecheck.surface.TileState
import io.github.vil4max.drivecheck.surface.WidgetPresenter
import kotlin.time.Clock
import kotlin.time.Instant

data class TileContent(val label: String, val subtitle: String, val state: Int) {
    companion object {
        // Android draws the tile's colours, so the traffic light is the state and the label (REQ-ANDROID-007).
        fun read(context: Context, now: Instant = Clock.System.now()): TileContent {
            val store = AppGraph.store(context)
            val region = store.loadRegion() ?: AlertRegion.KyivCity
            val label = TilePresenter.present(WidgetPresenter.present(store.loadSnapshot(), region, now))
            val state = when (label.state) {
                TileState.Active -> Tile.STATE_ACTIVE
                TileState.Inactive -> Tile.STATE_INACTIVE
                TileState.Unavailable -> Tile.STATE_UNAVAILABLE
            }
            return TileContent(context.getString(label.phase.shortTitle()), context.getString(region.nameRes()), state)
        }
    }
}

class StatusTileService : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        val content = TileContent.read(this)
        tile.label = content.label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = content.subtitle
        tile.state = content.state
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
