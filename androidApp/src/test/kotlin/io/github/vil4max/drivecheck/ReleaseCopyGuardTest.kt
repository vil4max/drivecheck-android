package io.github.vil4max.drivecheck

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Spec 002 T018 and the owner's decision of 2026-10-17 (spec 004): 1.0 ships no car screen, so no
// string in any locale may promise one. Comments are not copy and are skipped.
class ReleaseCopyGuardTest {
    @Test
    fun release_guard_no_string_promises_a_car_screen() {
        val strings = File("src/main/res").walkTopDown().filter { it.isFile && it.parent.contains("values") && it.extension == "xml" }.toList()
        assertTrue(strings.size >= 3, "string files found: ${strings.size}")
        assertEquals(emptyList(), strings.flatMap { file -> promises(file.readText()).map { "${file.parentFile.name}/${file.name}: $it" } })
    }

    @Test
    fun release_guard_knows_the_uk_and_ru_words_and_skips_comments() {
        assertEquals(listOf("Статус на екрані авто"), promises("<string name=\"a\">Статус на екрані авто</string>"))
        assertEquals(listOf("Статус на экране авто"), promises("<string name=\"a\">Статус на экране авто</string>"))
        assertEquals(listOf("Works in Android Auto"), promises("<string name=\"a\">Works in Android Auto</string>"))
        assertEquals(emptyList(), promises("<!-- CarPlay reads Android Auto -->\n<string name=\"a\">Автоматично</string>"))
    }

    private fun promises(xml: String): List<String> =
        Regex(">([^<]*)</string>").findAll(xml.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), ""))
            .map { it.groupValues[1] }
            .filter { text -> CAR_SURFACES.any { text.contains(it, ignoreCase = true) } }
            .toList()

    private companion object {
        val CAR_SURFACES = listOf(
            "Android Auto", "CarPlay", "Automotive",
            "екрані авто", "в авто", "автомобіл", "машин",
            "экране авто", "автомобил",
        )
    }
}
