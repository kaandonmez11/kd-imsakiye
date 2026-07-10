package com.kdgames.imsakiye

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kdgames.imsakiye.data.CityById
import java.text.Collator
import java.util.Locale

class CityManager private constructor(private val context: Context) {
    private val gson = Gson()
    private var cityCache: List<CityById>? = null

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: CityManager? = null

        fun getInstance(context: Context): CityManager {
            return instance ?: synchronized(this) {
                instance ?: CityManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun loadCities(): List<CityById> {
        cityCache?.let { return it }

        val json = context.resources.openRawResource(R.raw.city_request_ids)
            .bufferedReader()
            .use { it.readText() }

        val type = object : TypeToken<List<CityById>>() {}.type
        return gson.fromJson<List<CityById>>(json, type).also { cityCache = it }
    }

    fun getCityId(city: String) : String? {
        return loadCities().find {
            it.name.equals(city, ignoreCase = true)
        }?.id
    }

    fun getCityNames(): List<String> {
        val collator = Collator.getInstance(Locale.forLanguageTag("tr-TR"))
        return loadCities().map { it.name }.sortedWith(collator)
    }
}