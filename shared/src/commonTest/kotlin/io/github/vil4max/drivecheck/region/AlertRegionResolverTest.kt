package io.github.vil4max.drivecheck.region

import io.github.vil4max.drivecheck.domain.AlertRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlertRegionResolverTest {
    @Test
    fun REQ_REGION_004_kyiv_city_wins_over_the_oblast() {
        assertEquals(AlertRegion.KyivCity, AlertRegionResolver.resolve("Київ", "Київська область"))
        assertEquals(AlertRegion.KyivCity, AlertRegionResolver.resolve("Kyiv", null))
        assertEquals(AlertRegion.KyivCity, AlertRegionResolver.resolve(null, "м. Київ"))
        assertEquals(AlertRegion.KyivOblast, AlertRegionResolver.resolve("Бровари", "Київська область"))
    }

    @Test
    fun REQ_REGION_004_oblast_names_match_in_ukrainian_and_english_forms() {
        assertEquals(AlertRegion.Chernihiv, AlertRegionResolver.resolve(null, "Чернігівська обл."))
        assertEquals(AlertRegion.Chernihiv, AlertRegionResolver.resolve(null, "Chernihiv Oblast"))
        assertEquals(AlertRegion.Chernihiv, AlertRegionResolver.resolve(null, "  чернігівська   ОБЛАСТЬ "))
        assertEquals(AlertRegion.IvanoFrankivsk, AlertRegionResolver.resolve(null, "Ivano-Frankivsk Oblast"))
        assertEquals(AlertRegion.Lviv, AlertRegionResolver.resolve("Львівська область", null))
    }

    @Test
    fun REQ_REGION_004_an_unknown_result_resolves_to_nothing() {
        assertNull(AlertRegionResolver.resolve("Warszawa", "Mazowieckie"))
        assertNull(AlertRegionResolver.resolve(null, null))
        assertNull(AlertRegionResolver.resolve(" ", ""))
    }
}
