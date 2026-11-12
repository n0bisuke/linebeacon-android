package com.example.linebeacon

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.linebeacon.beacon.BeaconAdvertiser
import com.example.linebeacon.beacon.BeaconConfig
import com.example.linebeacon.beacon.Hex
import com.example.linebeacon.beacon.SimpleBeaconFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application-scoped coordinator shared between the Compose UI and the
 * foreground service. UI stays thin: it edits input fields and calls
 * [startAdvertising] / [stopAdvertising].
 */
class BeaconController(context: Context) {

    val advertiser = BeaconAdvertiser(context)
    val settings = SettingsStore(context)

    private val context: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var dynamicDmJob: Job? = null
    private var activeConfig: BeaconConfig? = null

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    data class LogEntry(val time: String, val message: String) {
        override fun toString(): String = "[$time] $message"
    }

    fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.update { (listOf(LogEntry(time, message)) + it).take(MAX_LOG_LINES) }
    }

    /**
     * Validates the current input, persists it and starts advertising.
     * Returns a human readable error message, or null on success.
     */
    fun startAdvertising(): String? {
        // テキストモード時は入力テキストをUTF-8 HEXに変換した値を発信に使う(HEXモードでは
        // 保存値そのもの)。UIとサービスのどちらから起動しても同じdmになる
        // バッテリーモード時は実行時の残量から生成する
        val deviceMessageHex: String = when (settings.deviceMessageMode) {
            SettingsStore.MODE_BATTERY -> SimpleBeaconFrame.batteryDeviceMessageHex(readBatteryLevel())
            else -> settings.effectiveDeviceMessage()
                ?: run {
                    val error = "テキストモードのDevice MessageがUTF-8で13バイトを超えています(日本語は最大4文字)"
                    log("設定エラー: $error")
                    return error
                }
        }
        val config = BeaconConfig.newInstance(settings.hwid, deviceMessageHex)
            .getOrElse { error ->
                log("設定エラー: ${error.message}")
                return error.message
            }

        log(
            "開始: HWID=${displayHwid(config.hwidHex)} DeviceMessage=${config.deviceMessageHex.ifEmpty { "(0x00)" }} " +
                "frame=${displayFrame(Hex.encode(config.frame))}"
        )
        return when (val result = advertiser.start(config.frame)) {
            is com.example.linebeacon.beacon.StartResult.Started -> {
                activeConfig = config
                if (settings.deviceMessageMode == SettingsStore.MODE_BATTERY) {
                    startDynamicDmUpdates()
                }
                null
            }
            is com.example.linebeacon.beacon.StartResult.Rejected -> {
                log(result.message)
                result.message
            }
        }
    }

    fun stopAdvertising() {
        dynamicDmJob?.cancel()
        dynamicDmJob = null
        advertiser.stop()
    }

    /** ログ表示用: マスク設定が有効ならHWIDをぼかす。 */
    private fun displayHwid(hwidHex: String): String =
        if (settings.hwidMasked) SimpleBeaconFrame.maskHwid(hwidHex) else hwidHex

    /** ログ表示用: マスク設定が有効ならフレーム内のHWID部分をぼかす。 */
    private fun displayFrame(frameHex: String): String =
        if (settings.hwidMasked) SimpleBeaconFrame.maskHwidInFrameHex(frameHex) else frameHex

    /**
     * バッテリー残量(%)を取得。BATTERY_PROPERTY_CAPACITYが取れない端末では
     * sticky broadcastのlevel/scaleから算出する。不明時は -1。
     */
    fun readBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = runCatching { bm!!.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
            .getOrDefault(-1)
        if (capacity in 1..100) return capacity
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level in 0..Int.MAX_VALUE && scale > 0) (level * 100) / scale else -1
    }

    /**
     * 発信セッションが続いている間、60秒ごとにバッテリー残量を読み直して値が
     * 変わっていれば広告データを差し替える。
     */
    private fun startDynamicDmUpdates() {
        dynamicDmJob?.cancel()
        dynamicDmJob = scope.launch {
            var sentHex = ""
            while (advertiser.isRunning) {
                val config = activeConfig ?: break
                val level = readBatteryLevel()
                if (level in 0..100) {
                    val hex = SimpleBeaconFrame.batteryDeviceMessageHex(level)
                    val dmBytes = SimpleBeaconFrame.deviceMessageBytes(hex)!!
                    val frame = SimpleBeaconFrame.buildFrame(
                        Hex.decode(config.hwidHex)!!,
                        dmBytes,
                    )
                    // 値が変わった時だけ端末側の広告データを差し替える
                    if (hex != sentHex && advertiser.updateAdvertisingData(frame)) {
                        sentHex = hex
                        log("バッテリー更新: dm=$hex frame=${displayFrame(Hex.encode(frame))}")
                    }
                }
                delay(DYNAMIC_DM_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val MAX_LOG_LINES = 100

        /** 動的dm(バッテリー)の再読み込み間隔。 */
        const val DYNAMIC_DM_INTERVAL_MS = 60_000L
    }
}
