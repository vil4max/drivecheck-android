package io.github.vil4max.drivecheck.store

import android.content.Context
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import kotlin.time.Instant

// Private app storage (REQ-ANDROID-006). Nothing here leaves the device.
class PreferencesStatusStore(context: Context) : StatusStore {
    private val prefs = context.getSharedPreferences("drivecheck.status", Context.MODE_PRIVATE)

    override fun loadSnapshot(): AlertsSnapshot? = SnapshotCodec.decode(prefs.getString(KEY_SNAPSHOT, null))

    override fun saveSnapshot(snapshot: AlertsSnapshot) {
        prefs.edit().putString(KEY_SNAPSHOT, SnapshotCodec.encode(snapshot)).apply()
    }

    override fun loadRegion(): AlertRegion? =
        prefs.getString(KEY_REGION, null)?.let { name -> AlertRegion.entries.firstOrNull { it.name == name } }

    override fun saveRegion(region: AlertRegion) {
        prefs.edit().putString(KEY_REGION, region.name).apply()
    }

    override fun loadRateLimitedUntil(): Instant? =
        prefs.getLong(KEY_RATE_LIMIT, -1).takeIf { it >= 0 }?.let(Instant::fromEpochMilliseconds)

    override fun saveRateLimitedUntil(until: Instant) {
        prefs.edit().putLong(KEY_RATE_LIMIT, until.toEpochMilliseconds()).apply()
    }

    override fun isOnboardingDone(): Boolean = prefs.getBoolean(KEY_ONBOARDING, false)

    override fun setOnboardingDone() {
        prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
    }

    private companion object {
        const val KEY_SNAPSHOT = "snapshot.v1"
        const val KEY_REGION = "region.v1"
        const val KEY_RATE_LIMIT = "rateLimitedUntil.v1"
        const val KEY_ONBOARDING = "onboardingDone.v1"
    }
}
