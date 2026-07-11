package com.kdgames.imsakiye

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.transition.Fade
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.kdgames.imsakiye.data.PrayTimeData
import com.kdgames.imsakiye.services.NotificationScheduler
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

    var reminderMinutes: Long = 60L
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
    private var lastScheduleWasExact: Boolean? = null

    private enum class Tab { IMSAKIYE, VAKITLER, AYARLAR }

    private var currentTab = Tab.IMSAKIYE

    override fun attachBaseContext(newBase: Context) {
        // Tema, activity context'i bağlanmadan ÖNCE uygulanmalı;
        // onCreate'te geç kalır ve sistem teması geçerli olur
        ThemePrefs.apply(newBase)
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        setupUi()

        setSplashScreen(splashScreen)

        if (hasLocationPermission()) {
            lifecycleScope.launch { initializeApp() }
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun setupUi() {
        setContentView(R.layout.main_root)

        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(ContextCompat.getColor(this, R.color.bg))
        )
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !isNightTheme()

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

        applyWindowInsets()
        navInitialized = false
        setupNavBar()

        if (cachedDataProp.isNotEmpty()) {
            findViewById<View>(R.id.loadingPage).visibility = View.GONE
            currentPanel = null
            findViewById<View>(R.id.navBar).visibility = View.VISIBLE
            selectTab(currentTab)
        } else {
            currentPanel = findViewById(R.id.loadingPage)
        }
    }

    private fun isNightTheme(): Boolean {
        val mask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * uiMode manifest'te configChanges ile üstlenildi: tema değişiminde activity
     * yeniden yaratılmaz (splash görünmez); eski ekranın görüntüsü üstte tutulup
     * fade ile çözülerek renk geçişi yapılır.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        val oldRoot = findViewById<View>(R.id.mainRoot)
        val snapshot = try {
            if (oldRoot != null && oldRoot.width > 0 && oldRoot.height > 0) {
                val bmp = android.graphics.Bitmap.createBitmap(
                    oldRoot.width, oldRoot.height, android.graphics.Bitmap.Config.ARGB_8888
                )
                oldRoot.draw(android.graphics.Canvas(bmp))
                bmp
            } else null
        } catch (_: Exception) {
            null
        }

        countdownTimer?.cancel()
        navAnimator?.cancel()
        setupUi()

        if (snapshot != null) {
            val newRoot = findViewById<ViewGroup>(R.id.mainRoot)
            val overlay = ImageView(this).apply {
                setImageBitmap(snapshot)
                elevation = 100f
            }
            newRoot.addView(
                overlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            overlay.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction {
                    newRoot.removeView(overlay)
                    snapshot.recycle()
                }
                .start()
        }
    }

    // ---------- Tema ----------

    private fun desiredAutoNight(): Boolean {
        val days = PrayTimeUtils.getInterestDays(cachedDataProp, 3)
        if (days.size < 2) return isNightTheme()

        val today = days[1]
        return when {
            !PrayTimeUtils.checkTimeStatus(today.times.imsak) -> true  // imsaka sayılıyor
            !PrayTimeUtils.checkTimeStatus(today.times.aksam) -> false // iftara sayılıyor
            else -> true                                               // yarınki imsaka sayılıyor
        }
    }

    private fun applyPreferredTheme() {
        val night = when (ThemePrefs.getThemeMode(this)) {
            ThemePrefs.MODE_LIGHT -> false
            ThemePrefs.MODE_DARK -> true
            else -> desiredAutoNight().also { ThemePrefs.setAutoNight(this, it) }
        }

        val target = if (night) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != target) {
            AppCompatDelegate.setDefaultNightMode(target)
        }
    }

    private fun applyWindowInsets() {
        // include'daki android:id, nav_bar.xml kök id'sini override eder
        val navContainer = findViewById<View>(R.id.navBar)
        val headerBand = findViewById<View>(R.id.header_band)
        val navPadBottom = navContainer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            headerBand.updatePadding(top = bars.top)
            navContainer.updatePadding(bottom = navPadBottom + bars.bottom)
            insets
        }

        // Navbar yüzer: sayfa içerikleri navbar yüksekliği kadar alttan pay alır,
        // Vakitler listesi ise navbar arkasından kayıp fading edge ile kaybolur
        navContainer.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val navHeight = v.height
            if (navHeight == 0) return@addOnLayoutChangeListener
            findViewById<View>(R.id.imsakiyePage).updatePadding(bottom = navHeight)
            findViewById<View>(R.id.ayarlarPage).updatePadding(bottom = navHeight)
            val gap = (8 * resources.displayMetrics.density).toInt()
            findViewById<View>(R.id.vakitler_card_list).updatePadding(bottom = navHeight + gap)
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

        // Konumdan gelen il listede yoksa (ör. yurt dışı) İstanbul'a düş
        if (city.isEmpty() || CityManager.getInstance(this).getCityId(city) == null) {
            city = "İstanbul"
        }

        reminderMinutes = dataManager.getReminderMinutes()
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

        // Otomatik moddaysa vakte göre tema; değiştiyse onConfigurationChanged UI'ı kurar
        applyPreferredTheme()

        findViewById<View>(R.id.navBar).visibility = View.VISIBLE
        selectTab(currentTab)
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

    /**
     * Şehri güvenli şekilde değiştirir: veri gelmezse mevcut veri ve alarmlar
     * KORUNUR (eskiden boş liste atanıp tüm alarmlar siliniyordu).
     * @return başarılıysa true
     */
    private suspend fun changeCity(newCity: String): Boolean {
        val data = loadCityData(newCity)
        if (data.isEmpty()) {
            Toast.makeText(
                this,
                "Vakitler alınamadı, mevcut şehir korunuyor. Bağlantınızı kontrol edin.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        city = newCity
        cachedDataProp = data
        scheduleRamadanNotifications(this, cachedDataProp)
        applyPreferredTheme()
        Toast.makeText(this, "Şehir değişikliği uygulandı.", Toast.LENGTH_SHORT).show()
        return true
    }

    // ---------- Navigasyon ----------

    private fun setupNavBar() {
        findViewById<View>(R.id.nav_tab_imsakiye).setOnClickListener { selectTab(Tab.IMSAKIYE) }
        findViewById<View>(R.id.nav_tab_vakitler).setOnClickListener { selectTab(Tab.VAKITLER) }
        findViewById<View>(R.id.nav_tab_ayarlar).setOnClickListener { selectTab(Tab.AYARLAR) }
    }

    private fun selectTab(tab: Tab) {
        currentTab = tab
        when (tab) {
            Tab.IMSAKIYE -> {
                uiHandler.removeCallbacks(vakitlerTick)
                bindImsakiyeScreen()
                showPanel(findViewById(R.id.imsakiyePage), 300)
            }
            Tab.VAKITLER -> {
                countdownTimer?.cancel()
                bindVakitlerScreen()
                showPanel(findViewById(R.id.vakitlerPage), 300)
            }
            Tab.AYARLAR -> {
                countdownTimer?.cancel()
                uiHandler.removeCallbacks(vakitlerTick)
                bindAyarlarScreen()
                showPanel(findViewById(R.id.ayarlarPage), 300)
            }
        }
        updateNavBar(tab)
    }

    private var navAnimator: ValueAnimator? = null
    private var navInitialized = false

    private fun updateNavBar(selected: Tab) {
        val tabsRow = findViewById<LinearLayout>(R.id.nav_tabs)
        if (tabsRow.width == 0) {
            tabsRow.doOnLayout { applyNavState(selected, animate = false) }
        } else {
            applyNavState(selected, animate = navInitialized)
        }
    }

    private fun applyNavState(selected: Tab, animate: Boolean) {
        navInitialized = true

        val tabsRow = findViewById<LinearLayout>(R.id.nav_tabs)
        val highlight = findViewById<View>(R.id.nav_highlight)
        val tabViews = listOf(
            findViewById<LinearLayout>(R.id.nav_tab_imsakiye),
            findViewById<LinearLayout>(R.id.nav_tab_vakitler),
            findViewById<LinearLayout>(R.id.nav_tab_ayarlar)
        )
        val icons = listOf(
            findViewById<ImageView>(R.id.nav_icon_imsakiye),
            findViewById<ImageView>(R.id.nav_icon_vakitler),
            findViewById<ImageView>(R.id.nav_icon_ayarlar)
        )
        val labels = listOf(
            findViewById<TextView>(R.id.nav_label_imsakiye),
            findViewById<TextView>(R.id.nav_label_vakitler),
            findViewById<TextView>(R.id.nav_label_ayarlar)
        )
        val sel = selected.ordinal

        val activeColor = ContextCompat.getColor(this, R.color.nav_active_content)
        val inactiveColor = ContextCompat.getColor(this, R.color.nav_inactive)

        labels.forEach { it.setTextColor(activeColor) }

        // Hedef genişlikler: aktif tab içerik kadar, kalan alan pasiflere eşit bölünür
        // (ikon 21dp + etiket marjı 8dp + yatay padding 2×20dp + etiket genişliği)
        val innerWidth = tabsRow.width
        val density = resources.displayMetrics.density
        labels[sel].measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val activeWidth = ((21 + 8 + 40) * density).toInt() + labels[sel].measuredWidth
        val inactiveWidth = ((innerWidth - activeWidth) / 2).coerceAtLeast(0)
        if (labels[sel].visibility != View.VISIBLE) {
            labels[sel].alpha = 0f
        }

        val targetWidths = IntArray(3) { if (it == sel) activeWidth else inactiveWidth }
        // yuvarlama artığı son pasif taba
        val lastInactive = (2 downTo 0).first { it != sel }
        targetWidths[lastInactive] += innerWidth - targetWidths.sum()

        val targetLeft = (0 until sel).sumOf { targetWidths[it] }

        navAnimator?.cancel()

        if (!animate) {
            tabViews.forEachIndexed { i, tab ->
                tab.layoutParams = LinearLayout.LayoutParams(targetWidths[i], ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            labels.forEachIndexed { i, label ->
                val lp = label.layoutParams as LinearLayout.LayoutParams
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                lp.marginStart = (8 * density).toInt()
                label.layoutParams = lp
                label.visibility = if (i == sel) View.VISIBLE else View.GONE
                label.alpha = 1f
            }
            icons.forEachIndexed { i, icon ->
                icon.imageTintList = android.content.res.ColorStateList.valueOf(
                    if (i == sel) activeColor else inactiveColor
                )
            }
            highlight.layoutParams = android.widget.FrameLayout.LayoutParams(
                activeWidth, tabsRow.height
            ).apply { leftMargin = targetLeft }
            return
        }

        val startWidths = IntArray(3) { tabViews[it].width }
        val highlightLp = highlight.layoutParams as android.widget.FrameLayout.LayoutParams
        val startLeft = highlightLp.leftMargin
        val startHighlightWidth = highlight.width

        val startAlphas = FloatArray(3) { if (labels[it].visibility == View.VISIBLE) labels[it].alpha else 0f }
        val targetAlphas = FloatArray(3) { if (it == sel) 1f else 0f }

        // Etiket genişliği de animasyona dahil: solarken yer kaplamaya devam edip
        // sonda ikonu ortaya "ışınlamasın" diye genişlik/marjin 0'a doğru daraltılır
        val labelMargin = (8 * resources.displayMetrics.density).toInt()
        val labelFullWidths = IntArray(3) { i ->
            labels[i].measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            labels[i].measuredWidth
        }
        val startLabelWidths = IntArray(3) { i ->
            val lp = labels[i].layoutParams as LinearLayout.LayoutParams
            when {
                labels[i].visibility != View.VISIBLE -> 0
                lp.width >= 0 -> lp.width
                else -> labelFullWidths[i]
            }
        }
        val startLabelMargins = IntArray(3) { i ->
            if (labels[i].visibility != View.VISIBLE) 0
            else (labels[i].layoutParams as LinearLayout.LayoutParams).marginStart
        }
        val targetLabelWidths = IntArray(3) { if (it == sel) labelFullWidths[it] else 0 }
        val targetLabelMargins = IntArray(3) { if (it == sel) labelMargin else 0 }

        labels.forEachIndexed { i, label ->
            if (startAlphas[i] > 0f || i == sel) {
                val lp = label.layoutParams as LinearLayout.LayoutParams
                lp.width = startLabelWidths[i]
                lp.marginStart = startLabelMargins[i]
                label.layoutParams = lp
                label.visibility = View.VISIBLE
            }
        }

        icons.forEachIndexed { i, icon ->
            icon.imageTintList = android.content.res.ColorStateList.valueOf(
                if (i == sel) activeColor else inactiveColor
            )
        }

        navAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float

                tabViews.forEachIndexed { i, tab ->
                    val lp = tab.layoutParams as LinearLayout.LayoutParams
                    lp.width = (startWidths[i] + (targetWidths[i] - startWidths[i]) * f).toInt()
                    lp.weight = 0f
                    tab.layoutParams = lp
                }

                highlightLp.leftMargin = (startLeft + (targetLeft - startLeft) * f).toInt()
                highlightLp.width = (startHighlightWidth + (activeWidth - startHighlightWidth) * f).toInt()
                highlight.layoutParams = highlightLp

                labels.forEachIndexed { i, label ->
                    if (label.visibility == View.VISIBLE) {
                        label.alpha = startAlphas[i] + (targetAlphas[i] - startAlphas[i]) * f
                        val lp = label.layoutParams as LinearLayout.LayoutParams
                        lp.width = (startLabelWidths[i] + (targetLabelWidths[i] - startLabelWidths[i]) * f).toInt()
                        lp.marginStart = (startLabelMargins[i] + (targetLabelMargins[i] - startLabelMargins[i]) * f).toInt()
                        label.layoutParams = lp
                    }
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (cancelled) return
                    labels.forEachIndexed { i, label ->
                        val lp = label.layoutParams as LinearLayout.LayoutParams
                        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                        lp.marginStart = labelMargin
                        label.layoutParams = lp
                        label.alpha = 1f
                        label.visibility = if (i == sel) View.VISIBLE else View.GONE
                    }
                }
            })
            start()
        }
    }

    // ---------- İmsakiye ekranı ----------

    @SuppressLint("SetTextI18n")
    private fun bindImsakiyeScreen() {
        val days = PrayTimeUtils.getInterestDays(cachedDataProp, 9)
        if (days.size < 2) return

        val today = days[1]
        val todayDate = PrayTimeUtils.parseDate(today.date)

        updateLocationUi()

        findViewById<TextView>(R.id.imsakiye_date_text).text =
            "${todayDate.dayOfMonth} ${PrayTimeUtils.getTurkishGregMonth(todayDate.monthValue)}, ${PrayTimeUtils.getTurkishWeekday(todayDate.dayOfWeek)}"
        findViewById<TextView>(R.id.imsakiye_hijri_text).text =
            PrayTimeUtils.formatHijriDate(today.hijri_date)

        findViewById<TextView>(R.id.countdown_imsak_time).text = today.times.imsak
        findViewById<TextView>(R.id.countdown_iftar_time).text = today.times.aksam

        bindDayTable(days)
        startImsakiyeCountdown(days, todayDate)
    }

    private fun bindDayTable(days: List<PrayTimeData>) {
        val container = findViewById<LinearLayout>(R.id.imsakiye_day_list)
        container.removeAllViews()

        val inflater = LayoutInflater.from(this)
        val accent = ContextCompat.getColor(this, R.color.accent)
        val ink = ContextCompat.getColor(this, R.color.ink)
        val muted = ContextCompat.getColor(this, R.color.muted)

        days.forEachIndexed { index, day ->
            val row = inflater.inflate(R.layout.row_imsakiye_day, container, false)
            val date = PrayTimeUtils.parseDate(day.date)

            val hijriNum = row.findViewById<TextView>(R.id.row_hijri_num)
            val dayLabel = row.findViewById<TextView>(R.id.row_day_label)
            val imsakTime = row.findViewById<TextView>(R.id.row_imsak_time)
            val iftarTime = row.findViewById<TextView>(R.id.row_iftar_time)

            hijriNum.text = day.hijri_date.day.toString()
            dayLabel.text = when (index) {
                0 -> getString(R.string.day_yesterday)
                1 -> getString(R.string.day_today)
                2 -> getString(R.string.day_tomorrow)
                else -> PrayTimeUtils.getTurkishWeekday(date.dayOfWeek)
            }
            imsakTime.text = day.times.imsak
            iftarTime.text = day.times.aksam

            when (index) {
                0 -> {
                    row.alpha = 0.42f
                    hijriNum.setTextColor(muted)
                }
                1 -> {
                    row.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_container))
                    hijriNum.setTextColor(accent)
                    dayLabel.setTextColor(ink)
                    dayLabel.typeface = Typeface.create(dayLabel.typeface, 700, false)
                    listOf(imsakTime, iftarTime).forEach {
                        it.setTextColor(accent)
                        it.typeface = Typeface.create(it.typeface, 600, false)
                    }
                }
            }

            container.addView(row)
        }
    }

    private fun startImsakiyeCountdown(days: List<PrayTimeData>, todayDate: LocalDate) {
        val today = days[1]
        val now = LocalDateTime.now()

        val todayImsak = LocalDateTime.of(todayDate, LocalTime.parse(today.times.imsak))
        val todayIftar = LocalDateTime.of(todayDate, LocalTime.parse(today.times.aksam))

        val label: Int
        val start: LocalDateTime
        val target: LocalDateTime

        when {
            now.isBefore(todayImsak) -> {
                label = R.string.countdown_to_imsak
                start = LocalDateTime.of(todayDate.minusDays(1), LocalTime.parse(days[0].times.aksam))
                target = todayImsak
            }
            now.isBefore(todayIftar) -> {
                label = R.string.countdown_to_iftar
                start = todayImsak
                target = todayIftar
            }
            else -> {
                // yıl sonunda yarının verisi olmayabilir; sayaç gösterilmez
                val tomorrow = days.getOrNull(2) ?: run {
                    countdownTimer?.cancel()
                    findViewById<TextView>(R.id.countdown_label).setText(R.string.countdown_to_imsak)
                    findViewById<TextView>(R.id.countdown_text).text = "—"
                    findViewById<GlowProgressBar>(R.id.countdown_progress).progress = 1f
                    return
                }
                label = R.string.countdown_to_imsak
                start = todayIftar
                target = LocalDateTime.of(todayDate.plusDays(1), LocalTime.parse(tomorrow.times.imsak))
            }
        }

        findViewById<TextView>(R.id.countdown_label).text = getString(label)
        startCountdown(start, target)
    }

    private var countdownTimer: CountDownTimer? = null

    @SuppressLint("DefaultLocale")
    private fun startCountdown(start: LocalDateTime, target: LocalDateTime) {
        countdownTimer?.cancel()

        val countdownText = findViewById<TextView>(R.id.countdown_text)
        val progressBar = findViewById<GlowProgressBar>(R.id.countdown_progress)

        val totalMillis = Duration.between(start, target).toMillis().coerceAtLeast(1L)
        val remainingMillis = Duration.between(LocalDateTime.now(), target).toMillis()

        if (remainingMillis <= 0) {
            bindImsakiyeScreen()
            return
        }

        countdownTimer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val h = millisUntilFinished / (1000 * 60 * 60)
                val m = (millisUntilFinished / (1000 * 60)) % 60
                val s = (millisUntilFinished / 1000) % 60

                countdownText.text = String.format("%02d : %02d : %02d", h, m, s)

                progressBar.progress = 1f - millisUntilFinished.toFloat() / totalMillis
            }

            override fun onFinish() {
                // vakit dönüşümü: otomatik moddaysa tema fade ile değişir
                applyPreferredTheme()
                if (currentTab == Tab.IMSAKIYE) {
                    bindImsakiyeScreen()
                }
            }
        }.start()
    }

    // ---------- Namaz Vakitleri ekranı ----------

    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val vakitlerTick = Runnable {
        if (currentTab == Tab.VAKITLER) bindVakitlerScreen()
    }

    /** Ekran açıkken vakit geçince "sıradaki" vurgusu tazelensin diye
     *  bir sonraki vakit anına yenileme zamanlar */
    private fun scheduleVakitlerRefresh(days: List<PrayTimeData>) {
        uiHandler.removeCallbacks(vakitlerTick)

        val today = days.getOrNull(1) ?: return
        val todayDate = PrayTimeUtils.parseDate(today.date)
        val now = LocalDateTime.now()

        val todayTimes = listOf(
            today.times.imsak, today.times.gunes, today.times.ogle,
            today.times.ikindi, today.times.aksam, today.times.yatsi
        )
        val nextChange = todayTimes
            .map { LocalDateTime.of(todayDate, LocalTime.parse(it)) }
            .firstOrNull { it.isAfter(now) }
            ?: days.getOrNull(2)?.let {
                LocalDateTime.of(todayDate.plusDays(1), LocalTime.parse(it.times.imsak))
            }
            ?: return

        val delayMillis = Duration.between(now, nextChange).toMillis() + 1000
        uiHandler.postDelayed(vakitlerTick, delayMillis)
    }

    @SuppressLint("SetTextI18n")
    private fun bindVakitlerScreen() {
        val days = PrayTimeUtils.getInterestDays(cachedDataProp, 7)
        if (days.size < 2) return

        scheduleVakitlerRefresh(days)

        updateLocationUi()

        val todayDate = PrayTimeUtils.parseDate(days[1].date)
        findViewById<TextView>(R.id.vakitler_month_text).text =
            "${PrayTimeUtils.getTurkishGregMonth(todayDate.monthValue)} ${todayDate.year}"

        val container = findViewById<LinearLayout>(R.id.vakitler_card_list)
        container.removeAllViews()

        val cardGap = (10 * resources.displayMetrics.density).toInt()

        days.forEachIndexed { index, day ->
            val card = if (index == 1) {
                buildTodayCard(day)
            } else {
                buildCompactCard(
                    day,
                    badge = when (index) {
                        0 -> getString(R.string.badge_yesterday)
                        2 -> getString(R.string.badge_tomorrow)
                        else -> null
                    },
                    faded = index == 0
                )
            }

            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) lp.topMargin = cardGap
            container.addView(card, lp)
        }
    }

    private fun vakitEntries(day: PrayTimeData): List<Pair<String, String>> = listOf(
        getString(R.string.imsak_label) to day.times.imsak,
        getString(R.string.vakit_gunes) to day.times.gunes,
        getString(R.string.vakit_ogle) to day.times.ogle,
        getString(R.string.vakit_ikindi) to day.times.ikindi,
        getString(R.string.vakit_aksam) to day.times.aksam,
        getString(R.string.vakit_yatsi) to day.times.yatsi
    )

    @SuppressLint("SetTextI18n")
    private fun buildCompactCard(day: PrayTimeData, badge: String?, faded: Boolean): View {
        val inflater = LayoutInflater.from(this)
        val card = inflater.inflate(R.layout.card_vakit_compact, null)

        val date = PrayTimeUtils.parseDate(day.date)
        card.findViewById<TextView>(R.id.card_date_title).text =
            "${date.dayOfMonth} ${PrayTimeUtils.getTurkishGregMonth(date.monthValue)}, ${PrayTimeUtils.getTurkishWeekday(date.dayOfWeek)}"
        card.findViewById<TextView>(R.id.card_hijri_text).text =
            "${day.hijri_date.day} ${PrayTimeUtils.getTurkishHijriMonth(day.hijri_date.month)}"

        badge?.let {
            card.findViewById<TextView>(R.id.card_badge).apply {
                text = it
                visibility = View.VISIBLE
            }
        }

        val row = card.findViewById<LinearLayout>(R.id.card_times_row)
        vakitEntries(day).forEach { (label, time) ->
            val cell = inflater.inflate(R.layout.cell_vakit_compact, row, false)
            cell.findViewById<TextView>(R.id.cell_label).text = label
            cell.findViewById<TextView>(R.id.cell_time).text = time
            row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        if (faded) card.alpha = 0.5f
        return card
    }

    @SuppressLint("SetTextI18n")
    private fun buildTodayCard(day: PrayTimeData): View {
        val inflater = LayoutInflater.from(this)
        val card = inflater.inflate(R.layout.card_vakit_today, null)

        val date = PrayTimeUtils.parseDate(day.date)
        card.findViewById<TextView>(R.id.today_date_title).text =
            "${date.dayOfMonth} ${PrayTimeUtils.getTurkishGregMonth(date.monthValue)}, ${PrayTimeUtils.getTurkishWeekday(date.dayOfWeek)}"
        card.findViewById<TextView>(R.id.today_hijri_text).text =
            "${day.hijri_date.day} ${PrayTimeUtils.getTurkishHijriMonth(day.hijri_date.month)}"

        val entries = vakitEntries(day)

        // Sıradaki vakit: henüz geçmemiş ilk vakit; öncekiler soluk gösterilir
        val nextIndex = entries.indexOfFirst { !PrayTimeUtils.checkTimeStatus(it.second) }

        val grid = card.findViewById<LinearLayout>(R.id.today_grid)
        val gap = (8 * resources.displayMetrics.density).toInt()
        val accent = ContextCompat.getColor(this, R.color.accent)

        for (rowIndex in 0..1) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

            for (colIndex in 0..2) {
                val i = rowIndex * 3 + colIndex
                val (label, time) = entries[i]

                val cell = inflater.inflate(R.layout.cell_vakit_today, row, false) as LinearLayout
                val labelView = cell.findViewById<TextView>(R.id.cell_label)
                val timeView = cell.findViewById<TextView>(R.id.cell_time)
                labelView.text = label
                timeView.text = time

                when {
                    nextIndex in 0..5 && i < nextIndex -> {
                        cell.alpha = 0.45f
                    }
                    i == nextIndex -> {
                        cell.background = ContextCompat.getDrawable(this, R.drawable.bg_cell_next)
                        labelView.setTextColor(accent)
                        labelView.typeface = Typeface.create(labelView.typeface, 700, false)
                        timeView.setTextColor(accent)
                        timeView.typeface = Typeface.create(timeView.typeface, 600, false)
                    }
                    nextIndex == -1 -> {
                        // tüm vakitler geçti (yatsı sonrası)
                        cell.alpha = 0.45f
                    }
                    else -> {
                        timeView.typeface = Typeface.create(timeView.typeface, 600, false)
                    }
                }

                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (colIndex > 0) lp.marginStart = gap
                row.addView(cell, lp)
            }

            val rowLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (rowIndex > 0) rowLp.topMargin = gap
            grid.addView(row, rowLp)
        }

        return card
    }

    // ---------- Ayarlar ekranı ----------

    private var rescheduleJob: kotlinx.coroutines.Job? = null

    private fun animateIconTint(view: ImageView, targetColor: Int) {
        val from = view.imageTintList?.defaultColor ?: targetColor
        if (from == targetColor) return
        ValueAnimator.ofArgb(from, targetColor).apply {
            duration = 280
            addUpdateListener {
                view.imageTintList = android.content.res.ColorStateList.valueOf(it.animatedValue as Int)
            }
            start()
        }
    }

    private fun animateTextColor(view: TextView, targetColor: Int) {
        val from = view.currentTextColor
        if (from == targetColor) return
        ValueAnimator.ofArgb(from, targetColor).apply {
            duration = 280
            addUpdateListener { view.setTextColor(it.animatedValue as Int) }
            start()
        }
    }

    /** Metni fade ile söndürüp değiştirip fade ile geri açar */
    private fun setTextFaded(view: TextView, newText: String) {
        if (view.text.toString() == newText) return
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                view.text = newText
                view.animate().alpha(1f).setDuration(150).start()
            }
            .start()
    }

    private fun setCityTextAnimated(cityName: String) {
        setTextFaded(findViewById(R.id.header_city_text), cityName)
        setTextFaded(findViewById(R.id.settings_city_value), cityName)
    }

    /**
     * Konum durumu göstergeleri: konum otomatik kullanılıyorsa header pinleri
     * normal, elle şehir seçiliyse pinler soluk; Ayarlar'daki "Konumu kullan"
     * aksiyonu konum aktifken devre dışı ve "Güncel konum kullanılıyor" yazar.
     * animated=true iken renkler color fade, metin fade ile geçer.
     */
    private fun updateLocationUi(animated: Boolean = false) {
        // Ortak header: şehir adı (animasyonlu değişim setCityTextAnimated ile yapılır)
        val cityText = findViewById<TextView>(R.id.header_city_text)
        if (!animated && cityText.text.toString() != city) {
            cityText.text = city
        }

        val pinColor = ContextCompat.getColor(this, if (useLocation) R.color.ink else R.color.muted_faint)
        val pin = findViewById<ImageView>(R.id.header_pin)
        if (animated) {
            animateIconTint(pin, pinColor)
        } else {
            pin.imageTintList = android.content.res.ColorStateList.valueOf(pinColor)
        }

        val actionColor = ContextCompat.getColor(
            this,
            if (useLocation) R.color.muted_faint else R.color.accent
        )
        val icon = findViewById<ImageView>(R.id.settings_use_location_icon)
        val label = findViewById<TextView>(R.id.settings_use_location_label)
        val labelText = getString(
            if (useLocation) R.string.use_location_active else R.string.use_location
        )

        if (animated) {
            animateIconTint(icon, actionColor)
            animateTextColor(label, actionColor)
            setTextFaded(label, labelText)
        } else {
            icon.imageTintList = android.content.res.ColorStateList.valueOf(actionColor)
            label.setTextColor(actionColor)
            label.text = labelText
        }

        findViewById<View>(R.id.settings_use_location).isEnabled = !useLocation
    }

    @SuppressLint("SetTextI18n")
    private fun bindAyarlarScreen() {
        findViewById<TextView>(R.id.settings_city_value).text = city
        updateLocationUi()

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            ""
        }
        findViewById<TextView>(R.id.settings_version_text).text = "v$versionName · Kaan Dönmez"

        findViewById<View>(R.id.settings_city_row).setOnClickListener { showCityDialog() }

        findViewById<View>(R.id.settings_use_location).setOnClickListener {
            if (useLocation) return@setOnClickListener
            lifecycleScope.launch {
                // Konumdan gelen il listede yoksa (ör. yurt dışı) İstanbul'a düş
                var located = getCity()
                if (located.isEmpty() || CityManager.getInstance(this@MainActivity).getCityId(located) == null) {
                    located = "İstanbul"
                }

                if (changeCity(located)) {
                    useLocation = true
                    DataManager.getInstance(this@MainActivity).setLocationUsingData(true, located)
                    setCityTextAnimated(city)
                    updateLocationUi(animated = true)
                }
            }
        }

        setupDurationStepper(
            rowId = R.id.reminder_stepper_row,
            getTotal = { reminderMinutes },
            setTotal = { reminderMinutes = it }
        )
        setupDurationStepper(
            rowId = R.id.countdown_stepper_row,
            getTotal = { countdownMinutes },
            setTotal = { countdownMinutes = it }
        )

        updateSegmentUi()
    }

    /** Tasarım diline uygun, iki temayı da destekleyen özel şehir seçme paneli */
    private fun showCityDialog() {
        val dialog = android.app.Dialog(this)
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_city_picker, null)
        dialog.setContentView(content)

        val density = resources.displayMetrics.density
        dialog.window?.apply {
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            setLayout(
                resources.displayMetrics.widthPixels - (48 * density).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val scroll = content.findViewById<View>(R.id.city_picker_scroll)
        scroll.layoutParams = scroll.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        }

        val list = content.findViewById<LinearLayout>(R.id.city_picker_list)
        val accent = ContextCompat.getColor(this, R.color.accent)
        val inkSecondary = ContextCompat.getColor(this, R.color.ink_secondary)
        var selectedRow: View? = null

        cities.forEach { cityName ->
            val row = TextView(this).apply {
                text = cityName
                textSize = 15f
                setPadding((24 * density).toInt(), (12 * density).toInt(), (24 * density).toInt(), (12 * density).toInt())
                val selected = cityName == city
                setTextColor(if (selected) accent else inkSecondary)
                typeface = Typeface.create(typeface, if (selected) 700 else 500, false)
                if (selected) {
                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.accent_container))
                }
                setOnClickListener {
                    dialog.dismiss()
                    if (cityName != city) {
                        lifecycleScope.launch {
                            if (changeCity(cityName)) {
                                useLocation = false
                                DataManager.getInstance(this@MainActivity).setLocationUsingData(false, cityName)
                                setCityTextAnimated(cityName)
                                updateLocationUi(animated = true)
                            }
                        }
                    }
                }
            }
            list.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            if (cityName == city) selectedRow = row
        }

        dialog.show()

        // seçili şehir görünür konumda açılsın
        selectedRow?.let { row ->
            scroll.post {
                (scroll as android.widget.ScrollView).scrollTo(0, (row.top - scroll.height / 3).coerceAtLeast(0))
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupDurationStepper(rowId: Int, getTotal: () -> Long, setTotal: (Long) -> Unit) {
        val row = findViewById<LinearLayout>(rowId)
        row.removeAllViews()

        val inflater = LayoutInflater.from(this)
        val gap = (8 * resources.displayMetrics.density).toInt()

        val hourStepper = inflater.inflate(R.layout.stepper, row, false)
        val minuteStepper = inflater.inflate(R.layout.stepper, row, false)

        val hourValue = hourStepper.findViewById<android.widget.EditText>(R.id.stepper_value)
        val minuteValue = minuteStepper.findViewById<android.widget.EditText>(R.id.stepper_value)

        val minTotal = 5L
        val maxTotal = 12L * 60 + 59

        fun refresh() {
            val total = getTotal()
            // düzenlenmekte olan alanın içine yazma
            if (!hourValue.hasFocus()) hourValue.setText("${total / 60} sa")
            if (!minuteValue.hasFocus()) minuteValue.setText("${total % 60} dk")
        }

        // Saat ve dakika bileşen bazında sınırlanır (0-12 sa / 0-59 dk);
        // toplam clamp'i saat taşmasında dakikayı 59'a fırlatmasın diye kullanılmaz
        fun setHourMinute(hours: Int, minutes: Int) {
            val total = (hours.coerceIn(0, 12) * 60L + minutes.coerceIn(0, 59))
                .coerceIn(minTotal, maxTotal)
            if (total != getTotal()) {
                setTotal(total)
                persistNotificationSettings()
            }
            refresh()
        }

        fun changeHour(delta: Int) {
            val t = getTotal()
            setHourMinute((t / 60).toInt() + delta, (t % 60).toInt())
        }

        fun changeMinute(delta: Int) {
            val t = getTotal()
            setHourMinute((t / 60).toInt(), (t % 60).toInt() + delta)
        }

        // Değere tıklayınca sayısal klavyeyle doğrudan giriş: saat 0-12, dakika 0-59
        fun makeEditable(field: android.widget.EditText, isHour: Boolean) {
            field.setOnEditorActionListener { v, actionId, _ ->
                // bazı klavyeler DONE yerine UNSPECIFIED/NEXT gönderir
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                    actionId == android.view.inputmethod.EditorInfo.IME_NULL
                ) {
                    v.clearFocus()
                    true
                } else {
                    false
                }
            }
            field.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    val total = getTotal()
                    field.filters = arrayOf(android.text.InputFilter.LengthFilter(2))
                    field.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    field.setText((if (isHour) total / 60 else total % 60).toString())
                    field.selectAll()
                    field.post {
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        // klavye zaten açıksa sayısal moda geçmesi için input yeniden başlatılır
                        imm.restartInput(field)
                        imm.showSoftInput(field, 0)
                    }
                } else {
                    val typed = field.text.toString().trim().toIntOrNull()
                    field.inputType = android.text.InputType.TYPE_NULL
                    field.filters = arrayOf()
                    // odak başka bir giriş alanına geçmediyse klavyeyi kapat
                    if (currentFocus !is android.widget.EditText) {
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(field.windowToken, 0)
                    }
                    if (typed != null) {
                        val hours = (getTotal() / 60).toInt()
                        val minutes = (getTotal() % 60).toInt()
                        if (isHour) setHourMinute(typed, minutes) else setHourMinute(hours, typed)
                    } else {
                        refresh()
                    }
                }
            }
        }
        makeEditable(hourValue, isHour = true)
        makeEditable(minuteValue, isHour = false)

        hourStepper.findViewById<View>(R.id.stepper_minus).setOnClickListener { changeHour(-1) }
        hourStepper.findViewById<View>(R.id.stepper_plus).setOnClickListener { changeHour(1) }
        minuteStepper.findViewById<View>(R.id.stepper_minus).setOnClickListener { changeMinute(-5) }
        minuteStepper.findViewById<View>(R.id.stepper_plus).setOnClickListener { changeMinute(5) }

        row.addView(hourStepper)
        row.addView(minuteStepper, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = gap })

        val beforeText = TextView(this).apply {
            text = getString(R.string.before_suffix)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.muted_label))
            textSize = 12f
        }
        row.addView(beforeText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = gap })

        refresh()
    }

    /** Değerler ANINDA kaydedilir (uygulama hemen kapansa da kaybolmaz);
     *  yalnızca alarm kurulumu art arda tıklamalara karşı debounce edilir */
    private fun persistNotificationSettings() {
        val reminder = reminderMinutes
        val countdown = countdownMinutes
        lifecycleScope.launch {
            val dataManager = DataManager.getInstance(this@MainActivity)
            dataManager.setReminderMinutes(reminder)
            dataManager.setCountdownMinutes(countdown)
        }

        rescheduleJob?.cancel()
        rescheduleJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(800)
            scheduleRamadanNotifications(this@MainActivity, cachedDataProp)
        }
    }

    private fun updateSegmentUi(animate: Boolean = false) {
        val mode = ThemePrefs.getThemeMode(this)
        val segments = listOf(
            R.id.seg_gunduz to ThemePrefs.MODE_LIGHT,
            R.id.seg_gece to ThemePrefs.MODE_DARK,
            R.id.seg_otomatik to ThemePrefs.MODE_AUTO
        )

        val selectedColor = ContextCompat.getColor(this, R.color.nav_active_content)
        val normalColor = ContextCompat.getColor(this, R.color.muted_label)

        segments.forEach { (viewId, segMode) ->
            val segView = findViewById<TextView>(viewId)
            val active = segMode == mode

            segView.setTextColor(if (active) selectedColor else normalColor)
            segView.typeface = Typeface.create(segView.typeface, if (active) 700 else 600, false)

            segView.setOnClickListener {
                if (ThemePrefs.getThemeMode(this) != segMode) {
                    ThemePrefs.setThemeMode(this, segMode)
                    updateSegmentUi(animate = true)
                    applyPreferredTheme()
                }
            }
        }

        // Highlight navbar focus'u gibi kayarak seçili segmente gider
        val row = findViewById<LinearLayout>(R.id.seg_row)
        val highlight = findViewById<View>(R.id.seg_highlight)

        fun placeHighlight() {
            if (row.width == 0) return
            val cellWidth = row.width / 3
            val index = when (mode) {
                ThemePrefs.MODE_LIGHT -> 0
                ThemePrefs.MODE_DARK -> 1
                else -> 2
            }

            val lp = highlight.layoutParams
            if (lp.width != cellWidth || lp.height != row.height) {
                lp.width = cellWidth
                lp.height = row.height
                highlight.layoutParams = lp
            }

            val targetX = (index * cellWidth).toFloat()
            if (animate) {
                highlight.animate()
                    .translationX(targetX)
                    .setDuration(220)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            } else {
                highlight.translationX = targetX
            }
        }

        if (row.width == 0) {
            row.doOnLayout { placeHighlight() }
        } else {
            placeHighlight()
        }
    }

    // ---------- Bildirim planlama ----------

    suspend fun scheduleRamadanNotifications(context: Context, allYearData: List<PrayTimeData>) {
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

        lastScheduleWasExact = alarmManager.canScheduleExactAlarms()
        NotificationScheduler.schedule(context, allYearData, reminderMinutes, countdownMinutes)
    }

    // ---------- Konum ----------

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

    // ---------- Veri ----------

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

    // ---------- Genel ----------

    override fun onPause() {
        super.onPause()

        // arka planda boşa çalışmasınlar; onResume'da yeniden kurulurlar
        countdownTimer?.cancel()
        uiHandler.removeCallbacks(vakitlerTick)

        // bekleyen alarm kurulumu varsa debounce'u beklemeden hemen uygula
        // (kullanıcı uygulamayı kapatırsa ayar alarmlara işlenmemiş kalmasın)
        if (rescheduleJob?.isActive == true) {
            rescheduleJob?.cancel()
            rescheduleJob = lifecycleScope.launch {
                scheduleRamadanNotifications(this@MainActivity, cachedDataProp)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (cachedDataProp.isEmpty() || !navInitialized) return

        // arka plandayken vakit/tema değişmiş olabilir; ekranı tazele
        applyPreferredTheme()

        // kullanıcı bir stepper'ı düzenliyorken rebind odağı düşürmesin
        if (currentFocus !is android.widget.EditText) {
            selectTab(currentTab)
        }

        // exact alarm izni ayarlardan verilip dönüldüyse alarmlar dakik kurulsun
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (lastScheduleWasExact == false && alarmManager.canScheduleExactAlarms()) {
            lifecycleScope.launch {
                scheduleRamadanNotifications(this@MainActivity, cachedDataProp)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        uiHandler.removeCallbacks(vakitlerTick)
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
