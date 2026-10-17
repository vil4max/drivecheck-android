package io.github.vil4max.drivecheck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.status.RegionList
import io.github.vil4max.drivecheck.status.RegionRow
import io.github.vil4max.drivecheck.status.StatusState
import io.github.vil4max.drivecheck.status.SummaryLine
import io.github.vil4max.drivecheck.theme.DriveCheckColors

sealed interface MapCardState {
    data object Loading : MapCardState
    data object Failed : MapCardState
    data class Loaded(val image: ImageBitmap, val alerts: List<AlertRegion>, val minutes: Long) : MapCardState
}

// The iOS inline map card (REQ-ANDROID-002): the ratio is reserved before the image decodes, the
// caption counts the regions under alert and ages the image, and a tap opens the region list.
@Composable
fun MapCard(state: MapCardState, onOpen: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val alertNames = (state as? MapCardState.Loaded)?.alerts?.map { stringResource(it.nameRes()) }.orEmpty()
    val description = when {
        state !is MapCardState.Loaded -> null
        alertNames.isEmpty() -> stringResource(R.string.map_a11y_clear)
        else -> stringResource(R.string.map_a11y_alarm, alertNames.size, alertNames.joinToString(", "))
    }
    val hint = stringResource(R.string.map_open_regions_hint)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black)
            .border(1.dp, DriveCheckColors.surfaceStroke, RoundedCornerShape(28.dp))
            .aspectRatio(1000f / 670f)
            .clickable(onClick = onOpen)
            .semantics {
                role = Role.Button
                description?.let { contentDescription = it }
                onClick(label = hint) { onOpen(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            MapCardState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = DriveCheckColors.textSecondary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.map_loading), color = DriveCheckColors.textSecondary)
            }
            MapCardState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.map_error), color = DriveCheckColors.textSecondary)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.map_refresh), color = DriveCheckColors.textPrimary) }
            }
            is MapCardState.Loaded -> {
                Image(state.image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Text(
                    stringResource(
                        R.string.map_caption,
                        state.alerts.size,
                        AlertRegion.entries.size,
                        stringResource(R.string.age_minutes, state.minutes.toInt()),
                    ),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                        .padding(top = 18.dp, bottom = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

// The iOS region list (REQ-ANDROID-003), read-only, with short-form pills (REQ-SURF-001).
@Composable
fun RegionListScreen(list: RegionList, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        val back = stringResource(R.string.back)
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(52.dp).background(DriveCheckColors.buttonFill, CircleShape).semantics { contentDescription = back },
        ) {
            Canvas(Modifier.size(18.dp)) {
                val w = size.width
                drawLine(DriveCheckColors.textPrimary, Offset(w * 0.7f, 0f), Offset(w * 0.2f, w / 2), w * 0.14f, StrokeCap.Round)
                drawLine(DriveCheckColors.textPrimary, Offset(w * 0.2f, w / 2), Offset(w * 0.7f, w), w * 0.14f, StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.regions_title), fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(DriveCheckColors.surface, RoundedCornerShape(24.dp))
                .border(1.dp, DriveCheckColors.surfaceStroke, RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = list.current.status.tint()
            Box(Modifier.size(48.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                LocationArrow(tint)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.regions_current), fontSize = 14.sp, color = DriveCheckColors.textSecondary)
                Text(stringResource(list.current.region.nameRes()), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            StatusPill(list.current.status)
        }
        if (list.alarms.isNotEmpty()) {
            SectionHeader(stringResource(R.string.regions_section_alarm, list.alarms.size).uppercase())
            RegionSection(list.alarms, list.current.region, DriveCheckColors.statusAlert)
        }
        SectionHeader(stringResource(R.string.regions_section_other).uppercase())
        RegionSection(list.others, list.current.region, null)
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = DriveCheckColors.textSecondary,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 10.dp).semantics { heading() },
    )
}

@Composable
private fun RegionSection(rows: List<RegionRow>, current: AlertRegion, accent: Color?) {
    val fill = accent?.copy(alpha = 0.08f) ?: DriveCheckColors.surface
    val stroke = accent?.copy(alpha = 0.22f) ?: DriveCheckColors.surfaceStroke
    Column(
        Modifier
            .fillMaxWidth()
            .background(fill, RoundedCornerShape(24.dp))
            .border(1.dp, stroke, RoundedCornerShape(24.dp)),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = DriveCheckColors.surfaceStroke)
            val name = stringResource(row.region.nameRes())
            val status = stringResource(row.status.shortRes())
            val currentLabel = stringResource(R.string.regions_row_current)
            val label = if (row.region == current) "$name, $status, $currentLabel" else "$name, $status"
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp).semantics(mergeDescendants = true) { contentDescription = label },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(row.status.tint(), CircleShape))
                Spacer(Modifier.width(16.dp))
                Text(name, fontSize = 19.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, fontSize = 15.sp, color = row.status.tint())
            }
        }
    }
}

