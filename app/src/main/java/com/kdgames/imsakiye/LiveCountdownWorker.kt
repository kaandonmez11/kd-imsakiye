package com.kdgames.imsakiye

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.kdgames.imsakiye.services.LiveNotificationService

class LiveCountdownWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val title = inputData.getString("title") ?: "Geri Sayım"

        val serviceIntent = Intent(applicationContext, LiveNotificationService::class.java).apply {
            putExtra("title", title)
            putExtra("minutes", 5L) // Geri sayım süresi
        }

        // Android 8.0 (Oreo) ve üzeri için Foreground Service başlatma kuralı
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(serviceIntent)
        } else {
            applicationContext.startService(serviceIntent)
        }

        return Result.success()
    }
}