package com.kdgames.imsakiye.data

data class CalendarResponse(val data: List<DayData>)

data class DayData(
    val timings: Timings,
    val date: DateInfo
)

data class DateInfo(
    val readable: String,
    val gregorian: GregorianDate,
    val hijri: HijriDate
)

data class GregorianDate(
    val day: String,
    val month: MonthInfo,
    val weekday: WeekdayInfo
)

data class HijriDate(
    val day: String,
    val month: MonthInfo,
    val year: String,
    val weekday: WeekdayInfo
)

data class MonthInfo(val en: String, val number: Int)
data class WeekdayInfo(val en: String)

data class Timings(
    val Fajr: String,       //imsak
    val Sunrise: String,    //gun dogumu
    val Dhuhr: String,      // ogle
    val Asr: String,        // ikindi
    val Maghrib: String,    // aksam
    val Isha: String        // yatsi
)

data class AnnualResponse(
    val data: Map<String, List<DayData>> // "1", "2" ... "12" anahtarlarıyla ayları tutar
)