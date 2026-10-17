package io.github.vil4max.drivecheck

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class NotificationSwitchState(val isOn: Boolean, val isBlocked: Boolean)

// The driver's choice is kept apart from what Android allows, so the switch never promises
// what Android refuses (REQ-SURF-008).
class NotificationSettings(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("notification", Context.MODE_PRIVATE)
    private val manager = app.getSystemService(NotificationManager::class.java)

    var isChosen: Boolean
        get() = prefs.getBoolean(KEY_CHOSEN, true)
        set(value) = prefs.edit().putBoolean(KEY_CHOSEN, value).apply()

    fun needsPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    // After a refusal the system may not ask again, so the way back is Android settings.
    fun onPermissionRefused() = prefs.edit().putBoolean(KEY_REFUSED, true).apply()

    fun isBlocked(): Boolean {
        if (needsPermission()) return prefs.getBoolean(KEY_REFUSED, false)
        if (!manager.areNotificationsEnabled()) return true
        return manager.getNotificationChannel(AlertNotifier.CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
    }

    fun isAllowed(): Boolean = isChosen && !needsPermission() && !isBlocked()

    fun state() = NotificationSwitchState(isOn = isAllowed(), isBlocked = isBlocked())

    private companion object {
        const val KEY_CHOSEN = "chosen"
        const val KEY_REFUSED = "permission_refused"
    }
}
