package org.example.spotifydownloader

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class DownloadService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val FOREGROUND_ID = 1002
    
    override fun onCreate() {
        super.onCreate()
        
        // Create notification channel
        NotificationHelper.createNotificationChannel(this)
        
        // Acquire wake lock to keep CPU running
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SpotifyDownloader::DownloadWakeLock"
        )
        wakeLock?.acquire(30 * 60 * 1000L) // 30 minutes max
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Create foreground notification
        val notification = NotificationCompat.Builder(this, "spotify_downloader_channel")
            .setContentTitle("🎵 Spotify Downloader Active")
            .setContentText("Download service running in background")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setColor(0xFF00D4FF.toInt())
            .build()
        
        startForeground(FOREGROUND_ID, notification)
        
        UiBridge.log("🔄 Background service started - app will continue running when minimized\n")
        
        return START_STICKY // Restart if killed
    }
    
    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        UiBridge.log("🔄 Background service stopped\n")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    companion object {
        fun start(context: android.content.Context) {
            val intent = Intent(context, DownloadService::class.java)
            context.startForegroundService(intent)
        }
        
        fun stop(context: android.content.Context) {
            val intent = Intent(context, DownloadService::class.java)
            context.stopService(intent)
        }
    }
}