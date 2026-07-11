package com.kdgames.imsakiye

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Tema tercihi. Sistem teması takip edilmez:
 * - LIGHT / DARK: sabit tema
 * - AUTO: vakit bazlı — iftara sayılırken gündüz, imsaka sayılırken gece.
 *   Son hesaplanan otomatik tema, açılışta flaş olmasın diye cache'lenir.
 *
 * Activity açılmadan senkron okunması gerektiği için SharedPreferences kullanır.
 */
object ThemePrefs {
    const val MODE_LIGHT = 0
    const val MODE_DARK = 1
    const val MODE_AUTO = 2

    private const val PREFS_NAME = "kd_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode_v2"
    private const val KEY_AUTO_NIGHT = "auto_night_last"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, MODE_AUTO)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun setAutoNight(context: Context, night: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_NIGHT, night).apply()
    }

    fun getStartupNightMode(context: Context): Int = when (getThemeMode(context)) {
        MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> if (prefs(context).getBoolean(KEY_AUTO_NIGHT, false)) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
    }

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getStartupNightMode(context))
    }
}
