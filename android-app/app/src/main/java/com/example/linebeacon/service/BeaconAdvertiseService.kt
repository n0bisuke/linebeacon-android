package com.example.linebeacon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.linebeacon.BeaconApplication
import com.example.linebeacon.MainActivity
import com.example.linebeacon.R
import com.example.linebeacon.beacon.AdvertiseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the BLE advertising session alive while the
 * screen is off. foregroundServiceType="connectedDevice" is declared in the
 * manifest; on Android 14+ the connectedDevice type additionally requires the
 * BLUETOOTH_CONNECT permission to be granted when the service starts, so the
 * UI must ask for it before calling [android.content.Context.startForegroundService].
 */
class BeaconAdvertiseService : Service() {

    private val controller by lazy { (application as BeaconApplication).controller }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        watchStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                controller.log("発信停止(サービス停止要求)")
                controller.stopAdvertising()
                stopForegroundInternal()
                stopSelf()
            }

            else -> {
                startForegroundCompat()
                controller.startAdvertising()?.let { error ->
                    controller.log("開始失敗: $error")
                    stopForegroundInternal()
                    stopSelf()
                }
            }
        }
        // Advertising must survive the UI being removed; we manage our own lifecycle.
        return START_NOT_STICKY
    }

    /**
     * Stops the service whenever the advertising session ends or fails.
     *
     * `drop(1)` skips the status value that was already set *before* this
     * service instance started (e.g. a leftover `Stopped` from the previous
     * session). Without it, restarting right after a stop would make the
     * watcher kill the freshly started service before `startForeground()` is
     * called, crashing with ForegroundServiceDidNotStartInTimeException.
     */
    private fun watchStatus() {
        scope.launch {
            controller.advertiser.status.drop(1).collect { status ->
                if (status is AdvertiseStatus.Failed || status is AdvertiseStatus.Stopped) {
                    stopForegroundInternal()
                    stopSelf()
                }
            }
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundInternal() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BeaconAdvertiseService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.linebeacon.action.STOP"
        private const val CHANNEL_ID = "beacon_advertising"
        private const val NOTIFICATION_ID = 1

        fun startIntent(context: Context): Intent =
            Intent(context, BeaconAdvertiseService::class.java)

        fun stopIntent(context: Context): Intent =
            Intent(context, BeaconAdvertiseService::class.java).setAction(ACTION_STOP)
    }
}
