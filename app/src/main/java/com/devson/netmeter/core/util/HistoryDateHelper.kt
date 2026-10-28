package com.devson.netmeter.core.util

import com.devson.netmeter.core.domain.model.DayBoundary
import com.devson.netmeter.core.domain.model.HistoryPeriod
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object HistoryDateHelper {

    fun calculateDaysForPeriod(
        period: HistoryPeriod,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        customStartMillis: Long? = null,
        customEndMillis: Long? = null
    ): List<DayBoundary> {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
        }

        return when (period) {
            HistoryPeriod.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                listOf(createDayBoundary(calendar, start, nowMillis, isToday = true))
            }
            HistoryPeriod.LAST_7_DAYS -> {
                generateConsecutiveDays(
                    numDays = 7,
                    nowMillis = nowMillis,
                    timeZone = timeZone
                )
            }
            HistoryPeriod.LAST_30_DAYS -> {
                generateConsecutiveDays(
                    numDays = 30,
                    nowMillis = nowMillis,
                    timeZone = timeZone
                )
            }
            HistoryPeriod.CUSTOM -> {
                val start = customStartMillis ?: (nowMillis - 6L * 24 * 3600 * 1000)
                val end = customEndMillis ?: nowMillis
                generateCustomDays(
                    startMillis = minOf(start, end),
                    endMillis = maxOf(start, end),
                    nowMillis = nowMillis,
                    timeZone = timeZone
                )
            }
        }
    }

    fun generateConsecutiveDays(
        numDays: Int,
        nowMillis: Long,
        timeZone: TimeZone
    ): List<DayBoundary> {
        val days = mutableListOf<DayBoundary>()
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
        }
        val todayYear = cal.get(Calendar.YEAR)
        val todayDayOfYear = cal.get(Calendar.DAY_OF_YEAR)

        cal.add(Calendar.DAY_OF_YEAR, -(numDays - 1))

        for (i in 0 until numDays) {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis

            val isToday = (cal.get(Calendar.YEAR) == todayYear && cal.get(Calendar.DAY_OF_YEAR) == todayDayOfYear)

            val dayEnd = if (isToday) {
                nowMillis
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                cal.timeInMillis
            }

            days.add(createDayBoundary(cal, dayStart, dayEnd, isToday))

            // Advance by 1 day using safe noon time to prevent DST boundary drift
            cal.set(Calendar.HOUR_OF_DAY, 12)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return days
    }

    fun generateCustomDays(
        startMillis: Long,
        endMillis: Long,
        nowMillis: Long,
        timeZone: TimeZone
    ): List<DayBoundary> {
        val days = mutableListOf<DayBoundary>()
        val startCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = startMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = endMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val todayCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
        }
        val todayYear = todayCal.get(Calendar.YEAR)
        val todayDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR)

        val cursor = startCal.clone() as Calendar

        while (cursor.timeInMillis <= endCal.timeInMillis && days.size < 366) {
            cursor.set(Calendar.HOUR_OF_DAY, 0)
            cursor.set(Calendar.MINUTE, 0)
            cursor.set(Calendar.SECOND, 0)
            cursor.set(Calendar.MILLISECOND, 0)
            val dayStart = cursor.timeInMillis

            val isToday = (cursor.get(Calendar.YEAR) == todayYear && cursor.get(Calendar.DAY_OF_YEAR) == todayDayOfYear)
            val dayEnd = if (isToday) {
                nowMillis
            } else {
                cursor.set(Calendar.HOUR_OF_DAY, 23)
                cursor.set(Calendar.MINUTE, 59)
                cursor.set(Calendar.SECOND, 59)
                cursor.set(Calendar.MILLISECOND, 999)
                cursor.timeInMillis
            }

            days.add(createDayBoundary(cursor, dayStart, dayEnd, isToday))

            cursor.set(Calendar.HOUR_OF_DAY, 12)
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }

        return days.ifEmpty {
            listOf(createDayBoundary(todayCal, startMillis, endMillis, isToday = true))
        }
    }

    private fun createDayBoundary(
        calendar: Calendar,
        startMillis: Long,
        endMillis: Long,
        isToday: Boolean
    ): DayBoundary {
        val dateIsoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = calendar.timeZone
        }
        val displayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).apply {
            timeZone = calendar.timeZone
        }
        val shortFormat = SimpleDateFormat("d", Locale.getDefault()).apply {
            timeZone = calendar.timeZone
        }

        val dateObj = Date(startMillis)
        val dateStr = dateIsoFormat.format(dateObj)
        val display = if (isToday) "Today" else displayFormat.format(dateObj)
        val short = shortFormat.format(dateObj)

        return DayBoundary(
            dateString = dateStr,
            displayLabel = display,
            shortLabel = short,
            startMillis = startMillis,
            endMillis = endMillis,
            isToday = isToday
        )
    }
}
