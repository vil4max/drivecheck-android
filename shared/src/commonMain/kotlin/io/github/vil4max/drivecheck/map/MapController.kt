package io.github.vil4max.drivecheck.map

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.provider.HttpRequest
import io.github.vil4max.drivecheck.provider.HttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed interface MapState {
    data object Idle : MapState
    data object Loading : MapState
    class Loaded(val image: ByteArray, val fetchedAt: Instant) : MapState
    data object Failed : MapState
}

// "{n} of 25 regions under alert · {minutes} min ago", the age being the image's own (REQ-ANDROID-002).
data class MapCaption(val alerts: Int, val total: Int, val minutes: Long) {
    companion object {
        fun of(alerts: Int, fetchedAt: Instant, now: Instant) =
            MapCaption(alerts, AlertRegion.entries.size, (now - fetchedAt).inWholeMinutes.coerceAtLeast(0))
    }
}

// The iOS inline map (MapViewModel): once per foreground session, 3 s after the status settles
// (the host's rate limit is shared), no polling, no reload on Refresh, a failure retried only by
// "Refresh map", and never a previous image after a failure.
class MapController(
    private val transport: HttpTransport,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val userAgent: String,
) {
    private val _state = MutableStateFlow<MapState>(MapState.Idle)
    val state: StateFlow<MapState> = _state.asStateFlow()

    private var isCardShown = false
    private var isStatusSettled = false
    private var pending: Job? = null

    fun onNewSession() {
        pending?.cancel()
        isStatusSettled = false
        _state.value = MapState.Idle
    }

    fun onCardShown() {
        isCardShown = true
        scheduleIfReady()
    }

    fun onStatusSettled() {
        isStatusSettled = true
        scheduleIfReady()
    }

    fun retry() {
        if (_state.value != MapState.Failed) return
        pending = scope.launch { load() }
    }

    private fun scheduleIfReady() {
        if (!isCardShown || !isStatusSettled || _state.value != MapState.Idle || pending?.isActive == true) return
        pending = scope.launch {
            delay(SETTLE_DELAY)
            load()
        }
    }

    private suspend fun load() {
        _state.value = MapState.Loading
        _state.value = try {
            val response = transport.execute(
                HttpRequest(MAP_URL, timeout = 15.seconds, bypassCache = true, headers = mapOf("User-Agent" to userAgent)),
            )
            val type = response.headers["content-type"].orEmpty()
            if (response.status in 200..299 && type.startsWith("image/") && response.body.isNotEmpty()) {
                MapState.Loaded(response.body, clock.now())
            } else {
                MapState.Failed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            MapState.Failed
        }
    }

    companion object {
        // The app is always dark, so the map is always the night variant; "rednight" is never
        // used because a red tint reads as an alarm.
        const val MAP_URL = "https://ubilling.net.ua/aerialalerts/?map=nightmode"
        val SETTLE_DELAY = 3.seconds
    }
}
