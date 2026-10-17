package io.github.vil4max.drivecheck

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Spec 002 T018 and spec 004: the copy promises a car screen only while the manifest declares the
// Android Auto service; CarPlay and Android Automotive OS are never shipped. Comments are not copy.
class ReleaseCopyGuardTest {
    @Test
    fun release_guard_the_copy_promises_only_the_car_screen_that_ships() {
        val strings = File("src/main/res").walkTopDown().filter { it.isFile && it.parent.contains("values") && it.extension == "xml" }.toList()
        assertTrue(strings.size >= 3, "string files found: ${strings.size}")
        val carShips = File("src/main/AndroidManifest.xml").readText().contains("androidx.car.app.CarAppService")
        assertEquals(emptyList(), strings.flatMap { file -> promises(file.readText(), carShips).map { "${file.parentFile.name}/${file.name}: $it" } })
    }

    @Test
    fun release_guard_knows_the_uk_and_ru_words_and_skips_comments() {
        assertEquals(listOf("Статус на екрані авто"), promises("<string name=\"a\">Статус на екрані авто</string>", carShips = false))
        assertEquals(listOf("Статус на экране авто"), promises("<string name=\"a\">Статус на экране авто</string>", carShips = false))
        assertEquals(emptyList(), promises("<string name=\"a\">Зроблено для Android Auto</string>", carShips = true))
        assertEquals(listOf("Works in CarPlay"), promises("<string name=\"a\">Works in CarPlay</string>", carShips = true))
        assertEquals(emptyList(), promises("<!-- CarPlay reads Android Auto -->\n<string name=\"a\">Автоматично</string>", carShips = false))
    }

    private fun promises(xml: String, carShips: Boolean): List<String> {
        val words = NEVER_SHIPPED + if (carShips) emptyList() else ANDROID_AUTO
        return Regex(">([^<]*)</string>").findAll(xml.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), ""))
            .map { it.groupValues[1] }
            .filter { text -> words.any { text.contains(it, ignoreCase = true) } }
            .toList()
    }

    private companion object {
        val NEVER_SHIPPED = listOf("CarPlay", "Automotive")
        val ANDROID_AUTO = listOf("Android Auto", "екрані авто", "в авто", "автомобіл", "машин", "экране авто", "автомобил")
    }
}
