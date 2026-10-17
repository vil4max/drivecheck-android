package io.github.vil4max.drivecheck.car

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.vil4max.drivecheck.AppGraph
import io.github.vil4max.drivecheck.Surfaces
import io.github.vil4max.drivecheck.status.StatusController
import io.github.vil4max.drivecheck.status.StatusPresenter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

// Android Auto (spec 004), in the weather category: the only listed car category with a status of
// the driver's current location.
class DriveCheckCarAppService : CarAppService() {
    // Release builds accept only the hosts Google lists; debug builds also run in the desktop head unit.
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()
        }

    override fun onCreateSession(): Session = DriveCheckCarSession()
}

// The car is a surface like the phone: it starts the shared refresh while it is on screen and
// reads the same controller, store and region (REQ-REFRESH-001, REQ-REFRESH-002). Connecting and
// the car's Refresh each run the CarPlay refresh cycle (REQ-REFRESH-004).
class DriveCheckCarSession(
    private val controllerOf: (Context) -> StatusController = AppGraph::controller,
) : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val app = carContext.applicationContext
        val controller = controllerOf(app)
        val store = AppGraph.store(app)
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    controller.onSurfaceStarted(fetchNow = false)
                    controller.startRefreshCycle()
                }

                override fun onStop(owner: LifecycleOwner) {
                    controller.cancelRefreshCycle()
                    controller.onSurfaceStopped()
                }
            },
        )
        // Staleness depends on time passing, as on the phone.
        val tick = flow {
            while (true) {
                emit(Unit)
                delay(10.seconds)
            }
        }
        fun current() = CarStatus(
            presentation = StatusPresenter.present(
                state = controller.state.value,
                snapshot = controller.snapshot.value,
                region = controller.region.value,
                now = Clock.System.now(),
                baseInterval = controller.baseInterval(),
            ),
            region = controller.region.value,
            isChecking = controller.isChecking.value,
            isLocationDenied = store.wasPermissionDenied(),
        )
        val status = combine(controller.state, controller.snapshot, controller.region, controller.isChecking, tick) { _, _, _, _, _ -> current() }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, current())
        controller.snapshot.onEach { Surfaces.updateAll(app) }.launchIn(lifecycleScope)
        return CarStatusScreen(carContext, status, controller::startRefreshCycle)
    }
}
