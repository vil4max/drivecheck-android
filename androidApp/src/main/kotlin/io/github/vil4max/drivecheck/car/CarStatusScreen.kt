package io.github.vil4max.drivecheck.car

import android.text.format.DateFormat
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import io.github.vil4max.drivecheck.R
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.nameRes
import io.github.vil4max.drivecheck.nearbyText
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.status.StatusPresentation
import io.github.vil4max.drivecheck.titleRes
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.time.Instant

data class CarStatus(
    val presentation: StatusPresentation,
    val region: AlertRegion,
    val isChecking: Boolean,
    val isLocationDenied: Boolean,
)

// The iOS CarPlay screen (REQ-SURF-006): the status in the title, the region with its update time,
// the nearby row only when a neighbouring region is under alert (REQ-SURF-005), the location row
// only when access is denied, and Refresh as the only action. No map, no tabs.
class CarStatusScreen(
    carContext: CarContext,
    private val status: StateFlow<CarStatus>,
    private val onRefresh: () -> Unit,
) : Screen(carContext) {
    init {
        lifecycleScope.launch { status.drop(1).collect { invalidate() } }
    }

    override fun onGetTemplate(): Template {
        val current = status.value
        val presentation = current.presentation
        val pane = Pane.Builder()
        val regionRow = Row.Builder().setTitle(carContext.getString(current.region.nameRes()))
        meta(presentation, current.isChecking)?.let(regionRow::addText)
        pane.addRow(regionRow.build())
        nearbyText(carContext.resources, presentation.nearbyAlerts)?.let { pane.addRow(Row.Builder().setTitle(it).build()) }
        if (current.isLocationDenied) pane.addRow(Row.Builder().setTitle(carContext.getString(R.string.location_denied)).build())
        icon(presentation.phase)?.let(pane::setImage)
        pane.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.action_refresh))
                .setOnClickListener(onRefresh)
                .build(),
        )
        return PaneTemplate.Builder(pane.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(presentation.phase.titleRes())).build())
            .build()
    }

    // As on the phone: "Updated HH:mm" for current data, "Last known: {status} · HH:mm" once stale.
    private fun meta(presentation: StatusPresentation, isChecking: Boolean): String? {
        val checkedAt = presentation.checkedAt
        return when {
            presentation.phase == DisplayPhase.Unavailable -> carContext.getString(R.string.hint_unavailable)
            checkedAt == null -> if (isChecking) carContext.getString(R.string.status_checking) else null
            presentation.isStale -> carContext.getString(
                R.string.status_meta_last_known,
                carContext.getString(if (presentation.phase == DisplayPhase.Alarm) R.string.status_short_alarm else R.string.status_quiet),
                time(checkedAt),
            )
            else -> carContext.getString(R.string.status_updated, time(checkedAt))
        }
    }

    private fun time(instant: Instant): String = DateFormat.getTimeFormat(carContext).format(Date(instant.toEpochMilliseconds()))

    // The glyph repeats the title in the host's own colours; the status never depends on colour alone.
    private fun icon(phase: DisplayPhase): CarIcon? = when (phase) {
        DisplayPhase.Alarm -> CarIcon.Builder(CarIcon.ALERT).setTint(CarColor.RED).build()
        DisplayPhase.StayAlert -> CarIcon.Builder(CarIcon.ALERT).setTint(CarColor.YELLOW).build()
        else -> null
    }
}
