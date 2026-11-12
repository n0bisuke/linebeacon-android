package com.example.linebeacon

import android.content.Context
import android.content.SharedPreferences
import com.example.linebeacon.beacon.BeaconConfig

/**
 * Persists the HWID / Device Message inputs in the device storage so the
 * user does not have to retype them on the next launch.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var hwid: String
        get() = prefs.getString(KEY_HWID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_HWID, value).apply()

    var deviceMessage: String
        get() = prefs.getString(KEY_DEVICE_MESSAGE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DEVICE_MESSAGE, value).apply()

    /** Loads the saved inputs as a [BeaconConfig] when they are still valid. */
    fun loadConfig(): BeaconConfig? =
        BeaconConfig.newInstance(hwid, deviceMessage).getOrNull()

    companion object {
        private const val PREFS_NAME = "beacon_settings"
        private const val KEY_HWID = "hwid"
        private const val KEY_DEVICE_MESSAGE = "device_message"
    }
}
