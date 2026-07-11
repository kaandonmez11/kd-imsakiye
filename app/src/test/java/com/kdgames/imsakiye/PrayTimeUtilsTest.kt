package com.kdgames.imsakiye

import com.kdgames.imsakiye.data.HijriDate
import com.kdgames.imsakiye.data.PrayTimeData
import com.kdgames.imsakiye.data.Times
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PrayTimeUtilsTest {

    private fun day(date: LocalDate) = PrayTimeData(
        date = date.atStartOfDay().toString(),
        hijri_date = HijriDate(1, 9, 1447),
        times = Times("05:00", "06:30", "13:00", "16:30", "19:45", "21:00")
    )

    private fun yearData(year: Int): List<PrayTimeData> {
        val start = LocalDate.of(year, 1, 1)
        val end = LocalDate.of(year, 12, 31)
        return generateSequence(start) { if (it < end) it.plusDays(1) else null }
            .map { day(it) }
            .toList()
    }

    @Test
    fun timeToMinutes_parsesPlainAndSuffixedTimes() {
        assertEquals(0, PrayTimeUtils.timeToMinutes("00:00"))
        assertEquals(5 * 60 + 30, PrayTimeUtils.timeToMinutes("05:30"))
        assertEquals(19 * 60 + 45, PrayTimeUtils.timeToMinutes("19:45 PM"))
    }

    @Test
    fun checkTimeStatus_comparesAgainstNow() {
        assertTrue(PrayTimeUtils.checkTimeStatus("05:00", LocalTime.of(5, 0)))
        assertTrue(PrayTimeUtils.checkTimeStatus("05:00", LocalTime.of(12, 0)))
        assertFalse(PrayTimeUtils.checkTimeStatus("05:00", LocalTime.of(4, 59)))
    }

    @Test
    fun getInterestDays_midYear_todayIsSecondEntry() {
        val today = LocalDate.of(2026, 3, 15)
        val days = PrayTimeUtils.getInterestDays(yearData(2026), 8, today)

        assertEquals(8, days.size)
        assertEquals(today.minusDays(1), PrayTimeUtils.parseDate(days[0].date))
        assertEquals(today, PrayTimeUtils.parseDate(days[1].date))
        assertEquals(today.plusDays(1), PrayTimeUtils.parseDate(days[2].date))
    }

    @Test
    fun getInterestDays_firstDayOfYear_doesNotCrash() {
        val today = LocalDate.of(2026, 1, 1)
        val days = PrayTimeUtils.getInterestDays(yearData(2026), 8, today)

        assertEquals(8, days.size)
        assertEquals(today, PrayTimeUtils.parseDate(days[0].date))
    }

    @Test
    fun getInterestDays_lastDayOfYear_truncatesButKeepsTodayAtIndex1() {
        val today = LocalDate.of(2026, 12, 31)
        val days = PrayTimeUtils.getInterestDays(yearData(2026), 8, today)

        // pencere kaydırılmaz, kısalır: [dün, bugün]
        assertEquals(2, days.size)
        assertEquals(today, PrayTimeUtils.parseDate(days[1].date))
    }

    @Test
    fun getInterestDays_nearYearEnd_todayStaysAtIndex1() {
        val today = LocalDate.of(2026, 12, 27)
        val days = PrayTimeUtils.getInterestDays(yearData(2026), 9, today)

        assertEquals(6, days.size)
        assertEquals(today, PrayTimeUtils.parseDate(days[1].date))
    }

    @Test
    fun getInterestDays_emptyList_returnsEmpty() {
        assertTrue(PrayTimeUtils.getInterestDays(emptyList(), 8, LocalDate.of(2026, 3, 15)).isEmpty())
    }

    @Test
    fun getInterestDays_todayNotInData_fallsBackWithoutCrash() {
        val days = PrayTimeUtils.getInterestDays(yearData(2026), 8, LocalDate.of(2027, 6, 1))
        assertEquals(8, days.size)
    }

    @Test
    fun getInterestDays_listSmallerThanCount_returnsWholeList() {
        val list = listOf(
            day(LocalDate.of(2026, 3, 14)),
            day(LocalDate.of(2026, 3, 15)),
            day(LocalDate.of(2026, 3, 16))
        )
        val days = PrayTimeUtils.getInterestDays(list, 8, LocalDate.of(2026, 3, 15))
        assertEquals(3, days.size)
    }

    @Test
    fun formatGregDate_usesTurkishMonthNames() {
        assertEquals("15 Mart 2026", PrayTimeUtils.formatGregDate(LocalDate.of(2026, 3, 15)))
    }

    @Test
    fun formatHijriDate_usesTurkishMonthNames() {
        assertEquals("1 Ramazan 1447", PrayTimeUtils.formatHijriDate(HijriDate(1, 9, 1447)))
    }
}
