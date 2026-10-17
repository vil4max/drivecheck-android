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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
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
    private var cycle: Job? = null
    private var lastFetchSucceeded = false
    private var inFlight: Job? = null
    private var polling: Job? = null
    // The phone and the car are counted: one timer runs while any of them is started, as the
    // iOS timer is shared between CarPlay and the phone (REQ-REFRESH-002).
    private var startedSurfaces = 0
    private val isSurfaceStarted get() = startedSurfaces > 0

    // The car passes fetchNow = false and starts its refresh cycle, whose first attempt is the
    // start's request (REQ-REFRESH-004).
    fun onSurfaceStarted(fetchNow: Boolean = true) {
        startedSurfaces += 1
        if (fetchNow) trigger()
        startPolling()
    }

    fun onSurfaceStopped() {
        startedSurfaces = maxOf(0, startedSurfaces - 1)
        if (isSurfaceStarted) return
        polling?.cancel()
        polling = null
    }

    fun refresh() = trigger(byDriver = true)

    // REQ-REFRESH-004, the iOS CarPlay cycle: up to 3 attempts, 2 s then 4 s apart, stopping at the
    // first answer or while rate limited. A new cycle supersedes the old one; the request in
    // flight is shared and never cancelled. An attempt the 2 s gap holds waits for it instead of
    // counting.
    fun startRefreshCycle() {
        cycle?.cancel()
        cycle = scope.launch {
            var attempts = 0
            while (attempts < CYCLE_BACKOFF.size) {
                if (!isSurfaceStarted || gate.isRateLimited(clock.now())) return@launch
                when (attemptAndWait()) {
                    Attempt.Answered -> return@launch
                    Attempt.Held -> delay(CYCLE_POLL)
                    Attempt.Failed -> {
                        attempts += 1
                        if (attempts < CYCLE_BACKOFF.size) delay(CYCLE_BACKOFF[attempts])
                    }
                }
            }
        }
    }

    private enum class Attempt { Answered, Failed, Held }

    private suspend fun attemptAndWait(): Attempt {
        val before = inFlight?.takeIf { it.isActive }
        trigger()
        val running = inFlight?.takeIf { it.isActive } ?: before
        if (running == null) {
            // Held: by the floor after a fresh answer, or by the gap after a failure.
            return if (lastFetchSucceeded) Attempt.Answered else Attempt.Held
        }
        running.join()
        return if (lastFetchSucceeded) Attempt.Answered else Attempt.Failed
    }

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

    private suspend fun fetch(): Boolean = doFetch().also { lastFetchSucceeded = it }

    private suspend fun doFetch(): Boolean {
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

// The wait before each attempt of the CarPlay cycle; the first goes at once (REQ-REFRESH-004).
private val CYCLE_BACKOFF = listOf(Duration.ZERO, 2.seconds, 4.seconds)
private val CYCLE_POLL = 250.milliseconds
