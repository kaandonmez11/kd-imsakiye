package com.kdgames.imsakiye.data

data class PrayTimeResponse(
    val success: Boolean,
    val code: Int,
    val message: String,
    val data: List<PrayTimeData>
)

data class PrayTimeData(
    val date: String,
    val hijri_date: HijriDate_N,
    val times: Times
)

data class HijriDate_N(
    val day: Int,
    val month: Int,
    val year: Int
)

data class Times(
    val imsak: String,
    val gunes: String,
    val ogle: String,
    val ikindi: String,
    val aksam: String,
    val yatsi: String
)

data class CityById(
    val _id: String,
    val name: String
)