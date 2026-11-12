package com.example.linebeacon.beacon

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Advertisement lifecycle state shown in the UI. */
sealed interface AdvertiseStatus {
    data object Idle : AdvertiseStatus
    data object Starting : AdvertiseStatus
    data class Advertising(
        val frameHex: String,
        val intervalMs: Int,
        val txPowerDbm: Int,
    ) : AdvertiseStatus

    data class Stopped(val message: String?) : AdvertiseStatus
    data class Failed(val message: String) : AdvertiseStatus
}

/** Result of [BeaconAdvertiser.start]: false means it never even started. */
sealed interface StartResult {
    data object Started : StartResult
    data class Rejected(val message: String) : StartResult
}

data class BluetoothCapabilities(
    /** Device has a Bluetooth adapter at all. */
    val bluetoothSupported: Boolean,
    /** Bluetooth radio is currently on. */
    val bluetoothEnabled: Boolean,
    /** [BluetoothLeAdvertiser] is available on this device. */
    val advertiserAvailable: Boolean,
    /** Informational: multiple / concurrent advertisement support. */
    val multipleAdvertisementSupported: Boolean,
)

/**
 * Owns the BLE advertising session for one LINE Simple Beacon frame.
 *
 * Uses the modern [BluetoothLeAdvertiser.startAdvertisingSet] API
 * ([AdvertisingSetParameters] / [AdvertisingSetCallback], API 26+), which lets
 * us choose the advertising interval precisely. LINE's beacon device spec
 * strongly recommends a 152.5 ms interval; 244 x 0.625 ms = exactly 152.5 ms.
 *
 * The packet layout is built from [SimpleBeaconFrame] and passed to
 * [AdvertiseData] as Service Data for UUID 0xFE6F. Android then composes the
 * flags AD structure (0x06) and the 16-bit Service UUID AD (0x03) itself,
 * producing the packet described in the LINE Simple Beacon spec.
 */
class BeaconAdvertiser(context: Context) {

    private companion object {
        /**
         * LINE's beacon device spec: "152.5ミリ秒間隔で送信することを強く推奨します".
         * AdvertisingSetParameters interval unit is 0.625 ms, min 160 (=100 ms).
         */
        const val ADVERTISING_INTERVAL_UNITS = 244 // 244 * 0.625ms = 152.5ms
        val INTERVAL_MS = ADVERTISING_INTERVAL_UNITS * 0.625f
    }

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _status = MutableStateFlow<AdvertiseStatus>(AdvertiseStatus.Idle)
    val status: StateFlow<AdvertiseStatus> = _status.asStateFlow()

