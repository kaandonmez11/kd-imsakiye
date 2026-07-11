package com.kdgames.imsakiye.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kdgames.imsakiye.DataManager
import com.kdgames.imsakiye.PrayTimeUtils
import com.kdgames.imsakiye.data.PrayTimeData
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Ramazan alarm kurulumu. Activity'den bağımsızdır ki cihaz yeniden
 * başladığında (BootReceiver) alarmlar yeniden kurulabilsin.
 */
object NotificationScheduler {

    /** Cache'teki veri ve kayıtlı ayarlarla alarmları kurar (boot/güncelleme sonrası) */
    suspend fun scheduleFromCache(context: Context) {
        val dataManager = DataManager.getInstance(context)

        // sürüm kontrolsüz okuma: güncelleme sonrası versionCode artışı
        // cache'i geçersiz saydırıp alarm kurulumunu boşa düşürmesin
        val data = dataManager.getAnnualDataIgnoringVersion() ?: return
        if (data.isEmpty()) return

        schedule(
            context,
            data,
            dataManager.getReminderMinutes(),
            dataManager.getCountdownMinutes()
        )
    }

    /**
     * NonCancellable: kurulum "hepsini sil → yeniden kur" yaptığı için yarıda
     * kesilirse (ör. activity destroy) kullanıcı SIFIR alarmla kalabilir;
     * bir kez başladıysa iptal edilse bile tamamlanır.
     */
    suspend fun schedule(
        context: Context,
        allYearData: List<PrayTimeData>,
        reminderMinutes: Long,
        countdownMinutes: Long
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        cancelAll(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()
        val requestCodes = mutableListOf<Int>()

        allYearData.forEach { day ->
            // Only Ramadan month (Hijri 9th month)
            if (day.hijri_date.month != 9) return@forEach

            val date = PrayTimeUtils.parseDate(day.date)
            val sahurDateTime = LocalDateTime.of(date, LocalTime.parse(day.times.imsak))
            val iftarDateTime = LocalDateTime.of(date, LocalTime.parse(day.times.aksam))

            // --- SAHUR SCHEDULING ---
            if (sahurDateTime.minusMinutes(reminderMinutes).isAfter(now)) {
                planTask(context, alarmManager, sahurDateTime.minusMinutes(reminderMinutes), "Sahur Vakti", "İmsak vaktine az kaldı.", "SAHUR_1H_${date.dayOfMonth}_${date.monthValue}".hashCode(), "faded_davul", requestCodes)
            }
            if (sahurDateTime.minusMinutes(countdownMinutes).isAfter(now)) {
                planLiveTask(context, alarmManager, sahurDateTime, "İmsak Geri Sayım", date.dayOfMonth * 100 + date.monthValue, countdownMinutes, requestCodes)
            }
            if (sahurDateTime.isAfter(now)) {
                planTask(context, alarmManager, sahurDateTime, "İmsak Attı", "Oruç başladı.", "SAHUR_FULL_${date.dayOfMonth}_${date.monthValue}".hashCode(), "ezan", requestCodes)
            }

            // --- IFTAR SCHEDULING ---
            if (iftarDateTime.minusMinutes(reminderMinutes).isAfter(now)) {
                planTask(context, alarmManager, iftarDateTime.minusMinutes(reminderMinutes), "İftar Vaktine Az Kaldı", "Hazırlıklar tamam mı?", "IFTAR_1H_${date.dayOfMonth}_${date.monthValue}".hashCode(), "", requestCodes)
            }
            if (iftarDateTime.minusMinutes(countdownMinutes).isAfter(now)) {
                planLiveTask(context, alarmManager, iftarDateTime, "İftar Geri Sayım", date.dayOfMonth * 10000 + date.monthValue, countdownMinutes, requestCodes)
            }
            if (iftarDateTime.isAfter(now)) {
                planTask(context, alarmManager, iftarDateTime, "İftar Vakti", "Afiyet olsun.", "IFTAR_FULL_${date.dayOfMonth}_${date.monthValue}".hashCode(), "ezan_with_top", requestCodes)
            }
        }

        DataManager.getInstance(context).saveAlarmCodes(requestCodes)
    }

    suspend fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val dataManager = DataManager.getInstance(context)
        val set = dataManager.getAlarmCodes()

        for (code in set) {
            val codeInt = code.toIntOrNull() ?: continue

            val intentLive = Intent(context, LiveCountdownReceiver::class.java)
            val piLive = PendingIntent.getBroadcast(context, codeInt, intentLive, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(piLive)

            val intentNotify = Intent(context, NotificationReceiver::class.java)
            val piNotify = PendingIntent.getBroadcast(context, codeInt, intentNotify, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(piNotify)
        }

        dataManager.clearAlarmCodes()
    }

    private fun planTask(
        context: Context,
        alarmManager: AlarmManager,
        trigger: LocalDateTime,
        title: String,
        message: String,
        requestCode: Int,
        soundResourceName: String,
        requestCodes: MutableList<Int>
    ) {
        val triggerMillis = toEpochMillis(trigger)
        if (triggerMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("sound", soundResourceName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarm(alarmManager, triggerMillis, pendingIntent)
        requestCodes.add(requestCode)
    }

    private fun planLiveTask(
        context: Context,
        alarmManager: AlarmManager,
        targetTime: LocalDateTime,
        title: String,
        requestCode: Int,
        minutesBefore: Long,
        requestCodes: MutableList<Int>
    ) {
        val triggerMillis = toEpochMillis(targetTime.minusMinutes(minutesBefore))
        if (triggerMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, LiveCountdownReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("targetMillis", toEpochMillis(targetTime))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarm(alarmManager, triggerMillis, pendingIntent)
        requestCodes.add(requestCode)
    }

    private fun setAlarm(alarmManager: AlarmManager, triggerMillis: Long, pendingIntent: PendingIntent) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    private fun toEpochMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
