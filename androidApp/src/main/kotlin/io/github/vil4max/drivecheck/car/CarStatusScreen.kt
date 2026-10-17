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
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import io.github.vil4max.drivecheck.R
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.nameRes
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.status.NearbyLine
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

// The iOS CarPlay screen (REQ-SURF-006) within the PaneTemplate quota: the host counts a change of
// the title, the row count or a row title as a new template and closes the app after a few, so
// those stay fixed. The status, its time and the nearby names are row texts, which a refresh may
// change; the colour is the status row's icon.
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
        val statusRow = Row.Builder()
            .setTitle(carContext.getString(R.string.tab_status))
            .addText(carContext.getString(statusWord(presentation)))
            .addText(listOfNotNull(carContext.getString(current.region.nameRes()), meta(presentation, current.isChecking)).joinToString(" · "))
        icon(presentation)?.let(statusRow::setImage)
        val nearbyRow = Row.Builder()
            .setTitle(carContext.getString(R.string.car_row_nearby))
            .addText(nearbyNames(presentation.nearbyAlerts) ?: carContext.getString(R.string.car_nothing_nearby))
        if (current.isLocationDenied) nearbyRow.addText(carContext.getString(R.string.location_denied))
        val pane = Pane.Builder()
            .addRow(statusRow.build())
            .addRow(nearbyRow.build())
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_refresh))
                    .setOnClickListener(onRefresh)
                    .build(),
            )
        return PaneTemplate.Builder(pane.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.app_name)).build())
            .build()
    }

    // REQ-REFRESH-007: any stale snapshot reads "No Current Data" with its age, never a status.
    private fun statusWord(presentation: StatusPresentation): Int =
        if (presentation.isStale) R.string.status_no_current_data else presentation.phase.titleRes()

    // The time line; with no data yet the status word already says "Checking…", so it adds nothing.
    private fun meta(presentation: StatusPresentation, isChecking: Boolean): String? {
        val checkedAt = presentation.checkedAt
        return when {
            presentation.phase == DisplayPhase.Unavailable -> carContext.getString(R.string.hint_unavailable)
            checkedAt == null -> null
            isChecking -> carContext.getString(R.string.status_updated, time(checkedAt)) + " · " + carContext.getString(R.string.status_checking)
            else -> carContext.getString(R.string.status_updated, time(checkedAt))
        }
    }

    private fun nearbyNames(regions: List<AlertRegion>): String? {
        val line = NearbyLine.of(regions) ?: return null
        val names = line.names.joinToString(", ") { carContext.getString(it.nameRes()) }
        return if (line.more > 0) carContext.getString(R.string.nearby_names_more, names, line.more) else names
    }

    private fun time(instant: Instant): String = DateFormat.getTimeFormat(carContext).format(Date(instant.toEpochMilliseconds()))

    // The icon repeats the status word in the host's colours; stale data gets none (REQ-REFRESH-007).
    private fun icon(presentation: StatusPresentation): CarIcon? {
        if (presentation.isStale) return null
        return when (presentation.phase) {
            DisplayPhase.Alarm -> CarIcon.Builder(CarIcon.ALERT).setTint(CarColor.RED).build()
            DisplayPhase.StayAlert -> CarIcon.Builder(CarIcon.ALERT).setTint(CarColor.YELLOW).build()
            DisplayPhase.NoAlert -> CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_clear)).setTint(CarColor.GREEN).build()
            else -> null
        }
    }
}
