package com.kdgames.imsakiye.services

import com.kdgames.imsakiye.data.AnnualResponse
import com.kdgames.imsakiye.data.CalendarResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PrayerApiService {
    @GET("v1/calendarByCity")
    suspend fun getCalendarRange(
        @Query("city") city: String,
        @Query("country") country: String = "Turkey",
        @Query("method") method: Int = 13,
        @Query("start") start: String, // "dd-mm-yyyy"
        @Query("end") end: String      // "dd-mm-yyyy"
    ): CalendarResponse


        @GET("v1/calendarByCity/{year}")
        suspend fun getAnnualCalendar(
            @Path("year") year: Int,
            @Query("city") city: String,
            @Query("country") country: String = "Turkey",
            @Query("method") method: Int = 13
        ): AnnualResponse
}