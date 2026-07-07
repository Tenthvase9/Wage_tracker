package com.example.wagetracker

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Date helpers - single owner of how dates are stored and shown.
 * Records carry an ISO yyyy-MM-dd for sorting and a friendly "Mon 3 Apr"
 * string for display. monthKey is derived from the ISO date, so the
 * month selector can never disagree with the record it sorted.
 */
object DateUtils {

    private const val ISO = "yyyy-MM-dd"
    private val isoFormat = SimpleDateFormat(ISO, Locale.ENGLISH)
    private val dayOfWeek = SimpleDateFormat("EEE", Locale.ENGLISH)
    private val dayMonth = SimpleDateFormat("d MMM", Locale.ENGLISH)

    fun isoFor(year: Int, monthZeroBased: Int, day: Int): String {
        val cal = Calendar.getInstance().apply {
            set(year, monthZeroBased, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return isoFormat.format(cal.time)
    }

    fun calendarFromIso(iso: String): Calendar {
        val cal = Calendar.getInstance()
        cal.time = isoFormat.parse(iso) ?: Date(0)
        return cal
    }

    fun displayFromIso(iso: String): String {
        val cal = calendarFromIso(iso)
        val dow = dayOfWeek.format(cal.time)
        val dm = dayMonth.format(cal.time)
        return "$dow $dm"
    }

    fun monthKey(year: Int, monthZeroBased: Int): String =
        String.format(Locale.ENGLISH, "%04d-%02d", year, monthZeroBased + 1)
}