package io.github.vil4max.drivecheck.region

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Instant

// Platform location without Google Play services (REQ-REGION-005): network and GPS providers,
// updates only after 2 km of movement, as the iOS distance filter.
class PlatformLocationSource(private val context: Context) {
    fun fixes(): Flow<LocationFix> = callbackFlow {
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null || !hasPermission()) {
            close()
            return@callbackFlow
        }
        val listener = LocationListener { location -> trySend(location.toFix()) }
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.allProviders.contains(it) }
        try {
            providers.forEach { provider ->
                manager.getLastKnownLocation(provider)?.let { trySend(it.toFix()) }
                manager.requestLocationUpdates(provider, 0L, MIN_DISTANCE_M, listener, Looper.getMainLooper())
            }
        } catch (denied: SecurityException) {
            close()
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isPrecise(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun Location.toFix() = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else -1.0,
        timestamp = Instant.fromEpochMilliseconds(time),
    )

    private companion object {
        const val MIN_DISTANCE_M = 2000f
    }
}

// Android's Geocoder in Ukrainian, for the iOS resolver's Ukrainian names (REQ-REGION-004).
class PlatformGeocoder(context: Context) : ReverseGeocoder {
    private val geocoder = Geocoder(context, Locale.forLanguageTag("uk-UA"))

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): GeoAddress? {
        if (!Geocoder.isPresent()) error("no geocoder on this device")
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(
                    latitude,
                    longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            continuation.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            continuation.resumeWithException(IllegalStateException(errorMessage ?: "geocoder error"))
                        }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        }
        return address?.let { GeoAddress(it.countryCode, it.locality, it.adminArea) }
    }
}