    private val _capabilities = MutableStateFlow(queryCapabilities())
    val capabilities: StateFlow<BluetoothCapabilities> = _capabilities.asStateFlow()

    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertisingSetCallback? = null
    private var startedFrameHex: String? = null

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    val wasRunning =
                        _status.value is AdvertiseStatus.Advertising || _status.value is AdvertiseStatus.Starting
                    resetSession()
                    refreshCapabilities()
                    if (wasRunning) {
                        _status.value = AdvertiseStatus.Failed("Bluetoothがオフになったため発信を停止しました")
                    }
                }

                BluetoothAdapter.STATE_ON -> refreshCapabilities()
            }
        }
    }

    init {
        // Keep tracking ON/OFF so the UI and any running session react to it.
        ContextCompat.registerReceiver(
            appContext,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun refreshCapabilities() {
        _capabilities.value = queryCapabilities()
    }

    private fun queryCapabilities(): BluetoothCapabilities {
        val a = adapter
        return BluetoothCapabilities(
            bluetoothSupported = a != null,
            bluetoothEnabled = runCatching { a?.isEnabled == true }.getOrDefault(false),
            advertiserAvailable = runCatching { a?.bluetoothLeAdvertiser != null }.getOrDefault(false),
            multipleAdvertisementSupported = runCatching {
                a?.isMultipleAdvertisementSupported == true
            }.getOrDefault(false),
        )
    }

    /**
     * Starts advertising [frame]. Returns [StartResult.Rejected] when a
     * precondition (Bluetooth on, permission granted, device support, data
     * size) is not met - the reason is set as the status.
     */
    fun start(frame: ByteArray): StartResult {
        val caps = queryCapabilities()
        _capabilities.value = caps

        if (!caps.bluetoothSupported) {
            return reject("この端末にはBluetoothアダプタがありません")
        }
        if (!caps.bluetoothEnabled) {
            return reject("Bluetoothが無効です。設定でBluetoothをオンにしてください")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_ADVERTISE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return reject("BLUETOOTH_ADVERTISE権限が許可されていません")
        }
        val hwidAdvertiser = try {
            // BLE advertising itself needs no location permission (only scanning does).
            adapter?.bluetoothLeAdvertiser
        } catch (e: SecurityException) {
            null
        }
        if (hwidAdvertiser == null) {
            return reject("この端末はBLE Advertisingに対応していません")
        }

        // Sanity check: a legacy (Bluetooth 4.0 style) advertisement has only
        // 31 bytes. Android adds a 3 byte flags AD, our service UUID AD is 4
        // bytes and the service data AD is 3 + frame size bytes.
        val legacyPayloadBytes = 3 + (4 + (3 + frame.size))
        if (legacyPayloadBytes > 31) {
            return reject("広告データがサイズ上限(31バイト)を超えます: フレーム${frame.size}バイト")
        }

        advertiser = hwidAdvertiser
        startedFrameHex = Hex.encode(frame)
        _status.value = AdvertiseStatus.Starting

        val params = AdvertisingSetParameters.Builder()
            .setLegacyMode(true) // Bluetooth 4.0 compatible advertising
            .setConnectable(false) // broadcaster role only
            .setScannable(true) // legacy non-connectable must stay scannable
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
            .setInterval(ADVERTISING_INTERVAL_UNITS)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SimpleBeaconFrame.SERVICE_UUID))
            .addServiceData(ParcelUuid(SimpleBeaconFrame.SERVICE_UUID), frame)
            .build()

        val cb = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: AdvertisingSet?,
                txPower: Int,
                status: Int,
            ) {
                if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    advertiser = hwidAdvertiser
                    _status.value = AdvertiseStatus.Advertising(
                        frameHex = startedFrameHex ?: "",
                        intervalMs = INTERVAL_MS.toInt(),
                        txPowerDbm = txPower,
                    )
                } else {
                    resetSession()
                    _status.value = AdvertiseStatus.Failed(
                        "Advertising開始に失敗しました (${errorCodeMessage(status)})"
                    )
                }
            }

            override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet) {
                resetSession()
                refreshCapabilities()
                _status.value = AdvertiseStatus.Stopped("Advertisingを停止しました")
            }
        }
        callback = cb

        return try {
            hwidAdvertiser.startAdvertisingSet(params, data, null, null, null, cb, mainHandler)
            StartResult.Started
        } catch (e: IllegalArgumentException) {
            resetSession()
            reject("広告データサイズ超過またはパラメータ不正: ${e.message}")
        } catch (e: SecurityException) {
            resetSession()
            reject("Bluetooth権限が不足しています: ${e.message}")
        } catch (e: IllegalStateException) {
            resetSession()
            reject("Bluetoothスタックの状態が不正です: ${e.message}")
        }
    }

    /** Stops the current advertising session (no-op when not running). */
    fun stop() {
        val adv = advertiser
        val cb = callback
        if (adv == null || cb == null) {
            if (_status.value !is AdvertiseStatus.Idle) {
                _status.value = AdvertiseStatus.Idle
            }
            return
        }
        try {
            adv.stopAdvertisingSet(cb)
        } catch (e: SecurityException) {
            resetSession()
            _status.value = AdvertiseStatus.Failed("停止に失敗しました: ${e.message}")
            return
        }
        resetSession()
        _status.value = AdvertiseStatus.Stopped("発信を停止しました")
    }

    private fun reject(message: String): StartResult.Rejected {
        resetSession()
        _status.value = AdvertiseStatus.Failed(message)
        return StartResult.Rejected(message)
    }

    private fun resetSession() {
        advertiser = null
        callback = null
        startedFrameHex = null
    }

    private fun errorCodeMessage(code: Int): String = when (code) {
        AdvertisingSetCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "広告データサイズ超過"
        AdvertisingSetCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "同時広告数の上限"
        AdvertisingSetCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "すでに開始済み"
        AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "内部エラー"
        AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "この端末は非対応"
        else -> "不明なエラー(code=$code)"
    }

    /** True when the given frame is currently being advertised. */
    val isRunning: Boolean
        get() = _status.value is AdvertiseStatus.Advertising
}
