package com.kdgames.imsakiye

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.*
import android.os.CountDownTimer
import android.transition.Fade
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputLayout
import com.kdgames.imsakiye.data.PrayTimeData
import com.kdgames.imsakiye.services.LiveCountdownReceiver
import com.kdgames.imsakiye.services.NotificationReceiver
import com.kdgames.imsakiye.services.TurkishPrayerApiService
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.core.net.toUri

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    lateinit var cachedDataProp: List<PrayTimeData>
    lateinit var city: String

    var useLocation: Boolean = true

    var reminderHours: Long = 1L
    var countdownMinutes: Long = 5L

    val cities = listOf(
        "Adana",
        "Adıyaman",
        "Afyonkarahisar",
        "Ağrı",
        "Aksaray",
        "Amasya",
        "Ankara",
        "Antalya",
        "Ardahan",
        "Artvin",
        "Aydın",
        "Balıkesir",
        "Bartın",
        "Batman",
        "Bayburt",
        "Bilecik",
        "Bingöl",
        "Bitlis",
        "Bolu",
        "Burdur",
        "Bursa",
        "Çanakkale",
        "Çankırı",
        "Çorum",
        "Denizli",
        "Diyarbakır",
        "Düzce",
        "Edirne",
        "Elazığ",
        "Erzincan",
        "Erzurum",
        "Eskişehir",
        "Gaziantep",
        "Giresun",
        "Gümüşhane",
        "Hakkari",
        "Hatay",
        "Iğdır",
        "Isparta",
        "İstanbul",
        "İzmir",
        "Kahramanmaraş",
        "Karabük",
        "Karaman",
        "Kars",
        "Kastamonu",
        "Kayseri",
        "Kırıkkale",
        "Kırklareli",
        "Kırşehir",
        "Kilis",
        "Kocaeli",
        "Konya",
        "Kütahya",
        "Malatya",
        "Manisa",
        "Mardin",
        "Mersin",
        "Muğla",
        "Muş",
        "Nevşehir",
        "Niğde",
        "Ordu",
        "Osmaniye",
        "Rize",
        "Sakarya",
        "Samsun",
        "Siirt",
        "Sinop",
        "Sivas",
        "Şanlıurfa",
        "Şırnak",
        "Tekirdağ",
        "Tokat",
        "Trabzon",
        "Tunceli",
        "Uşak",
        "Van",
        "Yalova",
        "Yozgat",
        "Zonguldak"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_root)

        val root = findViewById<View>(R.id.mainRoot)
        root.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                v.performClick()
            }

            currentFocus?.clearFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)

            false
        }


        currentPanel = findViewById(R.id.loadingPage)

        setSplashScreen(splashScreen)

        lifecycleScope.launch {

            if (!hasLocationPermission()) {
                requestLocationPermission()
            }

            val dataManager = DataManager.getInstance(this@MainActivity)

            val (dmUseLocation, dmCity) = dataManager.getLocationUsingData()

            useLocation = dmUseLocation

            city = if(useLocation) {
                getCity()
            } else {
                dmCity ?: ""
            }

            if (city.isEmpty()) {
                city = "İstanbul"
            }

            reminderHours = dataManager.getReminderHours()
            countdownMinutes = dataManager.getCountdownMinutes()


            var cachedData = dataManager.getAnnualData(city)


            if(cachedData == null)
            {
                cachedData = fetchFullYearTimings(city)

                if(cachedData.isNotEmpty()){
                    dataManager.saveAnnualData(cachedData, city)
                }
            }
            else
            {
                val year = Calendar.YEAR

                val currentDayDate = LocalDate.parse(cachedData[0].date, DateTimeFormatter.ISO_DATE_TIME)
                if(year > currentDayDate.year)
                {
                    cachedData = fetchFullYearTimings(city)

                    if(cachedData.isNotEmpty()){
                        dataManager.saveAnnualData(cachedData, city)
                    }
                }
            }
            cachedDataProp = cachedData

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }

            scheduleRamadanNotifications(this@MainActivity, cachedDataProp)

            checkDataAndOpenPanel()
        }
    }

    private fun planLiveTask(
        context: Context,
        date: LocalDate,
        timeStr: String,
        title: String,
        requestCode: Int,
        minutesBefore: Long
    ) {

        val prayerTime = LocalTime.parse(timeStr)

        val triggerDateTime = LocalDateTime.of(date, prayerTime)
            .minusMinutes(minutesBefore)

        val triggerMillis = triggerDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (triggerMillis <= System.currentTimeMillis()) {
            Log.d("TEST_LOG", "trigger millis passed")
            return
        }

        val alarmManager =
            context.getSystemService(ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, LiveCountdownReceiver::class.java).apply {
            putExtra("title", title)
        }
        intent.putExtra("targetMillis", triggerDateTime
            .plusMinutes(minutesBefore)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli())

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {

            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                val permissionIntent = Intent(
                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                )
                permissionIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(permissionIntent)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        saveRequestCode(this@MainActivity, requestCode)
    }

    private fun saveRequestCode(context: Context, requestCode: Int) {
        DataManager.getInstance(context).saveRequestCode(requestCode)
    }

    private fun cancelAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
        val dataManager = DataManager.getInstance(context)
        val set = dataManager.getAlarmCodes()

        for (code in set) {
            val codeInt = code.toInt()

            val intentLive = Intent(context, LiveCountdownReceiver::class.java)
            val piLive = PendingIntent.getBroadcast(context, codeInt, intentLive, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(piLive)

            val intentNotify = Intent(context, NotificationReceiver::class.java)
            val piNotify = PendingIntent.getBroadcast(context, codeInt, intentNotify, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(piNotify)
        }

        dataManager.clearAlarmCodes()
    }

    fun checkDataAndOpenPanel()
    {
        val days = getInterestDays(cachedDataProp, 8 )
        val today = days[1]

        if(checkTimeStatus(today.times.imsak))
        {
            if(checkTimeStatus(today.times.aksam))
            {
                // LoadNightMode
                loadNightMode(days, days[2].times.imsak, city)
            }
            else
            {
                // LoadDayMode
                loadDayMode(days, today.times.aksam, city)
            }
        }
        else
        {
            // LoadNightMode
            loadNightMode(days, today.times.imsak, city)
        }
    }

    fun scheduleRamadanNotifications(context: Context, allYearData: List<PrayTimeData>) {
        cancelAllAlarms(this@MainActivity)

        val now = LocalDateTime.now()

        allYearData.forEach { day ->
            val currentDayDate = LocalDate.parse(day.date, DateTimeFormatter.ISO_DATE_TIME)

            // Only Ramadan month (Hijri 9th month)
            if (day.hijri_date.month == 9) {

                val sahurTime = LocalTime.parse(day.times.imsak)
                val sahurDateTime = LocalDateTime.of(currentDayDate, sahurTime)

                val iftarTime = LocalTime.parse(day.times.aksam)
                val iftarDateTime = LocalDateTime.of(currentDayDate, iftarTime)

                // --- SAHUR SCHEDULING ---
                // 1 Hour Before (Normal Notification)
                if (sahurDateTime.minusHours(reminderHours).isAfter(now)) {
                    planTask(context, currentDayDate, sahurTime.toString(), "Sahur Vakti", "İmsak vaktine az kaldı.", "SAHUR_1H_${currentDayDate.dayOfMonth}_${currentDayDate.monthValue}", reminderHours, "faded_davul")
                }
                // 5 Minutes Before (Live Countdown)
                if (sahurDateTime.minusMinutes(countdownMinutes).isAfter(now)) {
                    planLiveTask(context, currentDayDate, sahurTime.toString(), "İmsak Geri Sayım", currentDayDate.dayOfMonth * 100 + currentDayDate.monthValue, countdownMinutes)
                }
                // At Exact Time (Normal Notification)
                if (sahurDateTime.isAfter(now)) {
                    planTask(context, currentDayDate, sahurTime.toString(), "İmsak Attı", "Oruç başladı.", "SAHUR_FULL_${currentDayDate.dayOfMonth}_${currentDayDate.monthValue}", 0, "ezan")
                }

                // --- IFTAR SCHEDULING ---
                // 1 Hour Before (Normal Notification)
                if (iftarDateTime.minusHours(reminderHours).isAfter(now)) {
                    planTask(context, currentDayDate, iftarTime.toString(), "İftar Vaktine Az Kaldı", "Hazırlıklar tamam mı?", "IFTAR_1H_${currentDayDate.dayOfMonth}_${currentDayDate.monthValue}", reminderHours, "")
                }
                // 5 Minutes Before (Live Countdown)
                if (iftarDateTime.minusMinutes(countdownMinutes).isAfter(now)) {
                    planLiveTask(context, currentDayDate, iftarTime.toString(), "İftar Geri Sayım", currentDayDate.dayOfMonth * 10000 + currentDayDate.monthValue, countdownMinutes)
                }
                // At Exact Time (Normal Notification)
                if (iftarDateTime.isAfter(now)) {
                    planTask(context, currentDayDate, iftarTime.toString(), "İftar Vakti", "Afiyet olsun.", "IFTAR_FULL_${currentDayDate.dayOfMonth}_${currentDayDate.monthValue}", 0, "ezan_with_top")
                }
            }
        }
    }

    private fun planTask(context: Context, date: LocalDate, timeStr: String, title: String, message: String, uniqueTag: String, hoursBefore: Long, soundResourceName: String) {
        val prayerTime = LocalTime.parse(timeStr)
        val triggerDateTime = LocalDateTime.of(date, prayerTime).minusHours(hoursBefore)

        val triggerMillis = triggerDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (triggerMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("sound", soundResourceName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            uniqueTag.hashCode(), // Unique ID
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }

        saveRequestCode(context, uniqueTag.hashCode())
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCity(): String {
        return withTimeoutOrNull(15000L) { // 15.000 milliseconds = 15 seconds
            try {
                if (!hasLocationPermission()) return@withTimeoutOrNull ""

                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)
                val location = fusedLocationClient.lastLocation.await()

                if (location != null) {
                    val geocoder = Geocoder(this@MainActivity, Locale("tr"))
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    addresses?.get(0)?.adminArea
                } else {
                    ""
                }
            } catch (e: Exception) {
                Log.e("KD Imsakiye", "Error while fetching city!  :  " + e.message)
                ""
            }
        } ?: "" // If 15s timeout reached, returns null, converted to empty string
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 100)
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }

    fun setSplashScreen(splashScreen: SplashScreen){
        splashScreen.setOnExitAnimationListener { splashScreenView ->

            try {
                splashScreenView.iconView.let { icon ->
                    val scaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 2f)
                    val scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 2f)
                    scaleX.duration = 600L
                    scaleY.duration = 600L
                    scaleX.start()
                    scaleY.start()
                }
            } catch (_: NullPointerException) {
            }

            val alpha = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f)
            alpha.duration = 600L
            alpha.interpolator = AnticipateInterpolator()

            alpha.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    splashScreenView.remove()
                }
            })

            alpha.start()
        }
    }

    private var countdownTimer: CountDownTimer? = null

    fun startSmartCountdown(targetTime: String, onTickUpdate: (String) -> Unit) {
        countdownTimer?.cancel()

        val now = Calendar.getInstance()

        val timeParts = targetTime.split(" ")[0].split(":")
        val targetCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
            set(Calendar.MINUTE, timeParts[1].toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If target time is before now, automatically set for tomorrow
        if (targetCalendar.before(now)) {
            targetCalendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val remainingMillis = targetCalendar.timeInMillis - now.timeInMillis

        // Start countdown
        countdownTimer = object : CountDownTimer(remainingMillis, 1000)
        {
            @SuppressLint("DefaultLocale")
            override fun onTick(millisUntilFinished: Long) {
                val h = (millisUntilFinished / (1000 * 60 * 60))
                val m = (millisUntilFinished / (1000 * 60)) % 60
                val s = (millisUntilFinished / 1000) % 60

                val formattedTime = String.format("%02d:%02d:%02d", h, m, s)
                onTickUpdate(formattedTime) // UI updating callback
            }

            override fun onFinish() {
                checkDataAndOpenPanel()
            }
        }.start()
    }


    @SuppressLint("SetTextI18n")
    fun loadNightMode(dayList: List<PrayTimeData>, countdownTime: String, city: String)
    {
        val panel = findViewById<View>(R.id.nightImsakiyePage)
        showPanel(panel, 1000)

        findViewById<TextView>(R.id.city_name_text_night).text = city


        if(!useLocation || !hasLocationPermission()){
            findViewById<View>(R.id.location_set_icon_night).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.dark))
        }
        else{
            findViewById<View>(R.id.location_set_icon_night).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
        }

        for ((index, day) in dayList.withIndex())
        {
            val currentDayDate = LocalDate.parse(day.date, DateTimeFormatter.ISO_DATE_TIME)

            when (index) {
                0 -> {
                    findViewById<TextView>(R.id.dun_day_index_night).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.dun_iftar_vakti_night).text = day.times.aksam
                    findViewById<TextView>(R.id.dun_sahur_vakti_night).text = day.times.imsak
                }
                1 -> {
                    findViewById<TextView>(R.id.regular_date_text_night).text = currentDayDate.dayOfMonth.toString() + " " + getTurkishGregMonth(currentDayDate.month.value) + " " + currentDayDate.year.toString()
                    findViewById<TextView>(R.id.ramazan_date_text_night).text = day.hijri_date.day.toString() + " " + getTurkishHijriMonth(day.hijri_date.month) + " " + day.hijri_date.year.toString()
                    findViewById<TextView>(R.id.day_text_night).text = getTurkishWeekday(currentDayDate.dayOfWeek)
                    // list items
                    findViewById<TextView>(R.id.bugun_day_index_night).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.bugun_iftar_vakti_night).text = day.times.aksam
                    findViewById<TextView>(R.id.bugun_sahur_vakti_night).text = day.times.imsak
                }
                2 -> {
                    findViewById<TextView>(R.id.yarin_day_index_night).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.yarin_iftar_vakti_night).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_sahur_vakti_night).text = day.times.imsak
                }
                3 -> {
                    findViewById<TextView>(R.id.yarin_1_header_night).text = getTurkishShortDay(getTurkishWeekday(currentDayDate.dayOfWeek))
                    findViewById<TextView>(R.id.yarin_1_day_index_night).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.yarin_1_iftar_vakti_night).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_1_sahur_vakti_night).text = day.times.imsak
                }
                4 -> {
                    findViewById<TextView>(R.id.yarin_2_header_night).text = getTurkishShortDay(getTurkishWeekday(currentDayDate.dayOfWeek))
                    findViewById<TextView>(R.id.yarin_2_day_index_night).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.yarin_2_iftar_vakti_night).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_2_sahur_vakti_night).text = day.times.imsak
                }
                5 -> {
                    findViewById<TextView>(R.id.yarin_3_header_night).text = getTurkishShortDay(getTurkishWeekday(currentDayDate.dayOfWeek))
                    findViewById<TextView>(R.id.yarin_3_day_index_night).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.yarin_3_iftar_vakti_night).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_3_sahur_vakti_night).text = day.times.imsak
                }
                6 -> {
                    findViewById<TextView>(R.id.yarin_4_header_night).text = getTurkishShortDay(getTurkishWeekday(currentDayDate.dayOfWeek))
                    findViewById<TextView>(R.id.yarin_4_day_index_night).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.yarin_4_iftar_vakti_night).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_4_sahur_vakti_night).text = day.times.imsak
                }
                7 -> {
                    findViewById<TextView>(R.id.yarin_5_header_night).text = getTurkishShortDay(getTurkishWeekday(currentDayDate.dayOfWeek))
                    findViewById<TextView>(R.id.yarin_5_day_index_night).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.yarin_5_iftar_vakti_night).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_5_sahur_vakti_night).text = day.times.imsak
                }
            }
        }

        startSmartCountdown(countdownTime){
                timeString ->
            findViewById<TextView>(R.id.remaining_time_text_night).text = timeString
        }


        findViewById<Button>(R.id.prayer_times_button_night).setOnClickListener { loadNightPrayerTimes(dayList, city)}

        findViewById<Button>(R.id.settings_button_night).setOnClickListener { loadSettingsNight()}
    }

    @SuppressLint("SetTextI18n")
    fun loadDayMode(dayList: List<PrayTimeData>, countdownTime: String, city: String)
    {
        val panel = findViewById<View>(R.id.dayImsakiyePage)
        showPanel(panel, 1000)

        findViewById<TextView>(R.id.city_name_text_day).text = city

        if(!useLocation || !hasLocationPermission()){
            findViewById<View>(R.id.location_set_icon_day).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.light))

        }
        else{
            findViewById<View>(R.id.location_set_icon_day).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.secondary))
        }

        for ((index, day) in dayList.withIndex())
        {
            val currentDayDate = LocalDate.parse(day.date, DateTimeFormatter.ISO_DATE_TIME)
            when (index) {
                0 -> {
                    findViewById<TextView>(R.id.dun_day_index_day).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.dun_iftar_vakti_day).text = day.times.aksam
                    findViewById<TextView>(R.id.dun_sahur_vakti_day).text = day.times.imsak
                }
                1 -> {
                    findViewById<TextView>(R.id.regular_date_text_day).text = currentDayDate.dayOfMonth.toString() + " " + getTurkishGregMonth(currentDayDate.monthValue) + " " + currentDayDate.year.toString()
                    findViewById<TextView>(R.id.ramazan_date_text_day).text = day.hijri_date.day.toString() + " " + getTurkishHijriMonth(day.hijri_date.month) + " " + day.hijri_date.year.toString()
                    findViewById<TextView>(R.id.day_text_day).text = getTurkishWeekday(currentDayDate.dayOfWeek)
                    // list items
                    findViewById<TextView>(R.id.bugun_day_index_day).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.bugun_iftar_vakti_day).text = day.times.aksam
                    findViewById<TextView>(R.id.bugun_sahur_vakti_day).text = day.times.imsak
                }
                2 -> {
                    findViewById<TextView>(R.id.yarin_day_index_day).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.yarin_iftar_vakti_day).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_sahur_vakti_day).text = day.times.imsak
                }
                3 -> {
                    findViewById<TextView>(R.id.yarin_1_header_day).text = getTurkishShortDay(getTurkishWeekday(currentDayDate.dayOfWeek))
                    findViewById<TextView>(R.id.yarin_1_day_index_day).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.yarin_1_iftar_vakti_day).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_1_sahur_vakti_day).text = day.times.imsak
                }
                4 -> {
                    findViewById<TextView>(R.id.yarin_2_header_day).text = getTurkishShortDay(getTurkishWeekday(currentDayDate.dayOfWeek))
                    findViewById<TextView>(R.id.yarin_2_day_index_day).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.yarin_2_iftar_vakti_day).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_2_sahur_vakti_day).text = day.times.imsak
                }
                5 -> {
                    findViewById<TextView>(R.id.yarin_3_header_day).text = getTurkishShortDay(getTurkishWeekday(currentDayDate.dayOfWeek))
                    findViewById<TextView>(R.id.yarin_3_day_index_day).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.yarin_3_iftar_vakti_day).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_3_sahur_vakti_day).text = day.times.imsak
                }
                6 -> {
                    findViewById<TextView>(R.id.yarin_4_header_day).text = getTurkishShortDay(getTurkishWeekday(currentDayDate.dayOfWeek))
                    findViewById<TextView>(R.id.yarin_4_day_index_day).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.yarin_4_iftar_vakti_day).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_4_sahur_vakti_day).text = day.times.imsak
                }
                7 -> {
                    findViewById<TextView>(R.id.yarin_5_header_day).text = getTurkishShortDay(getTurkishWeekday(currentDayDate.dayOfWeek))
                    findViewById<TextView>(R.id.yarin_5_day_index_day).text = day.hijri_date.day.toString()
                    findViewById<TextView>(R.id.yarin_5_iftar_vakti_day).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_5_sahur_vakti_day).text = day.times.imsak
                }
            }
        }

        startSmartCountdown(countdownTime){
                timeString ->
            findViewById<TextView>(R.id.remaining_time_text_day).text = timeString
        }


        findViewById<Button>(R.id.prayer_times_button_day).setOnClickListener { loadDayPrayerTimes(dayList, city)}

        findViewById<Button>(R.id.settings_button_day).setOnClickListener { loadSettingsDay()}
    }


    fun loadSettingsNight(){

        val panel = findViewById<View>(R.id.nightSettingsPage)
        showPanel(panel, 1000)

        val spinner = findViewById<Spinner>(R.id.n_city_dropdown)

        val adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, cities) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).setTextColor(resources.getColor(R.color.secondary, theme))
                return view
            }
        }
        adapter.setDropDownViewResource(R.layout.night_spinner_dropdown_item)

        val position = adapter.getPosition(city)
        spinner.adapter = adapter
        spinner.setSelection(position, false)

        val dataManager = DataManager.getInstance(this@MainActivity)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val mCity = parent.getItemAtPosition(position).toString()
                if(mCity != city)
                {
                    dataManager.setLocationUsingData(false, mCity)

                    findViewById<View>(R.id.s_location_set_icon_night).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.dark))
                    findViewById<TextView>(R.id.s_city_name_text_night).text = mCity

                    lifecycleScope.launch {

                        city = mCity

                        var cachedData = dataManager.getAnnualData(city)

                        if(cachedData == null)
                        {
                            cachedData = fetchFullYearTimings(city)

                            if(cachedData.isNotEmpty()){
                                dataManager.saveAnnualData(cachedData, city)
                            }
                        }

                        cachedDataProp = cachedData

                        useLocation = false
                        val button = findViewById<Button>(R.id.s_use_location_button_n)
                        button.isEnabled = true
                        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.primary))

                        Toast.makeText(this@MainActivity, "Şehir değişikliği uygulandı.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val button = findViewById<Button>(R.id.s_use_location_button_n)
        button.isEnabled = !useLocation
        if(useLocation)
        {
            button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.disabled_button_n))
        }

        button.setOnClickListener {
            lifecycleScope.launch {
                useLocation = true
                setLocationUseToSettings()
                button.isEnabled = false
                button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.disabled_button_n))

                findViewById<View>(R.id.s_location_set_icon_night).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.primary))
                findViewById<TextView>(R.id.s_city_name_text_night).text = city


                val position = adapter.getPosition(city)
                spinner.setSelection(position, false)

                lifecycleScope.launch {

                    var cachedData = dataManager.getAnnualData(city)

                    if(cachedData == null)
                    {
                        cachedData = fetchFullYearTimings(city)

                        if(cachedData.isNotEmpty()){
                            dataManager.saveAnnualData(cachedData, city)
                        }
                    }
                    cachedDataProp = cachedData
                    Toast.makeText(this@MainActivity, "Şehir değişikliği uygulandı.", Toast.LENGTH_SHORT).show()
                }
            }
        }


        if(!useLocation || !hasLocationPermission()){
            findViewById<View>(R.id.s_location_set_icon_night).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.dark))
        }
        else{
            findViewById<View>(R.id.location_set_icon_night).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
        }

        findViewById<TextView>(R.id.s_city_name_text_night).text = city

        val editTextRem = findViewById<TextInputLayout>(R.id.n_reminder_text_input_layout).editText
        editTextRem?.setText(reminderHours.toString(), TextView.BufferType.EDITABLE)

        editTextRem?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = editTextRem.text.toString()
                var hours = value.toLongOrNull() ?: 1L

                if(hours > 23L){
                    hours = 23L
                }
                if(hours < 1L){
                    hours = 1L
                }

                if(editTextRem.text.toString() != hours.toString()){

                    editTextRem.setText(hours.toString(), TextView.BufferType.EDITABLE)
                }

                if(hours != reminderHours){
                    dataManager.setReminderHours(hours)
                    reminderHours = hours
                    scheduleRamadanNotifications(this@MainActivity, cachedDataProp)
                    Toast.makeText(this@MainActivity, "Bildirimler yeni hatırlatma süresine göre ayarlandı.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val editTextCd = findViewById<TextInputLayout>(R.id.n_countdown_input).editText
        editTextCd?.setText(countdownMinutes.toString(), TextView.BufferType.EDITABLE)

        editTextCd?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = editTextCd.text.toString()
                var mins = value.toLongOrNull() ?: 1L

                if(mins > 58L)
                {
                    mins = 58L
                }
                if(mins < 1L){
                    mins = 1L
                }

                if(editTextCd.text.toString() != mins.toString()){

                    editTextCd.setText(mins.toString(), TextView.BufferType.EDITABLE)
                }

                if(mins != countdownMinutes){
                    dataManager.setCountdownMinutes(mins)
                    countdownMinutes = mins
                    scheduleRamadanNotifications(this@MainActivity, cachedDataProp)
                    Toast.makeText(this@MainActivity, "Bildirimler yeni geri sayım süresine göre ayarlandı.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.s_imsakiye_button_n).setOnClickListener { checkDataAndOpenPanel()}

    }


    fun loadSettingsDay(){

        val panel = findViewById<View>(R.id.daySettingsPage)
        showPanel(panel, 1000)

        val spinner = findViewById<Spinner>(R.id.d_city_dropdown)

        val adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, cities) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).setTextColor(resources.getColor(R.color.secondary, theme))
                return view
            }
        }
        adapter.setDropDownViewResource(R.layout.day_spinner_dropdown_item)

        val position = adapter.getPosition(city)
        spinner.adapter = adapter
        spinner.setSelection(position, false)

        val dataManager = DataManager.getInstance(this@MainActivity)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val mCity = parent.getItemAtPosition(position).toString()

                if(mCity != city)
                {
                    dataManager.setLocationUsingData(false, mCity)

                    findViewById<View>(R.id.s_location_set_icon_day).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.light))
                    findViewById<TextView>(R.id.s_city_name_text_day).text = mCity

                    lifecycleScope.launch {

                        city = mCity

                        var cachedData = dataManager.getAnnualData(city)

                        if(cachedData == null)
                        {
                            cachedData = fetchFullYearTimings(city)

                            if(cachedData.isNotEmpty()){
                                dataManager.saveAnnualData(cachedData, city)
                            }
                        }

                        cachedDataProp = cachedData

                        useLocation = false
                        val button = findViewById<Button>(R.id.s_use_location_button_d)
                        button.isEnabled = true
                        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.secondary))

                        Toast.makeText(this@MainActivity, "Şehir değişikliği uygulandı.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val button = findViewById<Button>(R.id.s_use_location_button_d)
        button.isEnabled = !useLocation
        if(useLocation){
            button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.disabled_button_d))
        }

        button.setOnClickListener {
            lifecycleScope.launch {
                setLocationUseToSettings()
                button.isEnabled = false
                button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.secondary))

                findViewById<View>(R.id.s_location_set_icon_day).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.primary))
                findViewById<TextView>(R.id.s_city_name_text_day).text = city


                val position = adapter.getPosition(city)
                spinner.setSelection(position, false)

                lifecycleScope.launch {

                    var cachedData = dataManager.getAnnualData(city)
                    
                    if(cachedData == null)
                    {
                        cachedData = fetchFullYearTimings(city)

                        if(cachedData.isNotEmpty()){
                            dataManager.saveAnnualData(cachedData, city)
                        }
                    }
                    cachedDataProp = cachedData
                    Toast.makeText(this@MainActivity, "Şehir değişikliği uygulandı.", Toast.LENGTH_SHORT).show()
                }
            }
        }


        if(!useLocation || !hasLocationPermission()){
            findViewById<View>(R.id.s_location_set_icon_day).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.light))
        }
        else{
            findViewById<View>(R.id.s_location_set_icon_day).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.secondary))
        }
        findViewById<TextView>(R.id.s_city_name_text_day).text = city

        val editTextRem = findViewById<TextInputLayout>(R.id.d_reminder_text_input_layout).editText
        editTextRem?.setText(reminderHours.toString(), TextView.BufferType.EDITABLE)

        editTextRem?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = editTextRem.text.toString()
                var hours = value.toLongOrNull() ?: 1L

                if(hours > 23L){
                    hours = 23L
                }
                if(hours < 1L){
                    hours = 1L
                }

                if(editTextRem.text.toString() != hours.toString()){

                    editTextRem.setText(hours.toString(), TextView.BufferType.EDITABLE)
                }

                if(hours != reminderHours){
                    dataManager.setReminderHours(hours)
                    reminderHours = hours
                    scheduleRamadanNotifications(this@MainActivity, cachedDataProp)
                    Toast.makeText(this@MainActivity, "Bildirimler yeni hatırlatma süresine göre ayarlandı.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val editTextCd = findViewById<TextInputLayout>(R.id.d_countdown_input).editText
        editTextCd?.setText(countdownMinutes.toString(), TextView.BufferType.EDITABLE)

        editTextCd?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = editTextCd.text.toString()
                var mins = value.toLongOrNull() ?: 1L

                if(mins > 58L)
                {
                    mins = 58L
                }
                if(mins < 1L){
                    mins = 1L
                }

                if(editTextCd.text.toString() != mins.toString()){

                    editTextCd.setText(mins.toString(), TextView.BufferType.EDITABLE)
                }

                if(mins != countdownMinutes){
                    dataManager.setCountdownMinutes(mins)
                    countdownMinutes = mins
                    scheduleRamadanNotifications(this@MainActivity, cachedDataProp)
                    Toast.makeText(this@MainActivity, "Bildirimler yeni geri sayım süresine göre ayarlandı.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.s_imsakiye_button_d).setOnClickListener { checkDataAndOpenPanel()}

    }



    suspend fun setLocationUseToSettings(){

        city = getCity()

        if (city.isEmpty()) {
            city = "İstanbul"
        }

        val dataManager = DataManager.getInstance(this@MainActivity)
        dataManager.setLocationUsingData(true, city)
    }

    @SuppressLint("SetTextI18n")
    fun loadNightPrayerTimes(dayList: List<PrayTimeData>, city: String){

        val panel = findViewById<View>(R.id.nightPrayPage)
        showPanel(panel, 1000)


        findViewById<TextView>(R.id.pt_city_name_text_night).text = city

        if(!useLocation || !hasLocationPermission()){
            findViewById<View>(R.id.pt_location_set_icon_night).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.dark))
        }
        else{
            findViewById<View>(R.id.location_set_icon_night).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
        }

        for ((index, day) in dayList.withIndex())
        {
            val currentDayDate = LocalDate.parse(day.date, DateTimeFormatter.ISO_DATE_TIME)

            when (index) {
                0 -> {
                    findViewById<TextView>(R.id.dun_pt_greg_date_n).text = currentDayDate.dayOfMonth.toString() + " " + getTurkishGregMonth(currentDayDate.monthValue) + " " + currentDayDate.year.toString()
                    findViewById<TextView>(R.id.dun_pt_hijri_n).text = day.hijri_date.day.toString() + " " + getTurkishHijriMonth(day.hijri_date.month) + " " + day.hijri_date.year.toString()

                    findViewById<TextView>(R.id.dun_pt_imsak_time_n).text = day.times.imsak
                    findViewById<TextView>(R.id.dun_pt_gun_time_n).text = day.times.gunes
                    findViewById<TextView>(R.id.dun_pt_ogle_time_n).text = day.times.ogle
                    findViewById<TextView>(R.id.dun_pt_ikindi_time_n).text = day.times.ikindi
                    findViewById<TextView>(R.id.dun_pt_aksam_time_n).text = day.times.aksam
                    findViewById<TextView>(R.id.dun_pt_yatsi_time_n).text = day.times.yatsi

                }
                1 -> {
                    findViewById<TextView>(R.id.bugun_pt_greg_date_n).text = currentDayDate.dayOfMonth.toString() + " " + getTurkishGregMonth(currentDayDate.monthValue) + " " + currentDayDate.year.toString()
                    findViewById<TextView>(R.id.bugun_pt_hijri_n).text = day.hijri_date.day.toString() + " " + getTurkishHijriMonth(day.hijri_date.month) + " " + day.hijri_date.year.toString()

                    findViewById<TextView>(R.id.bugun_pt_imsak_time_n).text = day.times.imsak
                    findViewById<TextView>(R.id.bugun_pt_gun_time_n).text = day.times.gunes
                    findViewById<TextView>(R.id.bugun_pt_ogle_time_n).text = day.times.ogle
                    findViewById<TextView>(R.id.bugun_pt_ikindi_time_n).text = day.times.ikindi
                    findViewById<TextView>(R.id.bugun_pt_aksam_time_n).text = day.times.aksam
                    findViewById<TextView>(R.id.bugun_pt_yatsi_time_n).text = day.times.yatsi

                }
                2 -> {
                    findViewById<TextView>(R.id.yarin_pt_greg_date_n).text = currentDayDate.dayOfMonth.toString() + " " + getTurkishGregMonth(currentDayDate.monthValue) + " " + currentDayDate.year.toString()
                    findViewById<TextView>(R.id.yarin_pt_hijri_n).text = day.hijri_date.day.toString() + " " + getTurkishHijriMonth(day.hijri_date.month) + " " + day.hijri_date.year.toString()

                    findViewById<TextView>(R.id.yarin_pt_imsak_time_n).text = day.times.imsak
                    findViewById<TextView>(R.id.yarin_pt_gun_time_n).text = day.times.gunes
                    findViewById<TextView>(R.id.yarin_pt_ogle_time_n).text = day.times.ogle
                    findViewById<TextView>(R.id.yarin_pt_ikindi_time_n).text = day.times.ikindi
                    findViewById<TextView>(R.id.yarin_pt_aksam_time_n).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_pt_yatsi_time_n).text = day.times.yatsi
                }
                3 -> {
                    findViewById<TextView>(R.id.yarin_1_pt_greg_date_n).text = currentDayDate.dayOfMonth.toString() + " " + getTurkishGregMonth(currentDayDate.monthValue) + " " + currentDayDate.year.toString()
                    findViewById<TextView>(R.id.yarin_1_pt_hijri_n).text = day.hijri_date.day.toString() + " " + getTurkishHijriMonth(day.hijri_date.month) + " " + day.hijri_date.year.toString()

                    findViewById<TextView>(R.id.yarin_1_pt_imsak_time_n).text = day.times.imsak
                    findViewById<TextView>(R.id.yarin_1_pt_gun_time_n).text = day.times.gunes
                    findViewById<TextView>(R.id.yarin_1_pt_ogle_time_n).text = day.times.ogle
                    findViewById<TextView>(R.id.yarin_1_pt_ikindi_time_n).text = day.times.ikindi
                    findViewById<TextView>(R.id.yarin_1_pt_aksam_time_n).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_1_pt_yatsi_time_n).text = day.times.yatsi

                    findViewById<TextView>(R.id.yarin_1_pt_dun_header_n).text = getTurkishWeekday(currentDayDate.dayOfWeek)
                }
                4 -> {
                    findViewById<TextView>(R.id.yarin_2_pt_greg_date_n).text = currentDayDate.dayOfMonth.toString() + " " + getTurkishGregMonth(currentDayDate.monthValue) + " " + currentDayDate.year.toString()
                    findViewById<TextView>(R.id.yarin_2_pt_hijri_n).text = day.hijri_date.day.toString() + " " + getTurkishHijriMonth(day.hijri_date.month) + " " + day.hijri_date.year.toString()

                    findViewById<TextView>(R.id.yarin_2_pt_imsak_time_n).text = day.times.imsak
                    findViewById<TextView>(R.id.yarin_2_pt_gun_time_n).text = day.times.gunes
                    findViewById<TextView>(R.id.yarin_2_pt_ogle_time_n).text = day.times.ogle
                    findViewById<TextView>(R.id.yarin_2_pt_ikindi_time_n).text = day.times.ikindi
                    findViewById<TextView>(R.id.yarin_2_pt_aksam_time_n).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_2_pt_yatsi_time_n).text = day.times.yatsi

                    findViewById<TextView>(R.id.yarin_2_pt_dun_header_n).text = getTurkishWeekday(currentDayDate.dayOfWeek)
                }
            }
        }
        
        findViewById<Button>(R.id.pt_imsakiye_button_night).setOnClickListener { 
            checkDataAndOpenPanel()
        }

    }


    @SuppressLint("SetTextI18n")
    fun loadDayPrayerTimes(dayList: List<PrayTimeData>, city: String){

        val panel = findViewById<View>(R.id.dayPrayPage)
        showPanel(panel, 1000)


        findViewById<TextView>(R.id.pt_city_name_text_day).text = city

        if(!useLocation ||!hasLocationPermission()){
            findViewById<View>(R.id.pt_location_set_icon_night).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.dark))
        }

        for ((index, day) in dayList.withIndex())
        {
            val currentDayDate = LocalDate.parse(day.date, DateTimeFormatter.ISO_DATE_TIME)

            when (index) {
                0 -> {
                    findViewById<TextView>(R.id.dun_pt_greg_date_d).text = currentDayDate.dayOfMonth.toString() + " " + getTurkishGregMonth(currentDayDate.monthValue) + " " + currentDayDate.year.toString()
                    findViewById<TextView>(R.id.dun_pt_hijri_d).text = day.hijri_date.day.toString() + " " + getTurkishHijriMonth(day.hijri_date.month) + " " + day.hijri_date.year.toString()

                    findViewById<TextView>(R.id.dun_pt_imsak_time_d).text = day.times.imsak
                    findViewById<TextView>(R.id.dun_pt_gun_time_d).text = day.times.gunes
                    findViewById<TextView>(R.id.dun_pt_ogle_time_d).text = day.times.ogle
                    findViewById<TextView>(R.id.dun_pt_ikindi_time_d).text = day.times.ikindi
                    findViewById<TextView>(R.id.dun_pt_aksam_time_d).text = day.times.aksam
                    findViewById<TextView>(R.id.dun_pt_yatsi_time_d).text = day.times.yatsi

                }
                1 -> {
                    findViewById<TextView>(R.id.bugun_pt_greg_date_d).text = currentDayDate.dayOfMonth.toString() + " " + getTurkishGregMonth(currentDayDate.monthValue) + " " + currentDayDate.year.toString()
                    findViewById<TextView>(R.id.bugun_pt_hijri_d).text = day.hijri_date.day.toString() + " " + getTurkishHijriMonth(day.hijri_date.month) + " " + day.hijri_date.year.toString()

                    findViewById<TextView>(R.id.bugun_pt_imsak_time_d).text = day.times.imsak
                    findViewById<TextView>(R.id.bugun_pt_gun_time_d).text = day.times.gunes
                    findViewById<TextView>(R.id.bugun_pt_ogle_time_d).text = day.times.ogle
                    findViewById<TextView>(R.id.bugun_pt_ikindi_time_d).text = day.times.ikindi
                    findViewById<TextView>(R.id.bugun_pt_aksam_time_d).text = day.times.aksam
                    findViewById<TextView>(R.id.bugun_pt_yatsi_time_d).text = day.times.yatsi

                }
                2 -> {
                    findViewById<TextView>(R.id.yarin_pt_greg_date_d).text = currentDayDate.dayOfMonth.toString() + " " + getTurkishGregMonth(currentDayDate.monthValue) + " " + currentDayDate.year.toString()
                    findViewById<TextView>(R.id.yarin_pt_hijri_d).text = day.hijri_date.day.toString() + " " + getTurkishHijriMonth(day.hijri_date.month) + " " + day.hijri_date.year.toString()

                    findViewById<TextView>(R.id.yarin_pt_imsak_time_d).text = day.times.imsak
                    findViewById<TextView>(R.id.yarin_pt_gun_time_d).text = day.times.gunes
                    findViewById<TextView>(R.id.yarin_pt_ogle_time_d).text = day.times.ogle
                    findViewById<TextView>(R.id.yarin_pt_ikindi_time_d).text = day.times.ikindi
                    findViewById<TextView>(R.id.yarin_pt_aksam_time_d).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_pt_yatsi_time_d).text = day.times.yatsi
                }
                3 -> {
                    findViewById<TextView>(R.id.yarin_1_pt_greg_date_d).text = currentDayDate.dayOfMonth.toString() + " " + getTurkishGregMonth(currentDayDate.monthValue) + " " + currentDayDate.year.toString()
                    findViewById<TextView>(R.id.yarin_1_pt_hijri_d).text = day.hijri_date.day.toString() + " " + getTurkishHijriMonth(day.hijri_date.month) + " " + day.hijri_date.year.toString()

                    findViewById<TextView>(R.id.yarin_1_pt_imsak_time_d).text = day.times.imsak
                    findViewById<TextView>(R.id.yarin_1_pt_gun_time_d).text = day.times.gunes
                    findViewById<TextView>(R.id.yarin_1_pt_ogle_time_d).text = day.times.ogle
                    findViewById<TextView>(R.id.yarin_1_pt_ikindi_time_d).text = day.times.ikindi
                    findViewById<TextView>(R.id.yarin_1_pt_aksam_time_d).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_1_pt_yatsi_time_d).text = day.times.yatsi

                    findViewById<TextView>(R.id.yarin_1_pt_dun_header_d).text = getTurkishWeekday(currentDayDate.dayOfWeek)
                }
                4 -> {
                    findViewById<TextView>(R.id.yarin_2_pt_greg_date_d).text = currentDayDate.dayOfMonth.toString() + " " + getTurkishGregMonth(currentDayDate.monthValue) + " " + currentDayDate.year.toString()
                    findViewById<TextView>(R.id.yarin_2_pt_hijri_d).text = day.hijri_date.day.toString() + " " + getTurkishHijriMonth(day.hijri_date.month) + " " + day.hijri_date.year.toString()

                    findViewById<TextView>(R.id.yarin_2_pt_imsak_time_d).text = day.times.imsak
                    findViewById<TextView>(R.id.yarin_2_pt_gun_time_d).text = day.times.gunes
                    findViewById<TextView>(R.id.yarin_2_pt_ogle_time_d).text = day.times.ogle
                    findViewById<TextView>(R.id.yarin_2_pt_ikindi_time_d).text = day.times.ikindi
                    findViewById<TextView>(R.id.yarin_2_pt_aksam_time_d).text = day.times.aksam
                    findViewById<TextView>(R.id.yarin_2_pt_yatsi_time_d).text = day.times.yatsi

                    findViewById<TextView>(R.id.yarin_2_pt_dun_header_d).text = getTurkishWeekday(currentDayDate.dayOfWeek)
                }
            }
        }

        findViewById<Button>(R.id.pt_imsakiye_button_day).setOnClickListener {
            checkDataAndOpenPanel()
        }

    }

    suspend fun fetchFullYearTimings(city: String): List<PrayTimeData> {

        val cityId = CityManager.getInstance(this@MainActivity).getCityId(city)
            ?: run {
                Log.e("KDImsakiye","City not found! : $city")
                return emptyList()
            }

        val retrofit = Retrofit.Builder()
            .baseUrl("https://ezanvakti.imsakiyem.com/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(TurkishPrayerApiService::class.java)

        return try {
            val response = service.getAnnualCalendar(cityId)
            response.data
        } catch (e: Exception) {
            Log.e("KDImsakiye", "Failed to fetch annual data: ${e.message}")
            emptyList()
        }
    }

    fun checkTimeStatus(time: String) : Boolean
    {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTimeString = sdf.format(Date())
        val currentMinutes = timeToMinutes(currentTimeString)
        val timeMinutes = timeToMinutes(time)

        return currentMinutes >= timeMinutes
    }

    private var currentPanel: View? = null

    fun showPanel(newPanel: View, mDuration: Long) {
        val root = findViewById<ViewGroup>(R.id.mainRoot)

        if (newPanel == currentPanel) return

        val fade = Fade().apply {
            duration = mDuration
        }

        TransitionManager.beginDelayedTransition(root, fade)

        currentPanel?.visibility = View.GONE
        newPanel.visibility = View.VISIBLE

        currentPanel = newPanel
    }


    fun timeToMinutes(time: String): Int {
        val cleanTime = time.substring(0, 5)
        val parts = cleanTime.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    fun getTurkishGregMonth(monthNumber: Int) : String
    {
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

    fun getTurkishHijriMonth(monthNumber: Int) : String
    {
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
    fun getTurkishWeekday(day: String) : String
    {
        return when (day.trim()) {
            "Monday" -> "Pazartesi"
            "Tuesday" -> "Salı"
            "Wednesday" -> "Çarşamba"
            "Thursday" -> "Perşembe"
            "Friday" -> "Cuma"
            "Saturday" -> "Cumartesi"
            "Sunday" -> "Pazar"
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

    fun getTurkishShortDay(day: String) : String
    {
        return when (day.trim()) {
            "Pazartesi" -> "Pzt"
            "Salı" -> "Sal"
            "Çarşamba" -> "Çar"
            "Perşembe" -> "Per"
            "Cuma" -> "Cum"
            "Cumartesi" -> "Cmt"
            "Pazar" -> "Paz"
            else -> ""
        }
    }

    fun getInterestDays(list: List<PrayTimeData>, count: Int) : List<PrayTimeData>
    {
        val interestDays = mutableListOf<PrayTimeData>()

        val calendar = Calendar.getInstance()
        val todayIndex = calendar.get(Calendar.DAY_OF_YEAR) - 1


        for (i in 0 until count)
        {
            interestDays.add(list[todayIndex - 1 + i])
        }

        return interestDays
    }

    fun onVersionClick(view: View) {
        Log.d("KD Imsakiye", "User clicked to profile from : " + view.id)
        val intent = Intent(Intent.ACTION_VIEW, "https://www.linkedin.com/in/akaandonmez/".toUri())
        startActivity(intent)
    }
}