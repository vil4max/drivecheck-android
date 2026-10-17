package io.github.vil4max.drivecheck

import android.content.Context

// Everything outside the app that reads the stored snapshot, refreshed together after any fetch.
object Surfaces {
    fun updateAll(context: Context) {
        StatusWidgetProvider.render(context)
    }
}
