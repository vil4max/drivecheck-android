package io.github.vil4max.drivecheck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vil4max.drivecheck.status.DisplayPhase
import kotlin.math.cos
import kotlin.math.sin

// The iOS hero (StatusHeroGraphic): a tick ring, the dial with a soft glow in the status colour,
// a radar sweep, and the status symbol in the centre. Decorative; the title carries the status.
@Composable
fun StatusHero(phase: DisplayPhase, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val color = phase.color()
    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension / 2
        // A soft, wide glow, as iOS: the colour fades out well beyond the tick ring.
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = 0.16f),
                0.55f to color.copy(alpha = 0.07f),
                1f to Color.Transparent,
                center = center,
                radius = radius * 1.6f,
            ),
            radius = radius * 1.6f,
        )
        drawTicks(color, radius)
        val dial = radius * 0.70f
        drawCircle(color.copy(alpha = 0.14f), dial)
        drawCircle(color.copy(alpha = 0.40f), dial, style = Stroke(1.5.dp.toPx()))
        drawSweep(color, dial)
        drawCircle(color.copy(alpha = 0.22f), radius * 0.36f)
        drawSymbol(phase, color, radius * 0.36f)
    }
}

private fun DrawScope.drawTicks(color: Color, radius: Float) {
    val count = 60
    repeat(count) { index ->
        val angle = (index * 360f / count) * (Math.PI / 180).toFloat()
        val outer = radius * 0.94f
        val inner = radius * 0.88f
        drawLine(
            color = color.copy(alpha = 0.38f),
            start = center + Offset(cos(angle) * inner, sin(angle) * inner),
            end = center + Offset(cos(angle) * outer, sin(angle) * outer),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

// The radar sweep: a filled wedge whose tail fades out, ending in a bright leading edge at
// about four o'clock, as on iOS.
private fun DrawScope.drawSweep(color: Color, dial: Float) {
    val lead = 20f
    val span = 70f
    rotate(lead - span) {
        drawArc(
            brush = Brush.sweepGradient(
                0f to Color.Transparent,
                (span / 360f) to color.copy(alpha = 0.42f),
                (span / 360f) + 0.0001f to Color.Transparent,
                center = center,
            ),
            startAngle = 0f,
            sweepAngle = span,
            useCenter = true,
            topLeft = center - Offset(dial, dial),
            size = Size(dial * 2, dial * 2),
        )
    }
    val angle = lead * (Math.PI / 180).toFloat()
    val edge = Offset(cos(angle), sin(angle))
    drawLine(color.copy(alpha = 0.95f), center + edge * (dial * 0.8f), center + edge * dial, 2.5.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.drawSymbol(phase: DisplayPhase, color: Color, disc: Float) {
    val stroke = disc * 0.16f
    val s = disc * 0.55f
    when (phase) {
        DisplayPhase.Alarm, DisplayPhase.StayAlert -> {
            if (phase == DisplayPhase.StayAlert) {
                val triangle = Path().apply {
                    moveTo(center.x, center.y - s * 1.05f)
                    lineTo(center.x + s * 1.1f, center.y + s * 0.8f)
                    lineTo(center.x - s * 1.1f, center.y + s * 0.8f)
                    close()
                }
                drawPath(triangle, color.copy(alpha = 0.30f))
            }
            drawLine(color, center + Offset(0f, -s * 0.55f), center + Offset(0f, s * 0.15f), stroke, StrokeCap.Round)
            drawCircle(color, stroke * 0.62f, center + Offset(0f, s * 0.5f))
        }
        DisplayPhase.NoAlert -> {
            val check = Path().apply {
                moveTo(center.x - s * 0.5f, center.y)
                lineTo(center.x - s * 0.1f, center.y + s * 0.4f)
                lineTo(center.x + s * 0.55f, center.y - s * 0.4f)
            }
            drawPath(check, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        DisplayPhase.Stale -> {
            drawCircle(color, s * 0.75f, style = Stroke(stroke * 0.7f))
            drawLine(color, center, center + Offset(0f, -s * 0.45f), stroke * 0.7f, StrokeCap.Round)
            drawLine(color, center, center + Offset(s * 0.35f, 0f), stroke * 0.7f, StrokeCap.Round)
        }
        DisplayPhase.Checking -> drawCircle(color, stroke * 0.7f)
        DisplayPhase.Unavailable, DisplayPhase.RegionUnavailable -> {
            val hook = Path().apply {
                moveTo(center.x - s * 0.35f, center.y - s * 0.3f)
                cubicTo(center.x - s * 0.35f, center.y - s * 0.8f, center.x + s * 0.4f, center.y - s * 0.8f, center.x + s * 0.4f, center.y - s * 0.3f)
                cubicTo(center.x + s * 0.4f, center.y, center.x, center.y, center.x, center.y + s * 0.15f)
            }
            drawPath(hook, color, style = Stroke(stroke * 0.8f, cap = StrokeCap.Round))
            drawCircle(color, stroke * 0.55f, center + Offset(0f, s * 0.55f))
        }
    }
}

// The location arrow before the region name, as on iOS.
@Composable
fun LocationArrow(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val w = size.width
        val arrow = Path().apply {
            moveTo(w * 0.92f, w * 0.08f)
            lineTo(w * 0.08f, w * 0.45f)
            lineTo(w * 0.45f, w * 0.55f)
            lineTo(w * 0.55f, w * 0.92f)
            close()
        }
        drawPath(arrow, color)
    }
}

// The warning triangle of the nearby card.
@Composable
fun WarningTriangle(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val triangle = Path().apply {
            moveTo(w / 2, w * 0.06f)
            lineTo(w * 0.97f, w * 0.9f)
            lineTo(w * 0.03f, w * 0.9f)
            close()
        }
        drawPath(triangle, color, style = Stroke(w * 0.08f, join = StrokeJoin.Round))
        drawPath(triangle, color)
        drawLine(Color(0xFF1A1408), Offset(w / 2, w * 0.38f), Offset(w / 2, w * 0.62f), w * 0.1f, StrokeCap.Round)
        drawCircle(Color(0xFF1A1408), w * 0.055f, Offset(w / 2, w * 0.76f))
    }
}
