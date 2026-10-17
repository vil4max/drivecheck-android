package io.github.vil4max.drivecheck.region

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.status.StatusController
import io.github.vil4max.drivecheck.store.StatusStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Turns location fixes into region decisions, as iOS RegionSelection does: one evaluation at a
// time, a deferred fix retried when its wait ends, a commit shown as a notice, and the
// outside-Ukraine sheet. It also owns when location may run (REQ-REGION-009, REQ-REGION-010).
class RegionCoordinator(
    private val tracker: RegionTracker,
    private val controller: StatusController,
    private val store: StatusStore,
    private val scope: CoroutineScope,
) {
    private val evaluation = Mutex()
    private val outsideNotice = OutsideUkraineNotice()
    private var retry: Job? = null

    private val onboardingDone = MutableStateFlow(store.isOnboardingDone())
    val isOnboardingDone: StateFlow<Boolean> = onboardingDone.asStateFlow()

    private var permission: Boolean? = null
    private val _shouldRequestPermission = MutableStateFlow(false)
    val shouldRequestPermission: StateFlow<Boolean> = _shouldRequestPermission.asStateFlow()
    private val _shouldTrackLocation = MutableStateFlow(false)
    val shouldTrackLocation: StateFlow<Boolean> = _shouldTrackLocation.asStateFlow()
    private val _isLocationDenied = MutableStateFlow(false)
    val isLocationDenied: StateFlow<Boolean> = _isLocationDenied.asStateFlow()

    private val _notice = MutableStateFlow<AlertRegion?>(null)
    val notice: StateFlow<AlertRegion?> = _notice.asStateFlow()
    private val _showOutsideSheet = MutableStateFlow(false)
    val showOutsideSheet: StateFlow<Boolean> = _showOutsideSheet.asStateFlow()

    init {
        updateGates()
    }

    fun completeOnboarding() {
        store.setOnboardingDone()
        onboardingDone.value = true
        updateGates()
    }

    fun onPermission(granted: Boolean, precise: Boolean = true) {
        permission = granted
        tracker.approximateOnly = granted && !precise
        updateGates()
    }

    fun dismissNotice() {
        _notice.value = null
    }

    fun dismissOutsideSheet() {
        _showOutsideSheet.value = false
    }

    fun onFix(fix: LocationFix) {
        if (!_shouldTrackLocation.value) return
        scope.launch { evaluate(fix, isRetry = false) }
    }

    private fun updateGates() {
        val done = onboardingDone.value
        _shouldRequestPermission.value = done && permission == null
        _shouldTrackLocation.value = done && permission == true
        _isLocationDenied.value = done && permission == false
        if (!_shouldTrackLocation.value) retry?.cancel()
    }

    private suspend fun evaluate(fix: LocationFix, isRetry: Boolean) {
        evaluation.withLock {
            val outcome = tracker.evaluate(fix, controller.region.value, isRetry)
            // Only a newer fix the tracker acts on replaces a pending retry.
            if (outcome != RegionOutcome.Ignored) retry?.cancel()
            when (outcome) {
                is RegionOutcome.Committed -> {
                    controller.selectRegion(outcome.region)
                    _notice.value = outcome.region
                    outsideNotice.shouldShow(isOutside = false)
                }
                RegionOutcome.OutsideUkraine -> if (outsideNotice.shouldShow(isOutside = true)) _showOutsideSheet.value = true
                is RegionOutcome.Deferred -> retry = scope.launch {
                    delay(outcome.retryAfter)
                    evaluate(fix, isRetry = true)
                }
                is RegionOutcome.Candidate, RegionOutcome.Unchanged -> outsideNotice.shouldShow(isOutside = false)
                RegionOutcome.Ignored -> Unit
            }
        }
    }
}
