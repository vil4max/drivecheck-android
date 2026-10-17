package io.github.vil4max.drivecheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vil4max.drivecheck.provider.AndroidProviderLog
import io.github.vil4max.drivecheck.provider.UbillingClient
import io.github.vil4max.drivecheck.provider.UrlConnectionTransport
import io.github.vil4max.drivecheck.status.StatusController
import io.github.vil4max.drivecheck.status.StatusState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock

data class StatusScreenState(val status: StatusState, val isChecking: Boolean)

class StatusViewModel : ViewModel() {
    private val controller = StatusController(client, Clock.System, viewModelScope)

    val state: StateFlow<StatusScreenState> =
        combine(controller.state, controller.isChecking, ::StatusScreenState)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                StatusScreenState(controller.state.value, controller.isChecking.value),
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
