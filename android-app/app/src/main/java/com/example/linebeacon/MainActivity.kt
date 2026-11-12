package com.example.linebeacon

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.linebeacon.beacon.AdvertiseStatus
import com.example.linebeacon.beacon.BeaconConfig
import com.example.linebeacon.beacon.SimpleBeaconFrame
import com.example.linebeacon.service.BeaconAdvertiseService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BeaconScreen()
                }
            }
        }
    }
}

private val OkGreen = Color(0xFF068C4A)
private val WarnOrange = Color(0xFFB26A00)
private val ErrRed = Color(0xFFB3261E)

@Composable
fun BeaconScreen() {
    val context = LocalContext.current
    val controller = (context.applicationContext as BeaconApplication).controller

    var hwid by remember { mutableStateOf(controller.settings.hwid) }
    var deviceMessage by remember { mutableStateOf(controller.settings.deviceMessage) }

    val status by controller.advertiser.status.collectAsState()
    val caps by controller.advertiser.capabilities.collectAsState()
    val logs by controller.logs.collectAsState()

    val hwidError = remember(hwid) { SimpleBeaconFrame.hwidError(hwid) }
    val dmError = remember(deviceMessage) { SimpleBeaconFrame.deviceMessageError(deviceMessage) }
    val config = remember(hwid, deviceMessage) {
        BeaconConfig.newInstance(hwid, deviceMessage).getOrNull()
    }

    fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val bluetoothPermissionsGranted =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            has(Manifest.permission.BLUETOOTH_ADVERTISE) && has(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }

    val running = status is AdvertiseStatus.Advertising || status is AdvertiseStatus.Starting

    // The start button stays tappable without the Bluetooth permission: tapping
    // it is what triggers the permission request.
    val canStart = config != null && hwidError == null && dmError == null &&
        caps.bluetoothSupported && caps.bluetoothEnabled && caps.advertiserAvailable && !running
    val showPermissionHint = canStart && !bluetoothPermissionsGranted

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.any {
                it == Manifest.permission.BLUETOOTH_ADVERTISE ||
                    it == Manifest.permission.BLUETOOTH_CONNECT
            }
        ) {
            controller.log("Bluetooth権限が拒否されたため発信を開始できません")
        } else {
            context.startForegroundService(BeaconAdvertiseService.startIntent(context))
        }
    }

    fun requestAndStart() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = permissions.filter { !has(it) }
        if (missing.isEmpty()) {
            context.startForegroundService(BeaconAdvertiseService.startIntent(context))
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text("LINE Beacon Transmitter", style = MaterialTheme.typography.headlineSmall)
            Text(
                "LINE Simple Beacon (UUID 0xFE6F / FrameType 0x02)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- 入力 ----
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ビーコン設定", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = hwid,
                    onValueChange = {
                        hwid = it
                        controller.settings.hwid = it
                    },
                    label = { Text("HWID (10桁のHEX)") },
                    supportingText = {
                        Text(hwidError ?: "LINE Official Account Managerで発行されたHWID")
                    },
                    isError = hwidError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = deviceMessage,
                    onValueChange = {
                        deviceMessage = it
                        controller.settings.deviceMessage = it
                    },
                    label = { Text("Device Message (最大26桁のHEX、空欄可)") },
                    supportingText = {
                        Text(
                            dmError
                                ?: if (deviceMessage.isBlank()) "空欄の場合は0x00を送信します"
                                else "Webhookのdmフィールドに入る値"
                        )
                    },
                    isError = dmError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                config?.let {
                    Text(
                        "Service Data (UUID 0xFE6F): 0x6FFE ${it.frameHex}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- 対応状況 ----
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("対応状況", style = MaterialTheme.typography.titleMedium)
                StatusRow(
                    "Bluetoothアダプタ",
                    if (caps.bluetoothSupported) "対応" else "非対応",
                    caps.bluetoothSupported,
                )
                StatusRow(
                    "Bluetooth",
                    if (caps.bluetoothEnabled) "オン" else "オフ(設定でオンにしてください)",
                    caps.bluetoothEnabled,
                )
                StatusRow(
                    "BLE Advertising",
                    if (caps.advertiserAvailable) "対応" else "非対応(この端末では発信できません)",
                    caps.advertiserAvailable,
                )
                StatusRow(
                    "複数同時広告",
                    if (caps.multipleAdvertisementSupported) "対応" else "非対応(参考情報)",
                    caps.multipleAdvertisementSupported
                )
                StatusRow(
                    "Bluetooth権限",
                    if (bluetoothPermissionsGranted) "許可済み" else "未許可(発信ボタンで要求します)",
                    bluetoothPermissionsGranted,
                )
            }
        }

        // ---- 発信状態 ----
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("発信状態", style = MaterialTheme.typography.titleMedium)
                when (val s = status) {
                    is AdvertiseStatus.Idle -> StatusText("待機中", MaterialTheme.colorScheme.onSurface)
                    is AdvertiseStatus.Starting -> StatusText("開始中…", WarnOrange)
                    is AdvertiseStatus.Advertising -> {
                        StatusText(
                            "発信中 (間隔 ${s.intervalMs}ms / TxPower ${s.txPowerDbm}dBm)",
                            OkGreen,
                        )
                        Text(
                            "frame: ${s.frameHex}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    is AdvertiseStatus.Stopped -> StatusText(s.message ?: "停止中", MaterialTheme.colorScheme.onSurface)
                    is AdvertiseStatus.Failed -> StatusText(s.message, ErrRed)
                }
            }
        }

        // ---- ボタン ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { requestAndStart() },
                enabled = canStart,
            ) {
                Text("発信開始")
            }
            OutlinedButton(
                onClick = { controller.stopAdvertising() },
                enabled = running,
            ) {
                Text("発信停止")
            }
        }
        if (showPermissionHint) {
            Text(
                "発信ボタンを押すと権限を確認します",
                color = WarnOrange,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (!canStart && !running && config != null && hwidError == null && dmError == null) {
            Text(
                when {
                    !caps.bluetoothEnabled -> "Bluetoothがオンになっている必要があります"
                    !caps.bluetoothSupported || !caps.advertiserAvailable ->
                        "この端末はBLE Advertisingに対応していません"
                    else -> ""
                },
                color = WarnOrange,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ---- ログ ----
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("ログ", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                if (logs.isEmpty()) {
                    Text("まだログはありません", style = MaterialTheme.typography.bodySmall)
                } else {
                    logs.take(30).forEach { entry ->
                        Text(
                            entry.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, ok: Boolean?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (ok) {
                true -> "✓"
                false -> "✗"
                null -> "–"
            },
            color = when (ok) {
                true -> OkGreen
                false -> ErrRed
                null -> Color.Gray
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        Text("$label: ", style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.bodyLarge)
}
