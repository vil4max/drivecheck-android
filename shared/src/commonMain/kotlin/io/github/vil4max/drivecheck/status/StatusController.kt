package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.provider.AlertsProvider
import io.github.vil4max.drivecheck.provider.ProviderFailure
import io.github.vil4max.drivecheck.refresh.FetchGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface StatusState {
    data object Checking : StatusState
    data class Quiet(val checkedAt: Instant) : StatusState
    data class Alarm(val checkedAt: Instant) : StatusState
    data object Unavailable : StatusState
    data object RegionUnavailable : StatusState
}

// Triggers are expected on one thread (the Android main thread); the fetch runs in [scope].
class StatusController(
    private val provider: AlertsProvider,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val region: AlertRegion = AlertRegion.KyivCity,
) {
    private val _state = MutableStateFlow<StatusState>(StatusState.Checking)
    val state: StateFlow<StatusState> = _state.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val gate = FetchGate()
    private var snapshot: AlertsSnapshot? = null
    private var inFlight: Job? = null
    private var isSurfaceStarted = false

    fun onSurfaceStarted() {
        isSurfaceStarted = true
        trigger()
    }

    fun onSurfaceStopped() {
        isSurfaceStarted = false
    }

    fun refresh() = trigger()

    fun isRateLimited(): Boolean = gate.isRateLimited(clock.now())

    // REQ-REFRESH-001: no request without a started surface. REQ-PROVIDER-002: a trigger during a
    // request joins it. REQ-REFRESH-010: the floor holds the rest without touching the state.
    private fun trigger(isScheduled: Boolean = false) {
        if (!isSurfaceStarted) return
        if (inFlight?.isActive == true) return
        if (gate.isHeld(isScheduled, clock.now())) return
        gate.recordStart(clock.now())
        _isChecking.value = true
        inFlight = scope.launch {
            try {
                fetch()
            } finally {
                // The client's own retry may have started a request late in the fetch; stamping
                // the end keeps the next request at least the gap after it (REQ-ANDROID-001).
                gate.recordStart(clock.now())
                _isChecking.value = false
            }
        }
    }

    private suspend fun fetch() {
        try {
            val fetched = provider.fetchAlerts()
            gate.recordSuccess(clock.now())
            snapshot = fetched
            _state.value = resolve(fetched)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (rateLimited: ProviderFailure.RateLimited) {
            gate.recordRateLimit(rateLimited.until, clock.now())
            showUnavailableWithoutSnapshot()
        } catch (failure: Exception) {
            showUnavailableWithoutSnapshot()
        }
    }

    // REQ-REFRESH-006: a failure never replaces a known status.
    private fun showUnavailableWithoutSnapshot() {
        if (snapshot == null) _state.value = StatusState.Unavailable
    }

    private fun resolve(snapshot: AlertsSnapshot): StatusState = when (snapshot.status(region)) {
        AlertStatus.Alarm -> StatusState.Alarm(snapshot.checkedAt)
        AlertStatus.Quiet -> StatusState.Quiet(snapshot.checkedAt)
        null -> StatusState.RegionUnavailable
    }
}
