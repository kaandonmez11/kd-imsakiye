package com.kdgames.imsakiye

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.transition.Fade
import android.transition.TransitionManager
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputLayout
import com.kdgames.imsakiye.data.PrayTimeData
import com.kdgames.imsakiye.services.LiveCountdownReceiver
import com.kdgames.imsakiye.services.NotificationReceiver
import com.kdgames.imsakiye.services.TurkishPrayerApiService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class MainActivity : AppCompatActivity() {

    var cachedDataProp: List<PrayTimeData> = emptyList()
    var city: String = ""

    var useLocation: Boolean = true

    var reminderHours: Long = 1L
    var countdownMinutes: Long = 5L

    private val cities: List<String> by lazy {
        CityManager.getInstance(this).getCityNames()
    }

    private val apiService: TurkishPrayerApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://ezanvakti.imsakiyem.com/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TurkishPrayerApiService::class.java)
    }

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            lifecycleScope.launch { initializeApp() }
        }

    private val notificationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private var exactAlarmSettingsShown = false

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

        if (hasLocationPermission()) {
            lifecycleScope.launch { initializeApp() }
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private suspend fun initializeApp() {
        val dataManager = DataManager.getInstance(this)

        val (dmUseLocation, dmCity) = dataManager.getLocationUsingData()

        useLocation = dmUseLocation

        city = if (useLocation) {
            getCity()
        } else {
            dmCity ?: ""
        }

        if (city.isEmpty()) {
            city = "İstanbul"
        }

        reminderHours = dataManager.getReminderHours()
        countdownMinutes = dataManager.getCountdownMinutes()

        var data = loadCityData(city)

        if (data.isNotEmpty() && LocalDate.now().year > PrayTimeUtils.parseDate(data[0].date).year) {
            val refreshed = loadCityData(city, forceRefresh = true)
            if (refreshed.isNotEmpty()) {
                data = refreshed
            }
        }

        cachedDataProp = data

        if (cachedDataProp.isEmpty()) {
            Toast.makeText(
                this,
                "Vakit verileri alınamadı. İnternet bağlantınızı kontrol edip ekrana dokunarak tekrar deneyin.",
                Toast.LENGTH_LONG
            ).show()
            findViewById<View>(R.id.loadingPage).setOnClickListener { view ->
                view.setOnClickListener(null)
                lifecycleScope.launch { initializeApp() }
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        scheduleRamadanNotifications(this, cachedDataProp)

        checkDataAndOpenPanel()
    }

    private suspend fun loadCityData(city: String, forceRefresh: Boolean = false): List<PrayTimeData> {
        val dataManager = DataManager.getInstance(this)

        if (!forceRefresh) {
            dataManager.getAnnualData(city)?.let {
                if (it.isNotEmpty()) return it
            }
        }

        val fetched = fetchFullYearTimings(city)
        if (fetched.isNotEmpty()) {
            dataManager.saveAnnualData(fetched, city)
        }
        return fetched
    }

    private suspend fun applyCityChange() {
        cachedDataProp = loadCityData(city)
        scheduleRamadanNotifications(this, cachedDataProp)
        Toast.makeText(this, "Şehir değişikliği uygulandı.", Toast.LENGTH_SHORT).show()
    }

    fun checkDataAndOpenPanel() {
        val days = PrayTimeUtils.getInterestDays(cachedDataProp, 8)
        if (days.size < 3) return

        val today = days[1]

        if (PrayTimeUtils.checkTimeStatus(today.times.imsak)) {
            if (PrayTimeUtils.checkTimeStatus(today.times.aksam)) {
                loadNightMode(days, days[2].times.imsak, city)
            } else {
                loadDayMode(days, today.times.aksam, city)
            }
        } else {
            loadNightMode(days, today.times.imsak, city)
        }
    }

    suspend fun scheduleRamadanNotifications(context: Context, allYearData: List<PrayTimeData>) {
        cancelAllAlarms(context)

        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager

        if (!alarmManager.canScheduleExactAlarms() && !exactAlarmSettingsShown) {
            exactAlarmSettingsShown = true
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (_: ActivityNotFoundException) {
            }
        }

        val now = LocalDateTime.now()
        val requestCodes = mutableListOf<Int>()

        allYearData.forEach { day ->
            // Only Ramadan month (Hijri 9th month)
            if (day.hijri_date.month != 9) return@forEach

            val date = PrayTimeUtils.parseDate(day.date)
            val sahurDateTime = LocalDateTime.of(date, LocalTime.parse(day.times.imsak))
            val iftarDateTime = LocalDateTime.of(date, LocalTime.parse(day.times.aksam))

            // --- SAHUR SCHEDULING ---
            if (sahurDateTime.minusHours(reminderHours).isAfter(now)) {
                planTask(context, alarmManager, sahurDateTime.minusHours(reminderHours), "Sahur Vakti", "İmsak vaktine az kaldı.", "SAHUR_1H_${date.dayOfMonth}_${date.monthValue}".hashCode(), "faded_davul", requestCodes)
            }
            if (sahurDateTime.minusMinutes(countdownMinutes).isAfter(now)) {
                planLiveTask(context, alarmManager, sahurDateTime, "İmsak Geri Sayım", date.dayOfMonth * 100 + date.monthValue, countdownMinutes, requestCodes)
            }
            if (sahurDateTime.isAfter(now)) {
                planTask(context, alarmManager, sahurDateTime, "İmsak Attı", "Oruç başladı.", "SAHUR_FULL_${date.dayOfMonth}_${date.monthValue}".hashCode(), "ezan", requestCodes)
            }

            // --- IFTAR SCHEDULING ---
            if (iftarDateTime.minusHours(reminderHours).isAfter(now)) {
                planTask(context, alarmManager, iftarDateTime.minusHours(reminderHours), "İftar Vaktine Az Kaldı", "Hazırlıklar tamam mı?", "IFTAR_1H_${date.dayOfMonth}_${date.monthValue}".hashCode(), "", requestCodes)
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

    private suspend fun cancelAllAlarms(context: Context) {
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

    @SuppressLint("MissingPermission")
    private suspend fun getCity(): String {
        return withTimeoutOrNull(15000L) { // 15.000 milliseconds = 15 seconds
            try {
                if (!hasLocationPermission()) return@withTimeoutOrNull ""

                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)
                val location = fusedLocationClient.lastLocation.await()

                if (location != null) {
                    val geocoder = Geocoder(this@MainActivity, Locale.forLanguageTag("tr"))
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

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }

    fun setSplashScreen(splashScreen: SplashScreen) {
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

        val target = LocalTime.parse(targetTime.substringBefore(" "))
        var targetDateTime = LocalDateTime.of(LocalDate.now(), target)

        // If target time is before now, automatically set for tomorrow
        if (targetDateTime.isBefore(LocalDateTime.now())) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        val remainingMillis = Duration.between(LocalDateTime.now(), targetDateTime).toMillis()

        countdownTimer = object : CountDownTimer(remainingMillis, 1000) {
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

    private data class ImsakiyeRowIds(val header: Int?, val dayIndex: Int, val iftar: Int, val sahur: Int)

    private val nightImsakiyeRows = listOf(
        ImsakiyeRowIds(null, R.id.dun_day_index_night, R.id.dun_iftar_vakti_night, R.id.dun_sahur_vakti_night),
        ImsakiyeRowIds(null, R.id.bugun_day_index_night, R.id.bugun_iftar_vakti_night, R.id.bugun_sahur_vakti_night),
        ImsakiyeRowIds(null, R.id.yarin_day_index_night, R.id.yarin_iftar_vakti_night, R.id.yarin_sahur_vakti_night),
        ImsakiyeRowIds(R.id.yarin_1_header_night, R.id.yarin_1_day_index_night, R.id.yarin_1_iftar_vakti_night, R.id.yarin_1_sahur_vakti_night),
        ImsakiyeRowIds(R.id.yarin_2_header_night, R.id.yarin_2_day_index_night, R.id.yarin_2_iftar_vakti_night, R.id.yarin_2_sahur_vakti_night),
        ImsakiyeRowIds(R.id.yarin_3_header_night, R.id.yarin_3_day_index_night, R.id.yarin_3_iftar_vakti_night, R.id.yarin_3_sahur_vakti_night),
        ImsakiyeRowIds(R.id.yarin_4_header_night, R.id.yarin_4_day_index_night, R.id.yarin_4_iftar_vakti_night, R.id.yarin_4_sahur_vakti_night),
        ImsakiyeRowIds(R.id.yarin_5_header_night, R.id.yarin_5_day_index_night, R.id.yarin_5_iftar_vakti_night, R.id.yarin_5_sahur_vakti_night)
    )

    private val dayImsakiyeRows = listOf(
        ImsakiyeRowIds(null, R.id.dun_day_index_day, R.id.dun_iftar_vakti_day, R.id.dun_sahur_vakti_day),
        ImsakiyeRowIds(null, R.id.bugun_day_index_day, R.id.bugun_iftar_vakti_day, R.id.bugun_sahur_vakti_day),
        ImsakiyeRowIds(null, R.id.yarin_day_index_day, R.id.yarin_iftar_vakti_day, R.id.yarin_sahur_vakti_day),
        ImsakiyeRowIds(R.id.yarin_1_header_day, R.id.yarin_1_day_index_day, R.id.yarin_1_iftar_vakti_day, R.id.yarin_1_sahur_vakti_day),
        ImsakiyeRowIds(R.id.yarin_2_header_day, R.id.yarin_2_day_index_day, R.id.yarin_2_iftar_vakti_day, R.id.yarin_2_sahur_vakti_day),
        ImsakiyeRowIds(R.id.yarin_3_header_day, R.id.yarin_3_day_index_day, R.id.yarin_3_iftar_vakti_day, R.id.yarin_3_sahur_vakti_day),
        ImsakiyeRowIds(R.id.yarin_4_header_day, R.id.yarin_4_day_index_day, R.id.yarin_4_iftar_vakti_day, R.id.yarin_4_sahur_vakti_day),
        ImsakiyeRowIds(R.id.yarin_5_header_day, R.id.yarin_5_day_index_day, R.id.yarin_5_iftar_vakti_day, R.id.yarin_5_sahur_vakti_day)
    )

    private fun bindImsakiyeRows(rows: List<ImsakiyeRowIds>, days: List<PrayTimeData>) {
        rows.zip(days).forEach { (ids, day) ->
            ids.header?.let {
                findViewById<TextView>(it).text =
                    PrayTimeUtils.getTurkishShortDay(PrayTimeUtils.parseDate(day.date).dayOfWeek)
            }
            findViewById<TextView>(ids.dayIndex).text = day.hijri_date.day.toString()
            findViewById<TextView>(ids.iftar).text = day.times.aksam
            findViewById<TextView>(ids.sahur).text = day.times.imsak
        }
    }

    private fun setLocationIconTint(viewId: Int, activeColor: Int, inactiveColor: Int) {
        val color = if (useLocation && hasLocationPermission()) activeColor else inactiveColor
        findViewById<View>(viewId).backgroundTintList = colorList(color)
    }

    private fun colorList(colorRes: Int): ColorStateList =
        ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))

    fun loadNightMode(dayList: List<PrayTimeData>, countdownTime: String, city: String) {
        showPanel(findViewById(R.id.nightImsakiyePage), 1000)

        findViewById<TextView>(R.id.city_name_text_night).text = city
        setLocationIconTint(R.id.location_set_icon_night, R.color.primary, R.color.dark)

        val today = dayList[1]
        val todayDate = PrayTimeUtils.parseDate(today.date)
        findViewById<TextView>(R.id.regular_date_text_night).text = PrayTimeUtils.formatGregDate(todayDate)
        findViewById<TextView>(R.id.ramazan_date_text_night).text = PrayTimeUtils.formatHijriDate(today.hijri_date)
        findViewById<TextView>(R.id.day_text_night).text = PrayTimeUtils.getTurkishWeekday(todayDate.dayOfWeek)

        bindImsakiyeRows(nightImsakiyeRows, dayList)

        startSmartCountdown(countdownTime) { timeString ->
            findViewById<TextView>(R.id.remaining_time_text_night).text = timeString
        }

        findViewById<Button>(R.id.prayer_times_button_night).setOnClickListener { loadNightPrayerTimes(dayList, city) }
        findViewById<Button>(R.id.settings_button_night).setOnClickListener { loadSettings(nightSettingsConfig) }
    }

    fun loadDayMode(dayList: List<PrayTimeData>, countdownTime: String, city: String) {
        showPanel(findViewById(R.id.dayImsakiyePage), 1000)

        findViewById<TextView>(R.id.city_name_text_day).text = city
        setLocationIconTint(R.id.location_set_icon_day, R.color.secondary, R.color.light)

        val today = dayList[1]
        val todayDate = PrayTimeUtils.parseDate(today.date)
        findViewById<TextView>(R.id.regular_date_text_day).text = PrayTimeUtils.formatGregDate(todayDate)
        findViewById<TextView>(R.id.ramazan_date_text_day).text = PrayTimeUtils.formatHijriDate(today.hijri_date)
        findViewById<TextView>(R.id.day_text_day).text = PrayTimeUtils.getTurkishWeekday(todayDate.dayOfWeek)

        bindImsakiyeRows(dayImsakiyeRows, dayList)

        startSmartCountdown(countdownTime) { timeString ->
            findViewById<TextView>(R.id.remaining_time_text_day).text = timeString
        }

        findViewById<Button>(R.id.prayer_times_button_day).setOnClickListener { loadDayPrayerTimes(dayList, city) }
        findViewById<Button>(R.id.settings_button_day).setOnClickListener { loadSettings(daySettingsConfig) }
    }

    private data class SettingsConfig(
        val panelId: Int,
        val spinnerId: Int,
        val dropdownItemLayout: Int,
        val useLocationButtonId: Int,
        val locationIconId: Int,
        val cityNameTextId: Int,
        val reminderInputId: Int,
        val countdownInputId: Int,
        val imsakiyeButtonId: Int,
        val activeIconColor: Int,
        val inactiveIconColor: Int,
        val enabledButtonColor: Int,
        val disabledButtonColor: Int
    )

    private val nightSettingsConfig = SettingsConfig(
        panelId = R.id.nightSettingsPage,
        spinnerId = R.id.n_city_dropdown,
        dropdownItemLayout = R.layout.night_spinner_dropdown_item,
        useLocationButtonId = R.id.s_use_location_button_n,
        locationIconId = R.id.s_location_set_icon_night,
        cityNameTextId = R.id.s_city_name_text_night,
        reminderInputId = R.id.n_reminder_text_input_layout,
        countdownInputId = R.id.n_countdown_input,
        imsakiyeButtonId = R.id.s_imsakiye_button_n,
        activeIconColor = R.color.primary,
        inactiveIconColor = R.color.dark,
        enabledButtonColor = R.color.primary,
        disabledButtonColor = R.color.disabled_button_n
    )

    private val daySettingsConfig = SettingsConfig(
        panelId = R.id.daySettingsPage,
        spinnerId = R.id.d_city_dropdown,
        dropdownItemLayout = R.layout.day_spinner_dropdown_item,
        useLocationButtonId = R.id.s_use_location_button_d,
        locationIconId = R.id.s_location_set_icon_day,
        cityNameTextId = R.id.s_city_name_text_day,
        reminderInputId = R.id.d_reminder_text_input_layout,
        countdownInputId = R.id.d_countdown_input,
        imsakiyeButtonId = R.id.s_imsakiye_button_d,
        activeIconColor = R.color.secondary,
        inactiveIconColor = R.color.light,
        enabledButtonColor = R.color.secondary,
        disabledButtonColor = R.color.disabled_button_d
    )

    private fun loadSettings(config: SettingsConfig) {
        showPanel(findViewById(config.panelId), 1000)

        val dataManager = DataManager.getInstance(this)

        val spinner = findViewById<Spinner>(config.spinnerId)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, cities) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).setTextColor(resources.getColor(R.color.secondary, theme))
                return view
            }
        }
        adapter.setDropDownViewResource(config.dropdownItemLayout)
        spinner.adapter = adapter
        spinner.setSelection(adapter.getPosition(city), false)

        val useLocationButton = findViewById<Button>(config.useLocationButtonId)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedCity = parent.getItemAtPosition(position).toString()
                if (selectedCity == city) return

                useLocation = false
                city = selectedCity

                findViewById<View>(config.locationIconId).backgroundTintList = colorList(config.inactiveIconColor)
                findViewById<TextView>(config.cityNameTextId).text = selectedCity
                useLocationButton.isEnabled = true
                useLocationButton.backgroundTintList = colorList(config.enabledButtonColor)

                lifecycleScope.launch {
                    dataManager.setLocationUsingData(false, selectedCity)
                    applyCityChange()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        useLocationButton.isEnabled = !useLocation
        if (useLocation) {
            useLocationButton.backgroundTintList = colorList(config.disabledButtonColor)
        }

        useLocationButton.setOnClickListener {
            lifecycleScope.launch {
                useLocation = true
                setLocationUseToSettings()

                useLocationButton.isEnabled = false
                useLocationButton.backgroundTintList = colorList(config.disabledButtonColor)

                setLocationIconTint(config.locationIconId, config.activeIconColor, config.inactiveIconColor)
                findViewById<TextView>(config.cityNameTextId).text = city
                spinner.setSelection(adapter.getPosition(city), false)

                applyCityChange()
            }
        }

        setLocationIconTint(config.locationIconId, config.activeIconColor, config.inactiveIconColor)
        findViewById<TextView>(config.cityNameTextId).text = city

        setupNumberInput(
            layoutId = config.reminderInputId,
            min = 1L,
            max = 23L,
            toastMessage = "Bildirimler yeni hatırlatma süresine göre ayarlandı.",
            getValue = { reminderHours },
            setValue = { hours ->
                dataManager.setReminderHours(hours)
                reminderHours = hours
            }
        )

        setupNumberInput(
            layoutId = config.countdownInputId,
            min = 1L,
            max = 58L,
            toastMessage = "Bildirimler yeni geri sayım süresine göre ayarlandı.",
            getValue = { countdownMinutes },
            setValue = { mins ->
                dataManager.setCountdownMinutes(mins)
                countdownMinutes = mins
            }
        )

        findViewById<Button>(config.imsakiyeButtonId).setOnClickListener { checkDataAndOpenPanel() }
    }

    private fun setupNumberInput(
        layoutId: Int,
        min: Long,
        max: Long,
        toastMessage: String,
        getValue: () -> Long,
        setValue: suspend (Long) -> Unit
    ) {
        val editText = findViewById<TextInputLayout>(layoutId).editText ?: return
        editText.setText(getValue().toString(), TextView.BufferType.EDITABLE)

        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener

            val value = (editText.text.toString().toLongOrNull() ?: min).coerceIn(min, max)

            if (editText.text.toString() != value.toString()) {
                editText.setText(value.toString(), TextView.BufferType.EDITABLE)
            }

            if (value != getValue()) {
                lifecycleScope.launch {
                    setValue(value)
                    scheduleRamadanNotifications(this@MainActivity, cachedDataProp)
                    Toast.makeText(this@MainActivity, toastMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    suspend fun setLocationUseToSettings() {
        city = getCity()

        if (city.isEmpty()) {
            city = "İstanbul"
        }

        DataManager.getInstance(this).setLocationUsingData(true, city)
    }

    private data class PrayerRowIds(
        val gregDate: Int,
        val hijri: Int,
        val imsak: Int,
        val gunes: Int,
        val ogle: Int,
        val ikindi: Int,
        val aksam: Int,
        val yatsi: Int,
        val header: Int? = null
    )

    private val nightPrayerRows = listOf(
        PrayerRowIds(R.id.dun_pt_greg_date_n, R.id.dun_pt_hijri_n, R.id.dun_pt_imsak_time_n, R.id.dun_pt_gun_time_n, R.id.dun_pt_ogle_time_n, R.id.dun_pt_ikindi_time_n, R.id.dun_pt_aksam_time_n, R.id.dun_pt_yatsi_time_n),
        PrayerRowIds(R.id.bugun_pt_greg_date_n, R.id.bugun_pt_hijri_n, R.id.bugun_pt_imsak_time_n, R.id.bugun_pt_gun_time_n, R.id.bugun_pt_ogle_time_n, R.id.bugun_pt_ikindi_time_n, R.id.bugun_pt_aksam_time_n, R.id.bugun_pt_yatsi_time_n),
        PrayerRowIds(R.id.yarin_pt_greg_date_n, R.id.yarin_pt_hijri_n, R.id.yarin_pt_imsak_time_n, R.id.yarin_pt_gun_time_n, R.id.yarin_pt_ogle_time_n, R.id.yarin_pt_ikindi_time_n, R.id.yarin_pt_aksam_time_n, R.id.yarin_pt_yatsi_time_n),
        PrayerRowIds(R.id.yarin_1_pt_greg_date_n, R.id.yarin_1_pt_hijri_n, R.id.yarin_1_pt_imsak_time_n, R.id.yarin_1_pt_gun_time_n, R.id.yarin_1_pt_ogle_time_n, R.id.yarin_1_pt_ikindi_time_n, R.id.yarin_1_pt_aksam_time_n, R.id.yarin_1_pt_yatsi_time_n, R.id.yarin_1_pt_dun_header_n),
        PrayerRowIds(R.id.yarin_2_pt_greg_date_n, R.id.yarin_2_pt_hijri_n, R.id.yarin_2_pt_imsak_time_n, R.id.yarin_2_pt_gun_time_n, R.id.yarin_2_pt_ogle_time_n, R.id.yarin_2_pt_ikindi_time_n, R.id.yarin_2_pt_aksam_time_n, R.id.yarin_2_pt_yatsi_time_n, R.id.yarin_2_pt_dun_header_n)
    )

    private val dayPrayerRows = listOf(
        PrayerRowIds(R.id.dun_pt_greg_date_d, R.id.dun_pt_hijri_d, R.id.dun_pt_imsak_time_d, R.id.dun_pt_gun_time_d, R.id.dun_pt_ogle_time_d, R.id.dun_pt_ikindi_time_d, R.id.dun_pt_aksam_time_d, R.id.dun_pt_yatsi_time_d),
        PrayerRowIds(R.id.bugun_pt_greg_date_d, R.id.bugun_pt_hijri_d, R.id.bugun_pt_imsak_time_d, R.id.bugun_pt_gun_time_d, R.id.bugun_pt_ogle_time_d, R.id.bugun_pt_ikindi_time_d, R.id.bugun_pt_aksam_time_d, R.id.bugun_pt_yatsi_time_d),
        PrayerRowIds(R.id.yarin_pt_greg_date_d, R.id.yarin_pt_hijri_d, R.id.yarin_pt_imsak_time_d, R.id.yarin_pt_gun_time_d, R.id.yarin_pt_ogle_time_d, R.id.yarin_pt_ikindi_time_d, R.id.yarin_pt_aksam_time_d, R.id.yarin_pt_yatsi_time_d),
        PrayerRowIds(R.id.yarin_1_pt_greg_date_d, R.id.yarin_1_pt_hijri_d, R.id.yarin_1_pt_imsak_time_d, R.id.yarin_1_pt_gun_time_d, R.id.yarin_1_pt_ogle_time_d, R.id.yarin_1_pt_ikindi_time_d, R.id.yarin_1_pt_aksam_time_d, R.id.yarin_1_pt_yatsi_time_d, R.id.yarin_1_pt_dun_header_d),
        PrayerRowIds(R.id.yarin_2_pt_greg_date_d, R.id.yarin_2_pt_hijri_d, R.id.yarin_2_pt_imsak_time_d, R.id.yarin_2_pt_gun_time_d, R.id.yarin_2_pt_ogle_time_d, R.id.yarin_2_pt_ikindi_time_d, R.id.yarin_2_pt_aksam_time_d, R.id.yarin_2_pt_yatsi_time_d, R.id.yarin_2_pt_dun_header_d)
    )

    private fun bindPrayerRows(rows: List<PrayerRowIds>, days: List<PrayTimeData>) {
        rows.zip(days).forEach { (ids, day) ->
            val date = PrayTimeUtils.parseDate(day.date)

            findViewById<TextView>(ids.gregDate).text = PrayTimeUtils.formatGregDate(date)
            findViewById<TextView>(ids.hijri).text = PrayTimeUtils.formatHijriDate(day.hijri_date)

            findViewById<TextView>(ids.imsak).text = day.times.imsak
            findViewById<TextView>(ids.gunes).text = day.times.gunes
            findViewById<TextView>(ids.ogle).text = day.times.ogle
            findViewById<TextView>(ids.ikindi).text = day.times.ikindi
            findViewById<TextView>(ids.aksam).text = day.times.aksam
            findViewById<TextView>(ids.yatsi).text = day.times.yatsi

            ids.header?.let {
                findViewById<TextView>(it).text = PrayTimeUtils.getTurkishWeekday(date.dayOfWeek)
            }
        }
    }

    fun loadNightPrayerTimes(dayList: List<PrayTimeData>, city: String) {
        showPanel(findViewById(R.id.nightPrayPage), 1000)

        findViewById<TextView>(R.id.pt_city_name_text_night).text = city
        setLocationIconTint(R.id.pt_location_set_icon_night, R.color.primary, R.color.dark)

        bindPrayerRows(nightPrayerRows, dayList)

        findViewById<Button>(R.id.pt_imsakiye_button_night).setOnClickListener {
            checkDataAndOpenPanel()
        }
    }

    fun loadDayPrayerTimes(dayList: List<PrayTimeData>, city: String) {
        showPanel(findViewById(R.id.dayPrayPage), 1000)

        findViewById<TextView>(R.id.pt_city_name_text_day).text = city
        setLocationIconTint(R.id.pt_location_set_icon_day, R.color.secondary, R.color.light)

        bindPrayerRows(dayPrayerRows, dayList)

        findViewById<Button>(R.id.pt_imsakiye_button_day).setOnClickListener {
            checkDataAndOpenPanel()
        }
    }

    suspend fun fetchFullYearTimings(city: String): List<PrayTimeData> {
        val cityId = CityManager.getInstance(this).getCityId(city)
            ?: run {
                Log.e("KDImsakiye", "City not found! : $city")
                return emptyList()
            }

        return try {
            val response = apiService.getAnnualCalendar(cityId)
            response.data
        } catch (e: Exception) {
            Log.e("KDImsakiye", "Failed to fetch annual data: ${e.message}")
            emptyList()
        }
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

    fun onVersionClick(view: View) {
        Log.d("KD Imsakiye", "User clicked to profile from : " + view.id)
        val intent = Intent(Intent.ACTION_VIEW, "https://www.linkedin.com/in/akaandonmez/".toUri())
        startActivity(intent)
    }
}
