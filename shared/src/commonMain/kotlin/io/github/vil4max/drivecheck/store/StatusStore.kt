package io.github.vil4max.drivecheck.store

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

// What survives the process (REQ-ANDROID-006). Every read tolerates a missing or corrupt value.
interface StatusStore {
    fun loadSnapshot(): AlertsSnapshot?
    fun saveSnapshot(snapshot: AlertsSnapshot)
    fun loadRegion(): AlertRegion?
    fun saveRegion(region: AlertRegion)
    fun loadRateLimitedUntil(): Instant?
    fun saveRateLimitedUntil(until: Instant)
    fun isOnboardingDone(): Boolean
    fun setOnboardingDone()
}

class InMemoryStatusStore : StatusStore {
    private var snapshot: AlertsSnapshot? = null
    private var region: AlertRegion? = null
    private var rateLimitedUntil: Instant? = null
    private var onboardingDone = false

    override fun loadSnapshot() = snapshot
    override fun saveSnapshot(snapshot: AlertsSnapshot) {
        this.snapshot = snapshot
    }
    override fun loadRegion() = region
    override fun saveRegion(region: AlertRegion) {
        this.region = region
    }
    override fun loadRateLimitedUntil() = rateLimitedUntil
    override fun saveRateLimitedUntil(until: Instant) {
        rateLimitedUntil = until
    }
    override fun isOnboardingDone() = onboardingDone
    override fun setOnboardingDone() {
        onboardingDone = true
    }
}

object SnapshotCodec {
    @Serializable
    private class Stored(
        val source: String,
        val serverCachedAtMillis: Long? = null,
        val fetchedAtMillis: Long,
        val alarms: List<String>,
        val quiet: List<String>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: AlertsSnapshot): String = json.encodeToString(
        Stored.serializer(),
        Stored(
            source = snapshot.source,
            serverCachedAtMillis = snapshot.serverCachedAt?.toEpochMilliseconds(),
            fetchedAtMillis = snapshot.fetchedAt.toEpochMilliseconds(),
            alarms = snapshot.statuses.filterValues { it == AlertStatus.Alarm }.keys.map { it.name },
            quiet = snapshot.statuses.filterValues { it == AlertStatus.Quiet }.keys.map { it.name },
        ),
    )

    fun decode(raw: String?): AlertsSnapshot? {
        if (raw == null) return null
        val stored = runCatching { json.decodeFromString(Stored.serializer(), raw) }.getOrNull() ?: return null
        val statuses = buildMap {
            stored.quiet.mapNotNull(::region).forEach { put(it, AlertStatus.Quiet) }
            stored.alarms.mapNotNull(::region).forEach { put(it, AlertStatus.Alarm) }
        }
        return AlertsSnapshot(
            source = stored.source,
            serverCachedAt = stored.serverCachedAtMillis?.let(Instant::fromEpochMilliseconds),
            fetchedAt = Instant.fromEpochMilliseconds(stored.fetchedAtMillis),
            statuses = statuses,
        )
    }

    private fun region(name: String) = AlertRegion.entries.firstOrNull { it.name == name }
}
