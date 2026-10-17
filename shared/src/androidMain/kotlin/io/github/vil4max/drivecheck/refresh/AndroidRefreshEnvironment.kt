package io.github.vil4max.drivecheck.refresh

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager

// Android's counterparts of the iOS inputs to the polling interval (REQ-REFRESH-002): battery
// saver for Low Power Mode, thermal status severe for serious, Data Saver on a metered network for
// a constrained path.
class AndroidRefreshEnvironment(context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun read(): RefreshEnvironment = RefreshEnvironment(
        isPowerSaveMode = power?.isPowerSaveMode == true,
        isThermalSevere = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE,
        isDataSaverConstrained = connectivity != null && connectivity.isActiveNetworkMetered &&
            connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
    )
}