@Composable
private fun StatusPill(status: AlertStatus?) {
    val tint = status.tint()
    Text(
        stringResource(status.shortRes()),
        color = tint,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(tint.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

private fun AlertStatus?.tint(): Color = when (this) {
    AlertStatus.Alarm -> DriveCheckColors.statusAlert
    AlertStatus.Quiet -> DriveCheckColors.statusClear
    null -> DriveCheckColors.statusChecking
}

private fun AlertStatus?.shortRes(): Int = when (this) {
    AlertStatus.Alarm -> R.string.status_short_alarm
    AlertStatus.Quiet -> R.string.status_quiet
    null -> R.string.status_unavailable
}

// The iOS Details tab without Live Activity and Purchases (REQ-ANDROID-004).
@Composable
fun DetailsScreen(
    lines: List<SummaryLine>,
    countryStatuses: List<AlertStatus?>,
    alertRegions: List<AlertRegion>,
    locationDenied: Boolean,
    onOpenSettings: () -> Unit,
    onOpenSource: () -> Unit,
    versionName: String,
    versionCode: Int,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.details_title), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(DriveCheckColors.surface, RoundedCornerShape(28.dp))
                .border(1.dp, DriveCheckColors.surfaceStroke, RoundedCornerShape(28.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.summary_title).uppercase(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DriveCheckColors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.source_label) + " " + stringResource(R.string.source_external),
                    fontSize = 14.sp,
                    color = DriveCheckColors.textSecondary,
                )
            }
            lines.forEach { line -> Text(summaryText(line), fontSize = 19.sp, lineHeight = 27.sp) }
            if (countryStatuses.isNotEmpty()) {
                HorizontalDivider(color = DriveCheckColors.surfaceStroke)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    countryStatuses.forEach { status ->
                        Box(Modifier.weight(1f).height(8.dp).background(status.tint(), RoundedCornerShape(50)))
                    }
                }
                if (alertRegions.isNotEmpty()) {
                    Text(
                        alertRegions.map { stringResource(it.nameRes()) }.joinToString(", "),
                        fontSize = 15.sp,
                        color = DriveCheckColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (locationDenied) {
            DetailsHeader(stringResource(R.string.details_section_location))
            LocationDeniedCard(onOpenSettings)
        }
        DetailsHeader(stringResource(R.string.details_section_data))
        Column(
            Modifier
                .fillMaxWidth()
                .background(DriveCheckColors.surface, RoundedCornerShape(24.dp))
                .border(1.dp, DriveCheckColors.surfaceStroke, RoundedCornerShape(24.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onOpenSource).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val w = size.width
                    drawLine(DriveCheckColors.textSecondary, Offset(0f, w), Offset(w, 0f), w * 0.12f, StrokeCap.Round)
                    drawLine(DriveCheckColors.textSecondary, Offset(w * 0.35f, 0f), Offset(w, 0f), w * 0.12f, StrokeCap.Round)
                    drawLine(DriveCheckColors.textSecondary, Offset(w, 0f), Offset(w, w * 0.65f), w * 0.12f, StrokeCap.Round)
                }
                Spacer(Modifier.width(22.dp))
                Text(stringResource(R.string.details_source_link), fontSize = 19.sp)
            }
            HorizontalDivider(color = DriveCheckColors.surfaceStroke, modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.padding(20.dp)) {
                Canvas(Modifier.size(18.dp).padding(top = 2.dp)) {
                    drawCircle(DriveCheckColors.textSecondary, size.minDimension / 2, style = Stroke(1.5.dp.toPx()))
                    drawLine(DriveCheckColors.textSecondary, center, center + Offset(0f, size.height * 0.25f), 1.5.dp.toPx(), StrokeCap.Round)
                    drawCircle(DriveCheckColors.textSecondary, 1.2.dp.toPx(), center - Offset(0f, size.height * 0.22f))
                }
                Spacer(Modifier.width(20.dp))
                Text(stringResource(R.string.informational_source), fontSize = 16.sp, lineHeight = 23.sp, color = DriveCheckColors.textSecondary)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.details_version, versionName, versionCode), fontSize = 14.sp, color = DriveCheckColors.textSecondary)
        Spacer(Modifier.height(112.dp))
    }
}

@Composable
private fun DetailsHeader(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = DriveCheckColors.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 28.dp, bottom = 10.dp).semantics { heading() },
    )
}

