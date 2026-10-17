package io.github.vil4max.drivecheck.domain

import kotlin.time.Instant

enum class AlertStatus { Quiet, Alarm }

data class AlertsSnapshot(
    val source: String,
    val serverCachedAt: Instant?,
    val fetchedAt: Instant,
    val statuses: Map<AlertRegion, AlertStatus>,
) {
    val checkedAt: Instant get() = serverCachedAt ?: fetchedAt

    fun status(region: AlertRegion): AlertStatus? = statuses[region]
}
