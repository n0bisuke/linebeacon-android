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

    /** 旧バージョン用(テキストモードboolean)。互換のため残置、新規は[deviceMessageMode]。 */
    var deviceMessageTextMode: Boolean
        get() = prefs.getBoolean(KEY_DEVICE_MESSAGE_TEXT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DEVICE_MESSAGE_TEXT_MODE, value).apply()

    /** テキストモードで入力された元のテキスト。 */
    var deviceMessageText: String
        get() = prefs.getString(KEY_DEVICE_MESSAGE_TEXT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DEVICE_MESSAGE_TEXT, value).apply()

    /** Device Messageのモード(HEX / テキスト / バッテリー自動)。旧設定はフォールバック。 */
    var deviceMessageMode: String
        get() = prefs.getString(KEY_DEVICE_MESSAGE_MODE, null)
            ?: if (deviceMessageTextMode) MODE_TEXT else MODE_HEX
        set(value) = prefs.edit().putString(KEY_DEVICE_MESSAGE_MODE, value).apply()

    /**
     * 発信に使う実効Device Message(大文字HEX)。テキストモード時は入力テキストの
     * UTF-8 HEX変換結果を返す(変換不能=13バイト超過ならnull)。
     * バッテリーモードは値が実行時に変わるため対象外(発信時に[BeaconController]が生成)。
     */
    fun effectiveDeviceMessage(): String? =
        when (deviceMessageMode) {
            MODE_TEXT -> SimpleBeaconFrame.deviceMessageHexFromText(deviceMessageText)
            MODE_BATTERY -> null
            else -> deviceMessage
        }

    /**
     * 画面・ログでHWIDをマスク表示するか。既定はON(公開画面スクショ等の安全策)。
     * 発信されるフレーム自体はマスクの有無にかかわらず本物のHWIDを送る。
     */
    var hwidMasked: Boolean
        get() = prefs.getBoolean(KEY_HWID_MASKED, true)
        set(value) = prefs.edit().putBoolean(KEY_HWID_MASKED, value).apply()

    /** Loads the saved inputs as a [BeaconConfig] when they are still valid. */
    fun loadConfig(): BeaconConfig? =
        BeaconConfig.newInstance(hwid, deviceMessage).getOrNull()

    companion object {
        private const val PREFS_NAME = "beacon_settings"
        private const val KEY_HWID = "hwid"
        private const val KEY_DEVICE_MESSAGE = "device_message"
        private const val KEY_DEVICE_MESSAGE_TEXT_MODE = "device_message_text_mode"
        private const val KEY_DEVICE_MESSAGE_TEXT = "device_message_text"
        private const val KEY_DEVICE_MESSAGE_MODE = "device_message_mode"
        private const val KEY_HWID_MASKED = "hwid_masked"

        const val MODE_HEX = "hex"
        const val MODE_TEXT = "text"
        const val MODE_BATTERY = "battery"
    }
}
