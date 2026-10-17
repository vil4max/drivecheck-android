package io.github.vil4max.drivecheck.status

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.provider.AlertsProvider
import io.github.vil4max.drivecheck.provider.ProviderFailure
import io.github.vil4max.drivecheck.refresh.FetchGate
import io.github.vil4max.drivecheck.refresh.RefreshEnvironment
import io.github.vil4max.drivecheck.refresh.RefreshPolicy
import io.github.vil4max.drivecheck.store.InMemoryStatusStore
import io.github.vil4max.drivecheck.store.StatusStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

// How a refresh the driver started ends (REQ-REFRESH-011): a confirm haptic when a request
// succeeded or a hold kept it, a reject haptic only when the provider request failed.
enum class RefreshOutcome { Confirm, Reject }

sealed interface StatusState {
    data object Checking : StatusState
    data class Quiet(val checkedAt: Instant) : StatusState
    data class Alarm(val checkedAt: Instant) : StatusState
    data object Unavailable : StatusState
    data object RegionUnavailable : StatusState
}

// Triggers are expected on one thread (the Android main thread); the fetch and the periodic loop
// run in [scope].
class StatusController(
    private val provider: AlertsProvider,
    private val clock: Clock,
    private val scope: CoroutineScope,
    region: AlertRegion = AlertRegion.KyivCity,
    private val store: StatusStore = InMemoryStatusStore(),
    private val environment: () -> RefreshEnvironment = { RefreshEnvironment() },
    private val jitter: () -> Double = { Random.nextDouble() },
) {
    // REQ-REGION-008: the last region stays; the given default (Kyiv city) only when none is stored.
    private val _region = MutableStateFlow(store.loadRegion() ?: region)
    val region: StateFlow<AlertRegion> = _region.asStateFlow()

    private val _snapshot = MutableStateFlow(store.loadSnapshot())
    val snapshot: StateFlow<AlertsSnapshot?> = _snapshot.asStateFlow()

    // REQ-ANDROID-006: a stored snapshot is a known status from the first frame.
    private val _state = MutableStateFlow(_snapshot.value?.let(::resolve) ?: StatusState.Checking)
    val state: StateFlow<StatusState> = _state.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _refreshOutcomes = MutableSharedFlow<RefreshOutcome>(extraBufferCapacity = 8)
    val refreshOutcomes: SharedFlow<RefreshOutcome> = _refreshOutcomes.asSharedFlow()
    private var driverIsWaiting = false

    // The pull indicator: only a refresh the driver started, not the start or a scheduled poll.
    private val _isDriverRefreshing = MutableStateFlow(false)
    val isDriverRefreshing: StateFlow<Boolean> = _isDriverRefreshing.asStateFlow()

    private val gate = FetchGate(rateLimitedUntil = store.loadRateLimitedUntil())
    private var inFlight: Job? = null
    private var polling: Job? = null
    private var isSurfaceStarted = false

    fun onSurfaceStarted() {
        isSurfaceStarted = true
        trigger()
        startPolling()
    }

    fun onSurfaceStopped() {
        isSurfaceStarted = false
        polling?.cancel()
        polling = null
    }

    fun refresh() = trigger(byDriver = true)

    // A committed region shows from the held snapshot at once and is saved; the refresh that
    // follows goes through the usual holds (iOS refresh policy, "Region change").
    fun selectRegion(region: AlertRegion) {
        if (region == _region.value) return
        // Saved before it is published: the widget redraws from the store on each emission.
        store.saveRegion(region)
        _region.value = region
        _snapshot.value?.let { _state.value = resolve(it) }
        trigger()
    }

    fun isRateLimited(): Boolean = gate.isRateLimited(clock.now())

    // The current base interval, which also sets the stale threshold (REQ-REFRESH-006).
    fun baseInterval(): Duration = RefreshPolicy.baseInterval(currentEnvironment())

    private fun currentEnvironment(): RefreshEnvironment =
        environment().copy(isAlarmActive = _state.value is StatusState.Alarm)

    // REQ-REFRESH-002: one loop while a surface is started; each wait is recomputed and jittered.
    private fun startPolling() {
        if (polling?.isActive == true) return
        polling = scope.launch {
            while (isActive) {
                // Wait for a fetch in flight, so the next interval sees its answer (an alarm
                // shortens it to 30 s).
                inFlight?.join()
                delay(RefreshPolicy.interval(currentEnvironment(), jitter()))
                trigger(isScheduled = true)
            }
        }
    }

    // REQ-REFRESH-001: no request without a started surface. REQ-PROVIDER-002: a trigger during a
    // request joins it. REQ-REFRESH-010: the floor holds the rest without touching the state.
    private fun trigger(isScheduled: Boolean = false, byDriver: Boolean = false) {
        if (!isSurfaceStarted) return
        if (inFlight?.isActive == true) {
            // A driver's pull waits for the request already running and gets its outcome.
            if (byDriver) {
                driverIsWaiting = true
                _isDriverRefreshing.value = true
            }
            return
        }
        if (gate.isHeld(isScheduled, clock.now())) {
            if (byDriver) _refreshOutcomes.tryEmit(RefreshOutcome.Confirm)
            return
        }
        gate.recordStart(clock.now())
        _isChecking.value = true
        driverIsWaiting = byDriver
        _isDriverRefreshing.value = byDriver
        inFlight = scope.launch {
            var succeeded = false
            try {
                succeeded = fetch()
            } finally {
                // The client's own retry may have started a request late in the fetch; stamping
                // the end keeps the next request at least the gap after it (REQ-ANDROID-001).
                gate.recordStart(clock.now())
                _isChecking.value = false
                _isDriverRefreshing.value = false
                if (driverIsWaiting) {
                    _refreshOutcomes.tryEmit(if (succeeded) RefreshOutcome.Confirm else RefreshOutcome.Reject)
                    driverIsWaiting = false
                }
            }
        }
    }

    private suspend fun fetch(): Boolean {
        try {
            val fetched = provider.fetchAlerts()
            gate.recordSuccess(clock.now())
            store.saveSnapshot(fetched)
            _snapshot.value = fetched
            _state.value = resolve(fetched)
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (rateLimited: ProviderFailure.RateLimited) {
            store.saveRateLimitedUntil(gate.recordRateLimit(rateLimited.until, clock.now()))
            showUnavailableWithoutSnapshot()
        } catch (failure: Exception) {
            showUnavailableWithoutSnapshot()
        }
        return false
    }

    // REQ-REFRESH-006: a failure never replaces a known status.
    private fun showUnavailableWithoutSnapshot() {
        if (_snapshot.value == null) _state.value = StatusState.Unavailable
    }

    private fun resolve(snapshot: AlertsSnapshot): StatusState = when (snapshot.status(_region.value)) {
        AlertStatus.Alarm -> StatusState.Alarm(snapshot.checkedAt)
        AlertStatus.Quiet -> StatusState.Quiet(snapshot.checkedAt)
        null -> StatusState.RegionUnavailable
    }
}
