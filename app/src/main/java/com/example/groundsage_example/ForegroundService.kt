package com.example.groundsage_example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.indooratlas.sdk.groundsage.IAGSManager
import com.indooratlas.sdk.groundsage.IAGSManagerListener
import com.indooratlas.sdk.groundsage.data.IAGSVenueDensity

class ForegroundService : Service(), IAGSManagerListener {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "com.example.groundsage_example.foreground_service"
        const val NOTIFICATION_ID = 1234
    }

    private val notification by lazy {
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle("GS example app keeps density and location update.").build()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("ForegroundService", "onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification)
        IAGSManager.getInstance(this).addGroundSageListener(this)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ForegroundService", "onStartCommand")
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("ForegroundService", "onTaskRemoved")
        this.stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun didReceiveDensity(venueDensity: IAGSVenueDensity) {
        Log.d("ForegroundService", "didReceiveDensity")
    }
}
