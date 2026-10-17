package io.github.vil4max.drivecheck.domain

import io.github.vil4max.drivecheck.domain.AlertRegion.Cherkasy
import io.github.vil4max.drivecheck.domain.AlertRegion.Chernihiv
import io.github.vil4max.drivecheck.domain.AlertRegion.KyivCity
import io.github.vil4max.drivecheck.domain.AlertRegion.KyivOblast
import io.github.vil4max.drivecheck.domain.AlertRegion.Lviv
import io.github.vil4max.drivecheck.domain.AlertRegion.Poltava
import io.github.vil4max.drivecheck.domain.AlertRegion.Rivne
import io.github.vil4max.drivecheck.domain.AlertRegion.Volyn
import io.github.vil4max.drivecheck.domain.AlertRegion.Zhytomyr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NearbyRegionPolicyTest {
    @Test
    fun REQ_SURF_005_the_neighbour_table_matches_ios() {
        assertEquals(listOf(KyivOblast, Chernihiv, Zhytomyr, Cherkasy, Poltava), NearbyRegionPolicy.nearbyRegions(KyivCity))
        assertEquals(listOf(Rivne, Lviv), NearbyRegionPolicy.nearbyRegions(Volyn))
        assertEquals(KyivCity, NearbyRegionPolicy.nearbyRegions(KyivOblast).first())
        AlertRegion.entries.forEach { region ->
            val neighbours = NearbyRegionPolicy.nearbyRegions(region)
            assertTrue(neighbours.isNotEmpty() && region !in neighbours, region.name)
        }
    }

    @Test
    fun REQ_SURF_005_nearby_alerts_are_the_neighbours_under_alert_in_table_order() {
        assertEquals(listOf(Chernihiv, Poltava), NearbyRegionPolicy.activeAlerts(KyivCity, listOf(Poltava, Lviv, Chernihiv)))
        assertEquals(emptyList(), NearbyRegionPolicy.activeAlerts(KyivCity, listOf(Lviv)))
    }

    @Test
    fun REQ_SURF_010_half_the_neighbours_under_alert_surround_a_region() {
        assertTrue(NearbyRegionPolicy.isSurrounded(Volyn, listOf(Rivne)), "1 of 2")
        assertFalse(NearbyRegionPolicy.isSurrounded(KyivOblast, listOf(Zhytomyr, Cherkasy)), "2 of 6")
        assertTrue(NearbyRegionPolicy.isSurrounded(KyivOblast, listOf(Zhytomyr, Cherkasy, Poltava)), "3 of 6")
    }

    @Test
    fun REQ_SURF_010_more_than_half_the_country_surrounds_a_region_with_one_neighbour_alerting() {
        val farAway = AlertRegion.entries.filter { it !in NearbyRegionPolicy.nearbyRegions(KyivOblast) && it != KyivOblast }
        val country = farAway.take(12) + Zhytomyr

        assertEquals(13, country.size)
        assertTrue(NearbyRegionPolicy.isSurrounded(KyivOblast, country), "13 of 25 with one neighbour")
        assertFalse(NearbyRegionPolicy.isSurrounded(KyivOblast, farAway.take(13)), "no neighbour alerting")
        assertFalse(NearbyRegionPolicy.isSurrounded(KyivOblast, farAway.take(11) + Zhytomyr), "12 of 25 is not more than half")
    }

    @Test
    fun REQ_SURF_010_kyiv_oblast_alone_surrounds_kyiv_city_and_not_the_other_way() {
        assertTrue(NearbyRegionPolicy.isSurrounded(KyivCity, listOf(KyivOblast)))
        assertFalse(NearbyRegionPolicy.isSurrounded(KyivOblast, listOf(KyivCity)))
    }
}
