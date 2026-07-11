package com.kdgames.imsakiye

import com.kdgames.imsakiye.data.HijriDate
import com.kdgames.imsakiye.data.PrayTimeData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object PrayTimeUtils {

    fun parseDate(date: String): LocalDate =
        LocalDate.parse(date, DateTimeFormatter.ISO_DATE_TIME)

    fun timeToMinutes(time: String): Int {
        val parts = time.substring(0, 5).split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    fun checkTimeStatus(time: String, now: LocalTime = LocalTime.now()): Boolean {
        val nowMinutes = now.hour * 60 + now.minute
        return nowMinutes >= timeToMinutes(time)
    }

    /**
     * Dünden başlayarak en fazla [count] gün döner. Liste sonunda pencere
     * KAYDIRILMAZ, kısaltılır: bugün her zaman index 1'dedir (verinin ilk
     * günü hariç) — aksi halde yıl sonunda "Bugün" yanlış güne kayar.
     */
    fun getInterestDays(
        list: List<PrayTimeData>,
        count: Int,
        today: LocalDate = LocalDate.now()
    ): List<PrayTimeData> {
        if (list.isEmpty()) return emptyList()

        // Callers expect index 1 to be today; fall back to 1 when today is not in the data
        val todayIndex = list.indexOfFirst { parseDate(it.date) == today }
            .let { if (it == -1) 1 else it }

        val from = (todayIndex - 1).coerceAtLeast(0)
        val to = minOf(from + count, list.size)
        return list.subList(from, to)
    }

    fun formatGregDate(date: LocalDate): String =
        "${date.dayOfMonth} ${getTurkishGregMonth(date.monthValue)} ${date.year}"

    fun formatHijriDate(hijri: HijriDate): String =
        "${hijri.day} ${getTurkishHijriMonth(hijri.month)} ${hijri.year}"

    fun getTurkishGregMonth(monthNumber: Int): String {
        return when (monthNumber) {
            1 -> "Ocak"
            2 -> "Şubat"
            3 -> "Mart"
            4 -> "Nisan"
            5 -> "Mayıs"
            6 -> "Haziran"
            7 -> "Temmuz"
            8 -> "Ağustos"
            9 -> "Eylül"
            10 -> "Ekim"
            11 -> "Kasım"
            12 -> "Aralık"
            else -> ""
        }
    }

    fun getTurkishHijriMonth(monthNumber: Int): String {
        return when (monthNumber) {
            1 -> "Muharrem"
            2 -> "Safer"
            3 -> "Rebiülevvel"
            4 -> "Rebiülahir"
            5 -> "Cemaziyelevvel"
            6 -> "Cemaziyelahir"
            7 -> "Recep"
            8 -> "Şaban"
            9 -> "Ramazan"
            10 -> "Şevval"
            11 -> "Zilkade"
            12 -> "Zilhicce"
            else -> ""
        }
    }

    fun getTurkishWeekday(day: DayOfWeek): String {
        return when (day) {
            DayOfWeek.MONDAY -> "Pazartesi"
            DayOfWeek.TUESDAY -> "Salı"
            DayOfWeek.WEDNESDAY -> "Çarşamba"
            DayOfWeek.THURSDAY -> "Perşembe"
            DayOfWeek.FRIDAY -> "Cuma"
            DayOfWeek.SATURDAY -> "Cumartesi"
            DayOfWeek.SUNDAY -> "Pazar"
        }
    }

    fun getTurkishShortDay(day: DayOfWeek): String {
        return when (day) {
            DayOfWeek.MONDAY -> "Pzt"
            DayOfWeek.TUESDAY -> "Sal"
            DayOfWeek.WEDNESDAY -> "Çar"
            DayOfWeek.THURSDAY -> "Per"
            DayOfWeek.FRIDAY -> "Cum"
            DayOfWeek.SATURDAY -> "Cmt"
            DayOfWeek.SUNDAY -> "Paz"
        }
    }
}
