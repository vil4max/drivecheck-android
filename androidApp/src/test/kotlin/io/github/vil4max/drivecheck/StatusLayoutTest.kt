package io.github.vil4max.drivecheck

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusLayoutTest {
    @Test
    fun REQ_SURF_013_the_hero_shrinks_before_the_map_and_the_map_narrows_down_to_a_minimum() {
        val phone = StatusLayout.of(width = 412.dp, height = 915.dp, hasMap = true, hasNearby = true)
        assertEquals(StatusLayout.FULL_HERO, phone.heroSize, "a Pixel 7 fits everything, as an iPhone does")
        assertEquals(372.dp, phone.mapWidth)

        val medium = StatusLayout.of(width = 412.dp, height = 850.dp, hasMap = true, hasNearby = true)
        assertEquals(StatusLayout.COMPACT_HERO, medium.heroSize, "the hero gives way first")
        assertEquals(372.dp, medium.mapWidth)

        val landscape = StatusLayout.of(width = 900.dp, height = 1000.dp, hasMap = true, hasNearby = true)
        assertEquals(StatusLayout.COMPACT_HERO, landscape.heroSize)
        assertTrue(landscape.mapWidth < 860.dp && landscape.mapWidth >= StatusLayout.MIN_MAP_WIDTH, "${landscape.mapWidth}")

        val tiny = StatusLayout.of(width = 900.dp, height = 700.dp, hasMap = true, hasNearby = true)
        assertEquals(860.dp, tiny.mapWidth, "below the minimum the map keeps full width and the page scrolls")
    }
}
