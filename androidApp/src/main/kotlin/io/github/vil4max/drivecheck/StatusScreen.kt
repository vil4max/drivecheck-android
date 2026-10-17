package io.github.vil4max.drivecheck

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.status.StatusPresentation
import io.github.vil4max.drivecheck.theme.DriveCheckColors
import java.util.Date
import kotlin.time.Instant

@Composable
fun StatusScreen(viewModel: StatusViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    StatusContent(
        presentation = state.presentation,
        regionName = stringResource(state.region.nameRes()),
        isChecking = state.isChecking,
        onRefresh = viewModel::refresh,
        locationDenied = state.isLocationDenied,
        onOpenSettings = onOpenSettings,
    )
}

// The iOS Status tab: title, hero, coloured status word, region with its update time, the nearby
// card. Pull to refresh is the gesture (as on the iPhone); TalkBack gets a Refresh action on the hero.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusContent(
    presentation: StatusPresentation,
    regionName: String,
    isChecking: Boolean,
    onRefresh: () -> Unit,
    locationDenied: Boolean = false,
    onOpenSettings: () -> Unit = {},
    belowNearby: @Composable () -> Unit = {},
) {
    val refreshLabel = stringResource(R.string.action_refresh)
    PullToRefreshBox(isRefreshing = isChecking, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    customActions = listOf(CustomAccessibilityAction(refreshLabel) { onRefresh(); true })
                },
            ) {
                StatusHero(presentation.phase, Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(presentation.phase.titleRes()),
                    color = presentation.phase.color(),
                    fontSize = 40.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LocationArrow(DriveCheckColors.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(regionName, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                }
                presentation.meta(isChecking)?.let { meta ->
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyLarge,
                        color = DriveCheckColors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            nearbyText(presentation.nearbyAlerts)?.let { nearby ->
                Spacer(Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DriveCheckColors.surface, RoundedCornerShape(24.dp))
                        .border(1.dp, DriveCheckColors.surfaceStroke, RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    WarningTriangle(DriveCheckColors.statusCaution)
                    Spacer(Modifier.width(16.dp))
                    Text(nearby, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (locationDenied) {
                Spacer(Modifier.height(20.dp))
                LocationDeniedCard(onOpenSettings)
            }
            belowNearby()
            Spacer(Modifier.height(24.dp))
        }
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
private fun StatusPresentation.meta(isChecking: Boolean): String? {
    val checkedAt = checkedAt
    return when {
        phase == DisplayPhase.Unavailable -> stringResource(R.string.hint_unavailable)
        checkedAt == null -> if (isChecking) stringResource(R.string.status_checking) else null
        isStale -> stringResource(
            R.string.status_meta_last_known,
            stringResource(if (phase == DisplayPhase.Alarm) R.string.status_short_alarm else R.string.status_quiet),
            formatTime(checkedAt),
        )
        else -> stringResource(R.string.status_updated, formatTime(checkedAt))
    }
}

// The device's own time format: 24 h in uk and ru, as the locale or the user setting says.
@Composable
fun formatTime(instant: Instant): String =
    DateFormat.getTimeFormat(LocalContext.current).format(Date(instant.toEpochMilliseconds()))
