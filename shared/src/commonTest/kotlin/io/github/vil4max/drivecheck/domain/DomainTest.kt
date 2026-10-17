package io.github.vil4max.drivecheck.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class DomainTest {
    @Test
    fun REQ_REGION_001_catalog_has_25_regions_with_their_ubilling_keys() {
        val expected = listOf(
            "м. Київ", "Вінницька область", "Волинська область", "Дніпропетровська область",
            "Донецька область", "Житомирська область", "Закарпатська область", "Запорізька область",
            "Івано-Франківська область", "Київська область", "Кіровоградська область",
            "Луганська область", "Львівська область", "Миколаївська область", "Одеська область",
            "Полтавська область", "Рівненська область", "Сумська область", "Тернопільська область",
            "Харківська область", "Херсонська область", "Хмельницька область", "Черкаська область",
            "Чернівецька область", "Чернігівська область",
        )
        assertEquals(expected, AlertRegion.entries.map { it.apiKey })
        expected.forEach { key -> assertEquals(key, AlertRegion.fromApiKey(key)?.apiKey) }
    }

    @Test
    fun REQ_REGION_001_an_unknown_key_maps_to_no_region() {
        assertNull(AlertRegion.fromApiKey("Севастополь"))
        assertNull(AlertRegion.fromApiKey("АР Крим"))
        assertNull(AlertRegion.fromApiKey(""))
    }

    @Test
    fun REQ_PROVIDER_001_checked_at_is_cachedat_else_the_fetch_time() {
        val cachedAt = Instant.parse("2026-10-17T09:00:00Z")
        val fetchedAt = Instant.parse("2026-10-17T09:00:05Z")
        val statuses = mapOf(AlertRegion.KyivCity to AlertStatus.Quiet)

        val withCache = AlertsSnapshot("test", serverCachedAt = cachedAt, fetchedAt = fetchedAt, statuses = statuses)
        val withoutCache = AlertsSnapshot("test", serverCachedAt = null, fetchedAt = fetchedAt, statuses = statuses)

        assertEquals(cachedAt, withCache.checkedAt)
        assertEquals(fetchedAt, withoutCache.checkedAt)
        assertEquals(AlertStatus.Quiet, withCache.status(AlertRegion.KyivCity))
        assertNull(withCache.status(AlertRegion.Lviv))
    }
}
