package com.kdgames.imsakiye

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kdgames.imsakiye.data.PrayTimeData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "KDImsakiyePrefs")

class DataManager private constructor(private val context: Context) {
    private val gson = Gson()

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: DataManager? = null

        fun getInstance(context: Context): DataManager {
            return instance ?: synchronized(this) {
                instance ?: DataManager(context.applicationContext).also { instance = it }
            }
        }


        private val KEY_ANNUAL_DATA = stringPreferencesKey("annual_data")
        private val KEY_SAVED_CITY = stringPreferencesKey("saved_city")
        private val KEY_DATA_YEAR = intPreferencesKey("data_year")
        private val KEY_USE_LOCATION = booleanPreferencesKey("use_location")
        private val KEY_SET_CITY = stringPreferencesKey("set_city")
        private val KEY_REMINDER_HOURS = longPreferencesKey("reminder_hours")
        private val KEY_COUNTDOWN_MINUTES = longPreferencesKey("countdown_minutes")
        private val KEY_ALARM_CODES = stringPreferencesKey("alarm_codes")
        private val KEY_DATA_VERSION = intPreferencesKey("data_version")
    }

    private fun getAppVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.longVersionCode.toInt()
        } catch (_: Exception) {
            1
        }
    }

    fun saveAnnualData(list: List<PrayTimeData>, cityName: String) {
        val jsonString = gson.toJson(list)
        runBlocking {
            context.dataStore.edit { prefs ->
                prefs[KEY_ANNUAL_DATA] = jsonString
                prefs[KEY_SAVED_CITY] = cityName
                prefs[KEY_DATA_YEAR] = 2026
                prefs[KEY_DATA_VERSION] = getAppVersionCode()
            }
        }
    }

    fun getAnnualData(currentCity: String): List<PrayTimeData>? {
        return runBlocking {
            context.dataStore.data.map { prefs ->

                val savedVersion = prefs[KEY_DATA_VERSION] ?: 0
                if (savedVersion < getAppVersionCode()) return@map null

                val savedCity = prefs[KEY_SAVED_CITY] ?: ""
                if (currentCity.isEmpty() || currentCity != savedCity) return@map null

                val jsonString = prefs[KEY_ANNUAL_DATA] ?: return@map null
                if (jsonString.isEmpty()) return@map null

                val type = object : TypeToken<List<PrayTimeData>>() {}.type
                gson.fromJson<List<PrayTimeData>>(jsonString, type)
            }.first()
        }
    }

    fun getLocationUsingData(): Pair<Boolean, String?> {
        return runBlocking {
            context.dataStore.data.map { prefs ->
                val useLocation = prefs[KEY_USE_LOCATION] ?: true
                val setCity = if (!useLocation) prefs[KEY_SET_CITY] ?: "İstanbul" else null
                Pair(useLocation, setCity)
            }.first()
        }
    }

    fun setLocationUsingData(value: Boolean, city: String) {
        runBlocking {
            context.dataStore.edit { prefs ->
                prefs[KEY_USE_LOCATION] = value
                prefs[KEY_SET_CITY] = city
            }
        }
    }

    fun setReminderHours(value: Long) {
        runBlocking {
            context.dataStore.edit { prefs ->
                prefs[KEY_REMINDER_HOURS] = value
            }
        }
    }

    fun getReminderHours(): Long {
        return runBlocking {
            context.dataStore.data.map { prefs ->
                prefs[KEY_REMINDER_HOURS] ?: 1L
            }.first()
        }
    }

    fun setCountdownMinutes(value: Long) {
        runBlocking {
            context.dataStore.edit { prefs ->
                prefs[KEY_COUNTDOWN_MINUTES] = value
            }
        }
    }

    fun getCountdownMinutes(): Long {
        return runBlocking {
            context.dataStore.data.map { prefs ->
                prefs[KEY_COUNTDOWN_MINUTES] ?: 5L
            }.first()
        }
    }

    fun saveRequestCode(requestCode: Int) {
        runBlocking {
            context.dataStore.edit { prefs ->
                val existing = prefs[KEY_ALARM_CODES] ?: ""
                val codes = if (existing.isEmpty()) mutableSetOf() else existing.split(",").toMutableSet()
                codes.add(requestCode.toString())
                prefs[KEY_ALARM_CODES] = codes.joinToString(",")
            }
        }
    }

    fun getAlarmCodes(): Set<String> {
        return runBlocking {
            context.dataStore.data.map { prefs ->
                val existing = prefs[KEY_ALARM_CODES] ?: ""
                if (existing.isEmpty()) emptySet() else existing.split(",").toSet()
            }.first()
        }
    }

    fun clearAlarmCodes() {
        runBlocking {
            context.dataStore.edit { prefs ->
                prefs[KEY_ALARM_CODES] = ""
            }
        }
    }
}