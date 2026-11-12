package com.example.linebeacon

import android.content.Context
import com.example.linebeacon.beacon.BeaconAdvertiser
import com.example.linebeacon.beacon.BeaconConfig
import com.example.linebeacon.beacon.Hex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        val config = BeaconConfig.newInstance(settings.hwid, settings.deviceMessage)
            .getOrElse { error ->
                log("設定エラー: ${error.message}")
                return error.message
            }

        log(
            "開始: HWID=${config.hwidHex} DeviceMessage=${config.deviceMessageHex.ifEmpty { "(0x00)" }} " +
                "frame=${Hex.encode(config.frame)}"
        )
        return when (val result = advertiser.start(config.frame)) {
            is com.example.linebeacon.beacon.StartResult.Started -> null
            is com.example.linebeacon.beacon.StartResult.Rejected -> {
                log(result.message)
                result.message
            }
        }
    }

    fun stopAdvertising() {
        advertiser.stop()
    }

    private companion object {
        const val MAX_LOG_LINES = 100
    }
}
