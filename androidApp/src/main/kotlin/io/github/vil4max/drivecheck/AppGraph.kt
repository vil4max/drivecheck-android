package io.github.vil4max.drivecheck

import android.content.Context
import io.github.vil4max.drivecheck.provider.AndroidProviderLog
import io.github.vil4max.drivecheck.provider.UbillingClient
import io.github.vil4max.drivecheck.provider.UrlConnectionTransport
import io.github.vil4max.drivecheck.store.PreferencesStatusStore
import io.github.vil4max.drivecheck.refresh.AndroidRefreshEnvironment
import io.github.vil4max.drivecheck.status.StatusController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Clock

// One client per process, so the unknown-key log and the 429 streak span the app, the widget and
// the notification (REQ-REGION-001, REQ-REFRESH-005).
object AppGraph {
    val client: UbillingClient by lazy {
        UbillingClient(
            transport = UrlConnectionTransport(),
            clock = Clock.System,
            userAgent = UbillingClient.userAgent(BuildConfig.VERSION_NAME),
            log = AndroidProviderLog(),
        )
    }

    fun store(context: Context) = PreferencesStatusStore(context.applicationContext)

    // One controller per process: the phone and the car share its state, its fetch floor and its
    // one refresh timer, counted by their starts and stops (REQ-REFRESH-002).
    @Volatile
    private var controller: StatusController? = null

    fun controller(context: Context): StatusController = controller ?: synchronized(this) {
        controller ?: StatusController(
            provider = client,
            clock = Clock.System,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            store = store(context),
            environment = AndroidRefreshEnvironment(context.applicationContext)::read,
        ).also { controller = it }
    }
}
