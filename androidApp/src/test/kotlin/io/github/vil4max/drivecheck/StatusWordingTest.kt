package io.github.vil4max.drivecheck

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusWordingTest {
    private fun strings(folder: String): Map<String, String> =
        File("src/main/res/$folder").listFiles { file -> file.extension == "xml" }.orEmpty().flatMap { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until nodes.length).map { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
        }.toMap()

    private fun assertWording(expected: Map<String, List<String>>) {
        listOf("values", "values-uk", "values-ru").forEachIndexed { locale, folder ->
            val actual = strings(folder)
            expected.forEach { (key, texts) -> assertEquals(texts[locale], actual[key], "$folder/$key") }
        }
    }

    @Test
    fun REQ_SURF_001_status_wording_matches_ios_in_en_uk_ru() {
        assertWording(
            mapOf(
                "status_alarm" to listOf("Air Raid Alert", "Повітряна тривога", "Воздушная тревога"),
                "status_quiet" to listOf("No Alert", "Тривоги немає", "Тревоги нет"),
                "status_checking" to listOf("Checking…", "Перевіряємо…", "Проверяем…"),
                "status_unavailable" to listOf("Unavailable", "Недоступно", "Недоступно"),
                "status_region_unavailable" to listOf("Region Unavailable", "Регіон недоступний", "Регион недоступен"),
                "status_updated" to listOf("Updated %1\$s", "Оновлено %1\$s", "Обновлено %1\$s"),
                "region_kyiv_city" to listOf("Kyiv", "Київ", "Киев"),
                "action_refresh" to listOf("Refresh", "Оновити", "Обновить"),
                "hint_unavailable" to listOf(
                    "Pull to refresh and try again",
                    "Потягніть униз, щоб оновити й спробувати знову",
                    "Потяните вниз, чтобы обновить и попробовать снова",
                ),
            ),
        )
    }

    @Test
    fun REQ_SURF_010_stay_alert_and_the_nearby_line_match_ios_in_en_uk_ru() {
        assertWording(
            mapOf(
                "status_stay_alert" to listOf("Stay Alert", "Будьте уважні", "Будьте внимательны"),
                "nearby_line" to listOf("Nearby: %1\$s", "Поруч: %1\$s", "Рядом: %1\$s"),
                "region_kyiv_oblast" to listOf("Kyiv Oblast", "Київська область", "Киевская область"),
                "region_zaporizhzhia" to listOf("Zaporizhzhia Oblast", "Запорізька область", "Запорожская область"),
            ),
        )
    }

    @Test
    fun REQ_PROVIDER_003_the_informational_line_matches_ios_in_en_uk_ru() {
        assertWording(
            mapOf(
                "informational_source" to listOf(
                    "Alert data is informational only. Do not rely on it for critical decisions.",
                    "Дані тривог мають інформаційний характер. Не покладайтеся на них при критичних рішеннях.",
                    "Данные тревог носят информационный характер. Не полагайтесь на них при критических решениях.",
                ),
            ),
        )
    }
}
