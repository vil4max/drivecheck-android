package io.github.vil4max.drivecheck

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
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
import io.github.vil4max.drivecheck.status.StatusState
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

    StatusContent(state = state, onRefresh = viewModel::refresh)
}

@Composable
fun StatusContent(state: StatusState, onRefresh: () -> Unit) {
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
            Text(stringResource(R.string.region_kyiv_city), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .size(24.dp)
                    .background(state.indicatorColor(), CircleShape),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(state.titleRes()),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            state.detail()?.let { detail ->
                Spacer(Modifier.height(8.dp))
                Text(detail, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.action_refresh))
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

private fun StatusState.titleRes(): Int = when (this) {
    StatusState.Checking -> R.string.status_checking
    is StatusState.Quiet -> R.string.status_quiet
    is StatusState.Alarm -> R.string.status_alarm
    StatusState.Unavailable -> R.string.status_unavailable
    StatusState.RegionUnavailable -> R.string.status_region_unavailable
}

// The colour repeats what the title already says; the status never depends on colour alone.
private fun StatusState.indicatorColor(): Color = when (this) {
    is StatusState.Alarm -> DriveCheckColors.statusAlert
    is StatusState.Quiet -> DriveCheckColors.statusClear
    else -> DriveCheckColors.statusChecking
}

@Composable
private fun StatusState.detail(): String? = when (this) {
    is StatusState.Quiet -> updatedLine(checkedAt)
    is StatusState.Alarm -> updatedLine(checkedAt)
    StatusState.Unavailable -> stringResource(R.string.hint_unavailable)
    else -> null
}

@Composable
private fun updatedLine(checkedAt: Instant): String {
    val time = DateFormat.getTimeFormat(LocalContext.current).format(Date(checkedAt.toEpochMilliseconds()))
    return stringResource(R.string.status_updated, time)
}
