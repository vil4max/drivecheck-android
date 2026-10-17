package io.github.vil4max.drivecheck.domain

import io.github.vil4max.drivecheck.domain.AlertRegion.Cherkasy
import io.github.vil4max.drivecheck.domain.AlertRegion.Chernihiv
import io.github.vil4max.drivecheck.domain.AlertRegion.Chernivtsi
import io.github.vil4max.drivecheck.domain.AlertRegion.Dnipropetrovsk
import io.github.vil4max.drivecheck.domain.AlertRegion.Donetsk
import io.github.vil4max.drivecheck.domain.AlertRegion.IvanoFrankivsk
import io.github.vil4max.drivecheck.domain.AlertRegion.Kharkiv
import io.github.vil4max.drivecheck.domain.AlertRegion.Kherson
import io.github.vil4max.drivecheck.domain.AlertRegion.Khmelnytskyi
import io.github.vil4max.drivecheck.domain.AlertRegion.Kirovohrad
import io.github.vil4max.drivecheck.domain.AlertRegion.KyivCity
import io.github.vil4max.drivecheck.domain.AlertRegion.KyivOblast
import io.github.vil4max.drivecheck.domain.AlertRegion.Luhansk
import io.github.vil4max.drivecheck.domain.AlertRegion.Lviv
import io.github.vil4max.drivecheck.domain.AlertRegion.Mykolaiv
import io.github.vil4max.drivecheck.domain.AlertRegion.Odesa
import io.github.vil4max.drivecheck.domain.AlertRegion.Poltava
import io.github.vil4max.drivecheck.domain.AlertRegion.Rivne
import io.github.vil4max.drivecheck.domain.AlertRegion.Sumy
import io.github.vil4max.drivecheck.domain.AlertRegion.Ternopil
import io.github.vil4max.drivecheck.domain.AlertRegion.Vinnytsia
import io.github.vil4max.drivecheck.domain.AlertRegion.Volyn
import io.github.vil4max.drivecheck.domain.AlertRegion.Zakarpattia
import io.github.vil4max.drivecheck.domain.AlertRegion.Zaporizhzhia
import io.github.vil4max.drivecheck.domain.AlertRegion.Zhytomyr

// The iOS NearbyRegionPolicy (REQ-SURF-005, REQ-SURF-010). Kyiv city deliberately uses a wider
// ring than its administrative border.
object NearbyRegionPolicy {
    fun activeAlerts(region: AlertRegion, activeAlerts: Collection<AlertRegion>): List<AlertRegion> {
        val active = activeAlerts.toSet()
        return nearbyRegions(region).filter { it in active }
    }

    // Surrounded: at least one neighbour under alert and either half or more of the neighbours
    // are, or more than half of the country is. Kyiv Oblast encloses Kyiv city, so its alert alone
    // is enough for the city; the exception is one-way.
    fun isSurrounded(region: AlertRegion, activeAlerts: Collection<AlertRegion>): Boolean {
        if (region == KyivCity && KyivOblast in activeAlerts) return true
        val alerting = activeAlerts(region, activeAlerts).size
        if (alerting == 0) return false
        return alerting * 2 >= nearbyRegions(region).size ||
            activeAlerts.toSet().size * 2 > AlertRegion.entries.size
    }

    fun nearbyRegions(region: AlertRegion): List<AlertRegion> = when (region) {
        KyivCity -> listOf(KyivOblast, Chernihiv, Zhytomyr, Cherkasy, Poltava)
        Vinnytsia -> listOf(Zhytomyr, KyivOblast, Cherkasy, Kirovohrad, Odesa, Chernivtsi, Khmelnytskyi)
        Volyn -> listOf(Rivne, Lviv)
        Dnipropetrovsk -> listOf(Poltava, Kharkiv, Donetsk, Zaporizhzhia, Kherson, Mykolaiv, Kirovohrad)
        Donetsk -> listOf(Luhansk, Kharkiv, Dnipropetrovsk, Zaporizhzhia)
        Zhytomyr -> listOf(Rivne, Khmelnytskyi, Vinnytsia, KyivOblast)
        Zakarpattia -> listOf(Lviv, IvanoFrankivsk)
        Zaporizhzhia -> listOf(Donetsk, Dnipropetrovsk, Kherson)
        IvanoFrankivsk -> listOf(Zakarpattia, Lviv, Ternopil, Chernivtsi)
        KyivOblast -> listOf(KyivCity, Zhytomyr, Vinnytsia, Cherkasy, Poltava, Chernihiv)
        Kirovohrad -> listOf(Vinnytsia, Cherkasy, Poltava, Dnipropetrovsk, Mykolaiv, Odesa)
        Luhansk -> listOf(Kharkiv, Donetsk)
        Lviv -> listOf(Volyn, Rivne, Ternopil, IvanoFrankivsk, Zakarpattia)
        Mykolaiv -> listOf(Odesa, Kirovohrad, Dnipropetrovsk, Kherson)
        Odesa -> listOf(Vinnytsia, Kirovohrad, Mykolaiv)
        Poltava -> listOf(KyivOblast, Chernihiv, Sumy, Kharkiv, Dnipropetrovsk, Kirovohrad, Cherkasy)
        Rivne -> listOf(Volyn, Lviv, Ternopil, Khmelnytskyi, Zhytomyr)
        Sumy -> listOf(Chernihiv, Poltava, Kharkiv)
        Ternopil -> listOf(Lviv, Rivne, Khmelnytskyi, Chernivtsi, IvanoFrankivsk)
        Kharkiv -> listOf(Sumy, Poltava, Dnipropetrovsk, Donetsk, Luhansk)
        Kherson -> listOf(Mykolaiv, Dnipropetrovsk, Zaporizhzhia)
        Khmelnytskyi -> listOf(Rivne, Zhytomyr, Vinnytsia, Chernivtsi, Ternopil)
        Cherkasy -> listOf(Vinnytsia, KyivOblast, Poltava, Kirovohrad)
        Chernivtsi -> listOf(IvanoFrankivsk, Ternopil, Khmelnytskyi, Vinnytsia)
        Chernihiv -> listOf(KyivOblast, Poltava, Sumy)
    }
}
