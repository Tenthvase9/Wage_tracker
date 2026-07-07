package com.example.wagetracker

import java.util.Calendar
import java.util.Locale

/**
 * Pure functions that turn a list of records into month-level numbers.
 * No Android types, no SharedPreferences - safe to unit test and to call
 * from any screen (main, year view, widget).
 */
object MonthSummaryCalculator {

    data class Totals(
        val cardTips: Double,
        val cashTips: Double,
        val cashPaidIn: Double
    ) {
        val tips: Double get() = cardTips + cashTips
        // "Owed" = money the house owes the worker for this month.
        // Card tips get paid out by the house; cash paid in is banked for the worker.
        val owed: Double get() = cardTips + cashPaidIn
    }

    fun summarize(records: List<DailyRecord>, monthKey: String, isPaid: Boolean = false): Totals {
        var card = 0.0
        var cash = 0.0
        var paidIn = 0.0
        for (r in records) {
            if (r.monthKey != monthKey) continue
            if (r.isOff) continue
            card += r.cardTips
            cash += r.cashTips
            paidIn += r.cashPaidIn
        }
        return if (isPaid) Totals(0.0, 0.0, 0.0)
        else Totals(card, cash, paidIn)
    }

    fun workedDays(records: List<DailyRecord>, monthKey: String): Int {
        return records.count { it.monthKey == monthKey && !it.isOff }
    }

    fun monthKey(calendar: Calendar): String {
        return String.format(
            Locale.ENGLISH,
            "%04d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1
        )
    }

    fun monthName(calendar: Calendar): String {
        val months = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        return "${months[calendar.get(Calendar.MONTH)]} ${calendar.get(Calendar.YEAR)}"
    }
}