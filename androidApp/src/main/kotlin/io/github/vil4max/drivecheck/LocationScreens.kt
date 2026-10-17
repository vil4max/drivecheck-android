package io.github.vil4max.drivecheck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vil4max.drivecheck.theme.DriveCheckColors

// iOS OnboardingView: the neutral mark, the name, three rows and Get Started (REQ-REGION-010:
// location and its prompt start only after the tap).
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        NeutralMark()
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.app_name), fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_body),
            fontSize = 19.sp,
            lineHeight = 28.sp,
            textAlign = TextAlign.Center,
            color = DriveCheckColors.textPrimary.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(32.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(DriveCheckColors.surface, RoundedCornerShape(24.dp))
                .border(1.dp, DriveCheckColors.surfaceStroke, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp),
        ) {
            OnboardingRow(Glyph.Wheel, R.string.onboarding_car_title, R.string.onboarding_car_caption)
            HorizontalDivider(color = DriveCheckColors.surfaceStroke)
            OnboardingRow(Glyph.Arrow, R.string.onboarding_location_title, R.string.onboarding_location_caption)
            HorizontalDivider(color = DriveCheckColors.surfaceStroke)
            OnboardingRow(Glyph.Shield, R.string.onboarding_free_title, R.string.onboarding_free_caption)
        }
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DriveCheckColors.textPrimary,
                contentColor = DriveCheckColors.background,
            ),
        ) {
            Text(stringResource(R.string.get_started), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NeutralMark() {
    Canvas(Modifier.size(170.dp)) {
        drawCircle(DriveCheckColors.surfaceStroke, size.minDimension / 2 - 2.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        drawCircle(DriveCheckColors.textPrimary.copy(alpha = 0.9f), 12.dp.toPx())
    }
}

private enum class Glyph { Wheel, Arrow, Shield }

@Composable
private fun OnboardingRow(glyph: Glyph, title: Int, caption: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(DriveCheckColors.buttonFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (glyph) {
                Glyph.Arrow -> LocationArrow(DriveCheckColors.textPrimary)
                Glyph.Wheel -> Canvas(Modifier.size(20.dp)) {
                    val r = size.minDimension / 2
                    drawCircle(DriveCheckColors.textPrimary, r * 0.9f, style = Stroke(r * 0.22f))
                    drawCircle(DriveCheckColors.textPrimary, r * 0.25f)
                    drawLine(DriveCheckColors.textPrimary, center, center + Offset(0f, r * 0.85f), r * 0.22f)
                    drawLine(DriveCheckColors.textPrimary, center - Offset(r * 0.85f, r * 0.1f), center + Offset(r * 0.85f, -r * 0.1f), r * 0.22f)
                }
                Glyph.Shield -> Canvas(Modifier.size(18.dp)) {
                    val w = size.width
                    val shield = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w / 2, 0f)
                        lineTo(w, w * 0.18f)
                        cubicTo(w, w * 0.65f, w * 0.75f, w * 0.88f, w / 2, w)
                        cubicTo(w * 0.25f, w * 0.88f, 0f, w * 0.65f, 0f, w * 0.18f)
                        close()
                    }
                    drawPath(shield, DriveCheckColors.textPrimary, style = Stroke(w * 0.1f))
                    drawLine(DriveCheckColors.textPrimary, Offset(w * 0.3f, w * 0.52f), Offset(w * 0.46f, w * 0.68f), w * 0.1f, StrokeCap.Round)
                    drawLine(DriveCheckColors.textPrimary, Offset(w * 0.46f, w * 0.68f), Offset(w * 0.72f, w * 0.36f), w * 0.1f, StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(title), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(caption), fontSize = 14.sp, color = DriveCheckColors.textSecondary)
        }
    }
}

// The iOS outside-Ukraine sheet content (REQ-REGION-008).
@Composable
fun OutsideUkraineContent(onGotIt: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(44.dp)) {
            val w = size.width
            drawLine(DriveCheckColors.textPrimary, Offset(w * 0.1f, w * 0.1f), Offset(w * 0.9f, w * 0.9f), w * 0.07f, StrokeCap.Round)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.outside_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.outside_body),
            fontSize = 17.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            color = DriveCheckColors.textSecondary,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGotIt,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DriveCheckColors.textPrimary,
                contentColor = DriveCheckColors.background,
            ),
        ) {
            Text(stringResource(R.string.got_it), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// The non-modal "Region changed" pill with its close button and no Undo (REQ-REGION-007).
@Composable
fun RegionChangedNotice(regionName: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val close = stringResource(R.string.close)
    Row(
        modifier = modifier
            .background(DriveCheckColors.buttonFill, RoundedCornerShape(50))
            .border(1.dp, DriveCheckColors.surfaceStroke, RoundedCornerShape(50))
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.region_changed, regionName), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = close }) {
            Canvas(Modifier.size(12.dp)) {
                drawLine(DriveCheckColors.textSecondary, Offset.Zero, Offset(size.width, size.height), 2.dp.toPx(), StrokeCap.Round)
                drawLine(DriveCheckColors.textSecondary, Offset(size.width, 0f), Offset(0f, size.height), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

// The iOS LocationAccessDeniedCard (REQ-REGION-009).
@Composable
fun LocationDeniedCard(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DriveCheckColors.surface, RoundedCornerShape(24.dp))
            .border(1.dp, DriveCheckColors.surfaceStroke, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.location_denied), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.location_enable_tip), fontSize = 15.sp, color = DriveCheckColors.textSecondary)
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DriveCheckColors.buttonFill, contentColor = Color.White),
        ) {
            Text(stringResource(R.string.location_open_settings))
        }
    }
}