@Composable
private fun summaryText(line: SummaryLine): String = when (line) {
    SummaryLine.RegionQuiet -> stringResource(R.string.summary_region_quiet)
    SummaryLine.RegionAlarm -> stringResource(R.string.summary_region_alarm)
    is SummaryLine.RegionOther -> stringResource(
        R.string.summary_region_format,
        stringResource(line.region.nameRes()),
        stringResource(
            when (line.state) {
                StatusState.RegionUnavailable -> R.string.summary_explanation_region_unavailable
                StatusState.Checking -> R.string.summary_explanation_updating
                else -> R.string.summary_explanation_unknown
            },
        ),
    )
    is SummaryLine.Nearby -> {
        val names = line.names.map { stringResource(it.nameRes()) }.joinToString(", ")
        if (line.more > 0) stringResource(R.string.summary_nearby_more, names, line.more) else stringResource(R.string.summary_nearby, names)
    }
    SummaryLine.CountryNoData -> stringResource(R.string.summary_no_data)
    is SummaryLine.CountryAllClear -> stringResource(R.string.summary_all_clear, line.total)
    is SummaryLine.CountryPartialClear -> pluralStringResource(R.plurals.summary_partial_clear, line.clear, line.clear)
    is SummaryLine.CountryAlerts -> stringResource(R.string.summary_alerts_active, line.alerts, line.total)
    SummaryLine.Stale -> stringResource(R.string.summary_stale)
}

enum class Tab { Status, Details }

// The iOS floating tab bar with Status (steering wheel) and Details (list) (REQ-ANDROID-005).
@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .widthIn(max = 320.dp)
            .background(DriveCheckColors.buttonFill.copy(alpha = 0.92f), RoundedCornerShape(50))
            .border(1.dp, DriveCheckColors.surfaceStroke, RoundedCornerShape(50))
            .padding(6.dp),
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) DriveCheckColors.background.copy(alpha = 0.7f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .semantics { role = Role.Tab }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(28.dp)) {
                    val r = size.minDimension / 2
                    val c = DriveCheckColors.textPrimary
                    if (tab == Tab.Status) {
                        drawCircle(c, r * 0.85f, style = Stroke(r * 0.2f))
                        drawCircle(c, r * 0.22f)
                        drawLine(c, center, center + Offset(0f, r * 0.8f), r * 0.2f)
                        drawLine(c, center - Offset(r * 0.8f, r * 0.12f), center + Offset(r * 0.8f, -r * 0.12f), r * 0.2f)
                    } else {
                        drawRoundRect(c, topLeft = Offset(r * 0.1f, r * 0.3f), size = androidx.compose.ui.geometry.Size(r * 1.8f, r * 1.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.2f))
                        repeat(3) { i ->
                            val y = r * (0.62f + i * 0.38f)
                            drawCircle(DriveCheckColors.buttonFill, r * 0.08f, Offset(r * 0.45f, y))
                            drawLine(DriveCheckColors.buttonFill, Offset(r * 0.7f, y), Offset(r * 1.6f, y), r * 0.13f, StrokeCap.Round)
                        }
                    }
                }
                Text(
                    stringResource(if (tab == Tab.Status) R.string.tab_status else R.string.tab_details),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
