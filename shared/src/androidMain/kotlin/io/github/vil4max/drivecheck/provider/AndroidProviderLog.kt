package io.github.vil4max.drivecheck.provider

import android.util.Log

class AndroidProviderLog(private val tag: String = "DriveCheck.Provider") : ProviderLog {
    override fun info(message: String) {
        Log.i(tag, message)
    }
}
