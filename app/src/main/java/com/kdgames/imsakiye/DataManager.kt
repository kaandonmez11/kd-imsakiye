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
        private val KEY_USE_LOCATION = booleanPreferencesKey("use_location")
        private val KEY_SET_CITY = stringPreferencesKey("set_city")
        private val KEY_REMINDER_HOURS = longPreferencesKey("reminder_hours")
        private val KEY_COUNTDOWN_MINUTES = longPreferencesKey("countdown_minutes")
        private val KEY_REMINDER_TOTAL_MIN = longPreferencesKey("reminder_total_minutes")
        private val KEY_COUNTDOWN_TOTAL_MIN = longPreferencesKey("countdown_total_minutes")
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

    suspend fun saveAnnualData(list: List<PrayTimeData>, cityName: String) {
        val jsonString = gson.toJson(list)
        context.dataStore.edit { prefs ->
            prefs[KEY_ANNUAL_DATA] = jsonString
            prefs[KEY_SAVED_CITY] = cityName
            prefs[KEY_DATA_VERSION] = getAppVersionCode()
        }
    }

    suspend fun getAnnualData(currentCity: String): List<PrayTimeData>? {
        return context.dataStore.data.map { prefs ->

            val savedVersion = prefs[KEY_DATA_VERSION] ?: 0
            if (savedVersion < getAppVersionCode()) return@map null

            val savedCity = prefs[KEY_SAVED_CITY] ?: ""
            if (currentCity.isEmpty() || currentCity != savedCity) return@map null

            val jsonString = prefs[KEY_ANNUAL_DATA] ?: return@map null
            if (jsonString.isEmpty()) return@map null

            try {
                val type = object : TypeToken<List<PrayTimeData>>() {}.type
                gson.fromJson<List<PrayTimeData>>(jsonString, type)
            } catch (_: Exception) {
                // bozuk cache init akışını kilitlemesin; null → yeniden indirilir
                null
            }
        }.first()
    }

    /**
     * Sürüm kontrolü YAPMADAN cache'teki yıllık veriyi okur.
     * Boot/güncelleme sonrası alarm kurulumu için: uygulama güncellenince
     * versionCode artar ve normal okuma cache'i geçersiz sayar; oysa vakit
     * verisi sürümden bağımsız geçerlidir.
     */
    suspend fun getAnnualDataIgnoringVersion(): List<PrayTimeData>? {
        return context.dataStore.data.map { prefs ->
            val jsonString = prefs[KEY_ANNUAL_DATA] ?: return@map null
            if (jsonString.isEmpty()) return@map null

            try {
                val type = object : TypeToken<List<PrayTimeData>>() {}.type
                gson.fromJson<List<PrayTimeData>>(jsonString, type)
            } catch (_: Exception) {
                // eski sürümden kalan uyumsuz format olabilir
                null
            }
        }.first()
    }

    suspend fun getLocationUsingData(): Pair<Boolean, String?> {
        return context.dataStore.data.map { prefs ->
            val useLocation = prefs[KEY_USE_LOCATION] ?: true
            val setCity = if (!useLocation) prefs[KEY_SET_CITY] ?: "İstanbul" else null
            Pair(useLocation, setCity)
        }.first()
    }

    suspend fun setLocationUsingData(value: Boolean, city: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_LOCATION] = value
            prefs[KEY_SET_CITY] = city
        }
    }

    suspend fun setReminderMinutes(value: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REMINDER_TOTAL_MIN] = value
        }
    }

    suspend fun getReminderMinutes(): Long {
        return context.dataStore.data.map { prefs ->
            // eski sürümden geçiş: saat cinsinden değer dakikaya çevrilir
            prefs[KEY_REMINDER_TOTAL_MIN] ?: ((prefs[KEY_REMINDER_HOURS] ?: 1L) * 60)
        }.first()
    }

    suspend fun setCountdownMinutes(value: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COUNTDOWN_TOTAL_MIN] = value
        }
    }

    suspend fun getCountdownMinutes(): Long {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_COUNTDOWN_TOTAL_MIN] ?: prefs[KEY_COUNTDOWN_MINUTES] ?: 5L
        }.first()
    }

    suspend fun saveAlarmCodes(codes: Collection<Int>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ALARM_CODES] = codes.joinToString(",")
        }
    }

    suspend fun getAlarmCodes(): Set<String> {
        return context.dataStore.data.map { prefs ->
            val existing = prefs[KEY_ALARM_CODES] ?: ""
            if (existing.isEmpty()) emptySet() else existing.split(",").toSet()
        }.first()
    }

    suspend fun clearAlarmCodes() {
        context.dataStore.edit { prefs ->
            prefs[KEY_ALARM_CODES] = ""
        }
    }
}
