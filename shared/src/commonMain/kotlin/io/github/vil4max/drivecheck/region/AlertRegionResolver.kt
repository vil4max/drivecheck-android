package io.github.vil4max.drivecheck.region

import io.github.vil4max.drivecheck.domain.AlertRegion

// The iOS AlertRegionResolver (REQ-REGION-004): Kyiv city wins over the oblast, oblast names match
// in Ukrainian and English forms, and an unknown result is null, which keeps the current region.
object AlertRegionResolver {
    private val kyivCityNames = setOf("київ", "kyiv", "kiev", "м київ", "m kyiv")

    fun resolve(cityName: String?, administrativeArea: String?): AlertRegion? {
        val city = normalize(cityName)
        val area = normalize(administrativeArea)
        if (city != null && city in kyivCityNames) return AlertRegion.KyivCity
        if (area != null) {
            if (area in kyivCityNames) return AlertRegion.KyivCity
            matchOblast(area)?.let { return it }
        }
        return city?.let(::matchOblast)
    }

    private fun matchOblast(value: String): AlertRegion? {
        val candidates = expandedVariants(value)
        return AlertRegion.entries.firstOrNull { region ->
            region != AlertRegion.KyivCity &&
                (normalize(region.apiKey) in candidates || englishName(region) in candidates)
        }
    }

    private fun expandedVariants(value: String): Set<String> {
        val stem = value
            .replace(" область", "")
            .replace(" обл", "")
            .replace(" oblast", "")
            .trim()
        return setOf(value, stem, "$stem область", "$stem oblast")
    }

    // Case-, apostrophe-, dot- and whitespace-insensitive, so matching does not depend on the
    // device locale.
    private fun normalize(raw: String?): String? {
        if (raw == null) return null
        val collapsed = raw.trim()
            .replace('’', '\'')
            .replace('ʼ', '\'')
            .replace(".", "")
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return collapsed.ifEmpty { null }
    }

    private fun englishName(region: AlertRegion): String = when (region) {
        AlertRegion.KyivCity -> "kyiv"
        AlertRegion.IvanoFrankivsk -> "ivano-frankivsk oblast"
        AlertRegion.KyivOblast -> "kyiv oblast"
        else -> region.name.lowercase() + " oblast"
    }
}
