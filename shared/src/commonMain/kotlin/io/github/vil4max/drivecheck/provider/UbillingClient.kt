package io.github.vil4max.drivecheck.provider

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import kotlinx.coroutines.delay
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
) : AlertsProvider {
    private val reportedUnknownKeys = mutableSetOf<String>()

    // Consecutive 429s escalate the backoff (REQ-REFRESH-005). Only a decoded answer resets it:
    // a 5xx or a garbled body says nothing about whether the host stopped rate limiting.
    private var rateLimitStreak = 0
    private val stateLock = Mutex()

    override suspend fun fetchAlerts(): AlertsSnapshot = try {
        fetchOnce()
    } catch (failure: TransportFailure) {
        if (!failure.isTransient) throw failure
        log.info("Transient Ubilling failure ${failure.kind}, retrying once")
        delay(TRANSIENT_RETRY_DELAY)
        fetchOnce()
    }

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
        stateLock.withLock { rateLimitStreak = 0 }

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

    private suspend fun check(response: HttpResponse) {
        if (response.status == 429) {
            val attempt = stateLock.withLock { ++rateLimitStreak }
            val until = RetryAfter.deadline(response.headers["retry-after"], clock.now(), attempt)
            log.info("Ubilling HTTP 429, holding until $until")
            throw ProviderFailure.RateLimited(until)
        }
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
        val TRANSIENT_RETRY_DELAY = 2.seconds

        fun userAgent(appVersion: String) = "DriveCheck/$appVersion (Android)"

        private val json = Json { ignoreUnknownKeys = true }

        private fun bodyPrefix(body: ByteArray): String =
            if (body.isEmpty()) "<empty>" else body.copyOf(minOf(body.size, 240)).decodeToString()
    }
}
