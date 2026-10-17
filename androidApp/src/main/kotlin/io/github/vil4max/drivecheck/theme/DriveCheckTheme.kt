package io.github.vil4max.drivecheck.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The iOS app's palette (regional-check DriveCheckKit DriveCheckColors and Theme+Redesign).
// The iOS app is dark-only and never themes a status colour, so neither does this one:
// no light scheme and no wallpaper-based dynamic colour.
object DriveCheckColors {
    val background = Color(0xFF0C0E11)
    val statusClear = Color(0xFF7CC39B)
    val statusAlert = Color(0xFFF07C7C)
    // iOS names it statusStale; it means "be careful" (Stay Alert, REQ-SURF-010), never old data.
    val statusCaution = Color(0xFFE8BA62)
    val statusChecking = Color(0xFF9AA0A8)
    // Old or missing data: a light neutral grey, so yellow only ever means caution.
    val statusNoData = Color(0xFFC6CAD0)
    val textPrimary = Color(0xFFF2F3F5)
    val textSecondary = Color(0xFFA3A7AE)
    val buttonFill = Color(0xFF282B31)
}

private val scheme = darkColorScheme(
    primary = DriveCheckColors.buttonFill,
    onPrimary = DriveCheckColors.textPrimary,
    background = DriveCheckColors.background,
    onBackground = DriveCheckColors.textPrimary,
    surface = DriveCheckColors.background,
    onSurface = DriveCheckColors.textPrimary,
    onSurfaceVariant = DriveCheckColors.textSecondary,
)

@Composable
fun DriveCheckTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
