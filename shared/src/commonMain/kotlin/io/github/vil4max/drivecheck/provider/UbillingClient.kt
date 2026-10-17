package io.github.vil4max.drivecheck.provider

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class UbillingClient(
    private val transport: HttpTransport,
    private val clock: Clock,
    private val userAgent: String,
    private val log: ProviderLog = ProviderLog {},
) {
    private val reportedUnknownKeys = mutableSetOf<String>()
    private val stateLock = Mutex()

    suspend fun fetchAlerts(): AlertsSnapshot = fetchOnce()

    private suspend fun fetchOnce(): AlertsSnapshot {
        val response = transport.execute(
            HttpRequest(ENDPOINT, timeout = 15.seconds, bypassCache = true, headers = mapOf("User-Agent" to userAgent)),
        )
        val fetchedAt = clock.now()
        check(response)
        val feed = try {
            json.decodeFromString<Feed>(response.body.decodeToString())
        } catch (error: IllegalArgumentException) {
            throw ProviderFailure.Undecodable(bodyPrefix(response.body), error)
        }

        val statuses = mutableMapOf<AlertRegion, AlertStatus>()
        for ((key, state) in feed.states) {
            val region = AlertRegion.fromApiKey(key)
            if (region == null) {
                reportUnknownKey(key)
                continue
            }
            statuses[region] = if (state.alertnow) AlertStatus.Alarm else AlertStatus.Quiet
        }
        return AlertsSnapshot(feed.source, KyivTime.parseCachedAt(feed.cachedat), fetchedAt, statuses)
    }

    private fun check(response: HttpResponse) {
        if (response.status !in 200..299) {
            throw ProviderFailure.HttpStatus(response.status, bodyPrefix(response.body))
        }
        val contentType = response.headers["content-type"]
        if (contentType != null && !contentType.contains("application/json", ignoreCase = true)) {
            throw ProviderFailure.NotJson(contentType, bodyPrefix(response.body))
        }
    }

    // The live feed repeats keys that are not app regions on every fetch (REQ-REGION-001).
    private suspend fun reportUnknownKey(key: String) {
        val isNew = stateLock.withLock { reportedUnknownKeys.add(key) }
        if (isNew) log.info("Ignoring unknown Ubilling region key=$key")
    }

    @Serializable
    private class Feed(val source: String, val cachedat: String, val states: Map<String, RegionState>)

    @Serializable
    private class RegionState(val alertnow: Boolean)

    companion object {
        const val ENDPOINT = "https://ubilling.net.ua/aerialalerts/"

        fun userAgent(appVersion: String) = "DriveCheck/$appVersion (Android)"

        private val json = Json { ignoreUnknownKeys = true }

        private fun bodyPrefix(body: ByteArray): String =
            if (body.isEmpty()) "<empty>" else body.copyOf(minOf(body.size, 240)).decodeToString()
    }
}
