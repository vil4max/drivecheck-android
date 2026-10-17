package io.github.vil4max.drivecheck.theme

import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals

class DriveCheckColorsTest {
    private fun hex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

    @Test
    fun palette_matches_the_ios_app() {
        val expected = mapOf(
            "background" to "#0C0E11",
            "statusClear" to "#7CC39B",
            "statusAlert" to "#F07C7C",
            "statusChecking" to "#9AA0A8",
            "textPrimary" to "#F2F3F5",
            "textSecondary" to "#A3A7AE",
            "buttonFill" to "#282B31",
        )
        val actual = mapOf(
            "background" to DriveCheckColors.background,
            "statusClear" to DriveCheckColors.statusClear,
            "statusAlert" to DriveCheckColors.statusAlert,
            "statusChecking" to DriveCheckColors.statusChecking,
            "textPrimary" to DriveCheckColors.textPrimary,
            "textSecondary" to DriveCheckColors.textSecondary,
            "buttonFill" to DriveCheckColors.buttonFill,
        ).mapValues { hex(it.value.toArgb()) }

        assertEquals(expected, actual)
    }
}
