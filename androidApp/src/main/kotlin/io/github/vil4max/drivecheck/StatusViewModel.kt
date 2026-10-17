package io.github.vil4max.drivecheck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.provider.AndroidProviderLog
import io.github.vil4max.drivecheck.provider.UbillingClient
import io.github.vil4max.drivecheck.provider.UrlConnectionTransport
import io.github.vil4max.drivecheck.refresh.AndroidRefreshEnvironment
import io.github.vil4max.drivecheck.status.StatusController
import io.github.vil4max.drivecheck.status.StatusPresentation
import io.github.vil4max.drivecheck.status.StatusPresenter
import io.github.vil4max.drivecheck.store.PreferencesStatusStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

data class StatusScreenState(val presentation: StatusPresentation, val region: AlertRegion, val isChecking: Boolean)

class StatusViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = StatusController(
        provider = client,
        clock = Clock.System,
        scope = viewModelScope,
        store = PreferencesStatusStore(application),
        environment = AndroidRefreshEnvironment(application)::read,
    )

    // Staleness depends on time passing, so the presentation is recomputed on a slow tick too.
    private val tick = flow {
        while (true) {
            emit(Unit)
            delay(10.seconds)
        }
    }

    private fun present(): StatusPresentation = StatusPresenter.present(
        state = controller.state.value,
        snapshot = controller.snapshot.value,
        region = controller.region.value,
        now = Clock.System.now(),
        baseInterval = controller.baseInterval(),
    )

    val state: StateFlow<StatusScreenState> =
        combine(controller.state, controller.snapshot, controller.isChecking, controller.region, tick) { _, _, isChecking, region, _ ->
            StatusScreenState(present(), region, isChecking)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StatusScreenState(present(), controller.region.value, controller.isChecking.value),
        )

    fun onScreenStarted() = controller.onSurfaceStarted()

    fun onScreenStopped() = controller.onSurfaceStopped()

    fun refresh() = controller.refresh()

    private companion object {
        // One client per process, so the unknown-key log and the 429 streak span every screen
        // instance (REQ-REGION-001, REQ-REFRESH-005).
        val client = UbillingClient(
            transport = UrlConnectionTransport(),
            clock = Clock.System,
            userAgent = UbillingClient.userAgent(BuildConfig.VERSION_NAME),
            log = AndroidProviderLog(),
        )
    }
}
