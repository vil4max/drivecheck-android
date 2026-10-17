package io.github.vil4max.drivecheck

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.status.StatusPresentation
import io.github.vil4max.drivecheck.theme.DriveCheckColors
import java.util.Date
import kotlin.time.Instant

@Composable
fun StatusScreen(viewModel: StatusViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStarted()
        onStopOrDispose { viewModel.onScreenStopped() }
    }

    StatusContent(
        presentation = state.presentation,
        regionName = stringResource(state.region.nameRes()),
        isChecking = state.isChecking,
        onRefresh = viewModel::refresh,
    )
}

@Composable
fun StatusContent(
    presentation: StatusPresentation,
    regionName: String,
    isChecking: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(regionName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .size(24.dp)
                    .background(presentation.phase.color(), CircleShape),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(presentation.phase.titleRes()),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            presentation.meta()?.let { meta ->
                Spacer(Modifier.height(8.dp))
                Text(meta, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            nearbyText(presentation.nearbyAlerts)?.let { nearby ->
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(DriveCheckColors.statusCaution, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(nearby, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        // REQ-ANDROID-001: while a request runs the button says so and takes no taps.
        Button(
            onClick = onRefresh,
            enabled = !isChecking,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            // Material's disabled default is about 2.5:1 here; the iOS secondary text on the same
            // fill is about 6:1.
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = DriveCheckColors.buttonFill,
                disabledContentColor = DriveCheckColors.textSecondary,
            ),
        ) {
            Text(stringResource(if (isChecking) R.string.status_checking else R.string.action_refresh))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.informational_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun DisplayPhase.titleRes(): Int = when (this) {
    DisplayPhase.Checking -> R.string.status_checking
    DisplayPhase.NoAlert -> R.string.status_quiet
    DisplayPhase.StayAlert -> R.string.status_stay_alert
    DisplayPhase.Alarm -> R.string.status_alarm
    DisplayPhase.Stale -> R.string.status_no_current_data
    DisplayPhase.Unavailable -> R.string.status_unavailable
    DisplayPhase.RegionUnavailable -> R.string.status_region_unavailable
}

// The colour repeats what the title already says; the status never depends on colour alone.
fun DisplayPhase.color(): Color = when (this) {
    DisplayPhase.Alarm -> DriveCheckColors.statusAlert
    DisplayPhase.NoAlert -> DriveCheckColors.statusClear
    DisplayPhase.StayAlert -> DriveCheckColors.statusCaution
    DisplayPhase.Stale -> DriveCheckColors.statusNoData
    else -> DriveCheckColors.statusChecking
}

// "Updated HH:mm" for current data; "Last known: {status} · HH:mm" once it is stale.
@Composable
private fun StatusPresentation.meta(): String? {
    val checkedAt = checkedAt
    return when {
        phase == DisplayPhase.Unavailable -> stringResource(R.string.hint_unavailable)
        checkedAt == null -> null
        isStale -> stringResource(
            R.string.status_meta_last_known,
            stringResource(if (phase == DisplayPhase.Alarm) R.string.status_short_alarm else R.string.status_quiet),
            formatTime(checkedAt),
        )
        else -> stringResource(R.string.status_updated, formatTime(checkedAt))
    }
}

@Composable
fun formatTime(instant: Instant): String =
    DateFormat.getTimeFormat(LocalContext.current).format(Date(instant.toEpochMilliseconds()))
