package io.github.vil4max.drivecheck

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.status.NearbyLine

@StringRes
fun AlertRegion.nameRes(): Int = when (this) {
    AlertRegion.KyivCity -> R.string.region_kyiv_city
    AlertRegion.Vinnytsia -> R.string.region_vinnytsia
    AlertRegion.Volyn -> R.string.region_volyn
    AlertRegion.Dnipropetrovsk -> R.string.region_dnipropetrovsk
    AlertRegion.Donetsk -> R.string.region_donetsk
    AlertRegion.Zhytomyr -> R.string.region_zhytomyr
    AlertRegion.Zakarpattia -> R.string.region_zakarpattia
    AlertRegion.Zaporizhzhia -> R.string.region_zaporizhzhia
    AlertRegion.IvanoFrankivsk -> R.string.region_ivano_frankivsk
    AlertRegion.KyivOblast -> R.string.region_kyiv_oblast
    AlertRegion.Kirovohrad -> R.string.region_kirovohrad
    AlertRegion.Luhansk -> R.string.region_luhansk
    AlertRegion.Lviv -> R.string.region_lviv
    AlertRegion.Mykolaiv -> R.string.region_mykolaiv
    AlertRegion.Odesa -> R.string.region_odesa
    AlertRegion.Poltava -> R.string.region_poltava
    AlertRegion.Rivne -> R.string.region_rivne
    AlertRegion.Sumy -> R.string.region_sumy
    AlertRegion.Ternopil -> R.string.region_ternopil
    AlertRegion.Kharkiv -> R.string.region_kharkiv
    AlertRegion.Kherson -> R.string.region_kherson
    AlertRegion.Khmelnytskyi -> R.string.region_khmelnytskyi
    AlertRegion.Cherkasy -> R.string.region_cherkasy
    AlertRegion.Chernivtsi -> R.string.region_chernivtsi
    AlertRegion.Chernihiv -> R.string.region_chernihiv
}

// "Nearby: A, B +N" (REQ-SURF-005).
@Composable
fun nearbyText(regions: List<AlertRegion>): String? {
    val line = NearbyLine.of(regions) ?: return null
    val names = line.names.map { stringResource(it.nameRes()) }.joinToString(", ")
    val shown = if (line.more > 0) stringResource(R.string.nearby_names_more, names, line.more) else names
    return stringResource(R.string.nearby_line, shown)
}
