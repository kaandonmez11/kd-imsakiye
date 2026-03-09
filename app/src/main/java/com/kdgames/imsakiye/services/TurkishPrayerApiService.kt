package com.kdgames.imsakiye.services

import com.kdgames.imsakiye.data.PrayTimeResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface TurkishPrayerApiService {

    @GET("prayer-times/{cityId}/yearly")
    suspend fun getAnnualCalendar(
        @Path("cityId") cityId: String
    ): PrayTimeResponse
}
