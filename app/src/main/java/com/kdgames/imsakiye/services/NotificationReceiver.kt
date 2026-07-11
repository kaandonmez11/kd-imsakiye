package com.kdgames.imsakiye.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import androidx.core.app.NotificationCompat
import com.kdgames.imsakiye.MainActivity
import androidx.core.net.toUri

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: ""
        val message = intent.getStringExtra("message") ?: ""
        val sound = intent.getStringExtra("sound") ?: ""

        showNotification(context, title, message, sound)
    }



    private fun showNotification(applicationContext: Context, title: String, message: String, soundName: String) {

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Boş ses adı geçersiz URI'li (sessiz) kanal yaratıyordu;
        // ses belirtilmemişse varsayılan bildirim sesli kanal kullanılır
        val hasCustomSound = soundName.isNotBlank()
        val channelId = if (hasCustomSound) "ramadan_channel_$soundName" else "ramadan_channel_default"

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channel = NotificationChannel(
            channelId,
            "Worship Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Prayer and time reminders"
            if (hasCustomSound) {
                val soundUri = "android.resource://${applicationContext.packageName}/raw/$soundName".toUri()
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
            }
        }

        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(com.kdgames.imsakiye.R.drawable.notification_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}