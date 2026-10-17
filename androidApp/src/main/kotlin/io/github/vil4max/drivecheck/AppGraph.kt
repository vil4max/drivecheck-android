package io.github.vil4max.drivecheck

import android.content.Context
import io.github.vil4max.drivecheck.provider.AndroidProviderLog
import io.github.vil4max.drivecheck.provider.UbillingClient
import io.github.vil4max.drivecheck.provider.UrlConnectionTransport
import io.github.vil4max.drivecheck.store.PreferencesStatusStore
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
}
