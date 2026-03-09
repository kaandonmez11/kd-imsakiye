package com.kdgames.imsakiye.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class LiveCountdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        val serviceIntent = Intent(context, LiveNotificationService::class.java).apply {
            putExtra("title", intent.getStringExtra("title"))
            // Hata düzeltildi: targetMillis
            putExtra("targetMillis", intent.getLongExtra("targetMillis", 0L))
        }
        Log.d("TEST_LOG", "Receiver tetiklendi")
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}