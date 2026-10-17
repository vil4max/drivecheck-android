package io.github.vil4max.drivecheck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.map.MapController
import io.github.vil4max.drivecheck.map.MapState
import io.github.vil4max.drivecheck.provider.UbillingClient
import io.github.vil4max.drivecheck.provider.UrlConnectionTransport
import io.github.vil4max.drivecheck.region.PlatformGeocoder
import io.github.vil4max.drivecheck.region.PlatformLocationSource
import io.github.vil4max.drivecheck.region.RegionCoordinator
import io.github.vil4max.drivecheck.region.RegionTracker
import io.github.vil4max.drivecheck.status.StatusPresentation
import io.github.vil4max.drivecheck.status.StatusPresenter
import io.github.vil4max.drivecheck.store.PreferencesStatusStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

data class StatusScreenState(
    val presentation: StatusPresentation,
    val region: AlertRegion,
    val isChecking: Boolean,
    val isDriverRefreshing: Boolean = false,
    val isOnboardingDone: Boolean = true,
    val isLocationDenied: Boolean = false,
    val notice: AlertRegion? = null,
    val showOutsideSheet: Boolean = false,
    val shouldRequestPermission: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatusViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PreferencesStatusStore(application)
    private val location = PlatformLocationSource(application)
    private val controller = AppGraph.controller(application)
    private val coordinator = RegionCoordinator(
        tracker = RegionTracker(PlatformGeocoder(application), Clock.System),
        controller = controller,
        store = store,
        scope = viewModelScope,
        initiallyGranted = location.hasPermission(),
    )
    private val isStarted = MutableStateFlow(false)
    private val map = MapController(UrlConnectionTransport(), Clock.System, viewModelScope, UbillingClient.userAgent(BuildConfig.VERSION_NAME))
    val mapState: StateFlow<MapState> = map.state

    init {
        // REQ-ANDROID-002: the map waits until the status has settled after the session's start.
        controller.isChecking.onEach { checking -> if (!checking && isStarted.value) map.onStatusSettled() }.launchIn(viewModelScope)
        // Location runs only while the app is on screen and the coordinator allows it.
        combine(isStarted, coordinator.shouldTrackLocation) { started, allowed -> started && allowed }
            .flatMapLatest { track -> if (track) location.fixes() else emptyFlow() }
            .onEach(coordinator::onFix)
            .launchIn(viewModelScope)
        // The widget and the other surfaces read the store, so they follow every saved fetch.
        combine(controller.snapshot, controller.region) { _, _ -> }
            .onEach { Surfaces.updateAll(application) }
            .launchIn(viewModelScope)
    }

    // Staleness depends on time passing, so the presentation is recomputed on a slow tick too.
    private val tick = flow {
        while (true) {
            emit(Unit)
            delay(10.seconds)
        }
    }

    private fun current() = StatusScreenState(
        presentation = StatusPresenter.present(
            state = controller.state.value,
            snapshot = controller.snapshot.value,
            region = controller.region.value,
            now = Clock.System.now(),
            baseInterval = controller.baseInterval(),
        ),
        region = controller.region.value,
        isChecking = controller.isChecking.value,
        isDriverRefreshing = controller.isDriverRefreshing.value,
        isOnboardingDone = coordinator.isOnboardingDone.value,
        isLocationDenied = coordinator.isLocationDenied.value,
        notice = coordinator.notice.value,
        showOutsideSheet = coordinator.showOutsideSheet.value && coordinator.isOnboardingDone.value,
        shouldRequestPermission = coordinator.shouldRequestPermission.value,
    )

    val state: StateFlow<StatusScreenState> = combine(
        listOf(
            controller.state, controller.snapshot, controller.isChecking, controller.isDriverRefreshing, controller.region, tick,
            coordinator.isOnboardingDone, coordinator.isLocationDenied, coordinator.notice,
            coordinator.showOutsideSheet, coordinator.shouldRequestPermission,
        ),
    ) { current() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current())

    // A rotation stops and starts the screen but is not a new foreground session (the map stays).
    private var stoppedForConfigurationChange = false

    fun onScreenStarted() {
        isStarted.value = true
        if (!stoppedForConfigurationChange) map.onNewSession()
        stoppedForConfigurationChange = false
        controller.onSurfaceStarted()
        if (!controller.isChecking.value) map.onStatusSettled()
        // Coming back from the system settings may have changed the answer, and a granted start
        // learns whether the grant is precise or approximate.
        if (location.hasPermission()) onPermissionResult()
    }

    fun onScreenStopped(isChangingConfigurations: Boolean = false) {
        isStarted.value = false
        stoppedForConfigurationChange = isChangingConfigurations
        controller.onSurfaceStopped()
        coordinator.onTrackingStopped()
    }

    fun refresh() = controller.refresh()

    val refreshOutcomes = controller.refreshOutcomes

    fun onMapShown() = map.onCardShown()

    fun retryMap() = map.retry()

    fun snapshot() = controller.snapshot.value

    fun statusState() = controller.state.value

    fun completeOnboarding() = coordinator.completeOnboarding()

    fun hasLocationPermission() = location.hasPermission()

    fun onPermissionResult() = coordinator.onPermission(location.hasPermission(), location.isPrecise())

    fun dismissNotice() = coordinator.dismissNotice()

    fun dismissOutsideSheet() = coordinator.dismissOutsideSheet()
}
