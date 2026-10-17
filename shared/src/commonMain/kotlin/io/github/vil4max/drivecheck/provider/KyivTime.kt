package io.github.vil4max.drivecheck.provider

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlin.time.Instant

internal object KyivTime {
    private val cachedAtFormat = LocalDateTime.Format {
        date(LocalDate.Formats.ISO)
        char(' ')
        time(LocalTime.Formats.ISO)
    }

    // Older Android time zone databases know the zone only by its former name.
    private val zone: TimeZone by lazy {
        runCatching { TimeZone.of("Europe/Kyiv") }.getOrElse { TimeZone.of("Europe/Kiev") }
    }

    fun parseCachedAt(raw: String): Instant? =
        runCatching { cachedAtFormat.parse(raw).toInstant(zone) }.getOrNull()
}
