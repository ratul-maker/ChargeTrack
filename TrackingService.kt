package com.ratulmaker.chargetrack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TrackingService : Service() {
    private var receiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra("level", -1)
            val scale = intent.getIntExtra("scale", 100).coerceAtLeast(1)
            val status = intent.getIntExtra("status", -1)
            val pluggedValue = intent.getIntExtra("plugged", 0)
            val plugged = pluggedValue != 0 &&
                status != android.os.BatteryManager.BATTERY_STATUS_DISCHARGING
            if (level >= 0) {
                val percent = (level * 100f / scale).toInt().coerceIn(0, 100)
                ChargeData.updateBattery(this@TrackingService, percent, plugged)
                updateNotification(percent, plugged)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(null, false))
        try {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            receiverRegistered = true
        } catch (_: Exception) {
            receiverRegistered = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stop = intent?.action == ACTION_STOP
        if (stop) {
            ChargeData.setTrackingEnabled(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ChargeData.setTrackingEnabled(this, true)
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Charge tracking", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows when ChargeTrack is tracking battery charging." }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(percent: Int?, plugged: Boolean): Notification {
        val text = if (percent == null) "Tracking battery events" else
            "${percent}% • ${if (plugged) "Charging connected" else "Not charging"}"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("ChargeTrack is active")
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(percent: Int, plugged: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(percent, plugged))
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.ratulmaker.chargetrack.STOP_TRACKING"
        private const val CHANNEL_ID = "chargetrack_tracking"
        private const val NOTIFICATION_ID = 7401
    }
}
