package com.kdgames.imsakiye.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager alarmları cihaz yeniden başlatıldığında ve uygulama
 * güncellendiğinde silinir; cache'teki veriyle yeniden kurulur.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                NotificationScheduler.scheduleFromCache(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
