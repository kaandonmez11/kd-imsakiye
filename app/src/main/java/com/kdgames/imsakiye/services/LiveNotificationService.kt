package com.kdgames.imsakiye.services

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kdgames.imsakiye.MainActivity
import com.kdgames.imsakiye.R

class LiveNotificationService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private lateinit var notificationManager: NotificationManager

    private val CHANNEL_ID = "live_countdown_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TEST_LOG", "Service basladi")
        val title = intent?.getStringExtra("title") ?: "Geri Sayım"
        val targetMillis = intent?.getLongExtra("targetMillis", 0L) ?: 0L

        val notification = createNotificationBuilder(title, "Hesaplanıyor...", 0, 0)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        if (targetMillis > 0) {
            val remaining = targetMillis - System.currentTimeMillis()
            if (remaining > 0) {
                startCountdown(title, remaining)
            } else {
                stopSelf()
            }
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationBuilder(title: String, contentText: String, max: Int, progress: Int): NotificationCompat.Builder {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.notification_logo)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)

        if (max > 0) {
            builder.setProgress(max, progress, false)
        }

        if (Build.VERSION.SDK_INT >= 35) {
            builder.setRequestPromotedOngoing(true)
            
            // Durum çubuğunda (chip) görünecek kısa metin
            val minutes = progress / 60
            val seconds = progress % 60
            val chipText = if (minutes > 0) "${minutes}dk" else "${seconds}sn"
            builder.setShortCriticalText(chipText)
        } else {
            // Eski sürümlerde veya kütüphane desteği yoksa extra olarak ekleyelim
            builder.addExtras(android.os.Bundle().apply {
                putBoolean("android.requestPromotedOngoing", true)
            })
        }

        return builder
    }

    private fun startCountdown(title: String, totalMillis: Long) {
        countDownTimer?.cancel()
        val maxSeconds = (totalMillis / 1000).toInt()

        countDownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()
                val minutes = secondsRemaining / 60
                val seconds = secondsRemaining % 60
                val timeText = String.format("%02d:%02d", minutes, seconds)

                val updatedNotification = createNotificationBuilder(
                    title, 
                    "Kalan süre: $timeText", 
                    maxSeconds, 
                    secondsRemaining
                )
                .setOnlyAlertOnce(true)
                .build()

                notificationManager.notify(NOTIFICATION_ID, updatedNotification)
            }

            override fun onFinish() {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Canlı Geri Sayım",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "İftar ve Sahur geri sayım bildirimi"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }
}