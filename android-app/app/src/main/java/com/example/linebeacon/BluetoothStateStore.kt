package com.example.linebeacon

import android.content.Context
import android.content.SharedPreferences
import com.example.linebeacon.beacon.BeaconConfig
import com.example.linebeacon.beacon.SimpleBeaconFrame

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

    /** Device Message入力のモードがテキストだったかの記憶(HEX/テキスト切替)。 */
    var deviceMessageTextMode: Boolean
        get() = prefs.getBoolean(KEY_DEVICE_MESSAGE_TEXT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DEVICE_MESSAGE_TEXT_MODE, value).apply()

    /** テキストモードで入力された元のテキスト。 */
    var deviceMessageText: String
        get() = prefs.getString(KEY_DEVICE_MESSAGE_TEXT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DEVICE_MESSAGE_TEXT, value).apply()

    /**
     * 発信に使う実効Device Message(大文字HEX)。テキストモード時は入力テキストの
     * UTF-8 HEX変換結果を返す。変換不能(13バイト超過)ならnull。
     */
    fun effectiveDeviceMessage(): String? =
        if (deviceMessageTextMode) {
            SimpleBeaconFrame.deviceMessageHexFromText(deviceMessageText)
        } else {
            deviceMessage
        }

    /** Loads the saved inputs as a [BeaconConfig] when they are still valid. */
    fun loadConfig(): BeaconConfig? =
        BeaconConfig.newInstance(hwid, deviceMessage).getOrNull()

    companion object {
        private const val PREFS_NAME = "beacon_settings"
        private const val KEY_HWID = "hwid"
        private const val KEY_DEVICE_MESSAGE = "device_message"
        private const val KEY_DEVICE_MESSAGE_TEXT_MODE = "device_message_text_mode"
        private const val KEY_DEVICE_MESSAGE_TEXT = "device_message_text"
    }
}
