package com.example.wagetracker

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthSummaryCalculatorTest {

    private fun record(
        monthKey: String,
        hours: Double = 0.0,
        wage: Double = 0.0,
        isOff: Boolean = false
    ) = DailyRecord(
        id = "$monthKey-$hours-$wage",
        date = "Mon 1 Jan",
        isoDate = "${monthKey}-01",
        hours = hours,
        wageAmount = wage,
        isOff = isOff
    )

    @Test
    fun sums_hours_and_wages() {
        val records = listOf(
            record("2026-04", hours = 8.0, wage = 960.0),
            record("2026-04", hours = 7.5, wage = 900.0),
            record("2026-05", hours = 4.0, wage = 480.0)
        )
        val totals = MonthSummaryCalculator.summarize(records, "2026-04")
        assertEquals(15.5, totals.hours, 0.001)
        assertEquals(1860.0, totals.wages, 0.001)
    }

    @Test
    fun day_off_excluded_from_hours_and_wages() {
        val totals = MonthSummaryCalculator.summarize(
            listOf(record("2026-04", hours = 8.0, wage = 960.0, isOff = true)),
            "2026-04"
        )
        assertEquals(0.0, totals.hours, 0.001)
        assertEquals(0.0, totals.wages, 0.001)
    }

    @Test
    fun paid_month_zeroes_hours_and_wages() {
        val totals = MonthSummaryCalculator.summarize(
            listOf(record("2026-04", hours = 8.0, wage = 960.0)),
            "2026-04",
            isPaid = true
        )
        assertEquals(0.0, totals.hours, 0.001)
        assertEquals(0.0, totals.wages, 0.001)
    }

    @Test
    fun formatHours_whole_and_decimal() {
        assertEquals("8 hours", MonthSummaryCalculator.formatHours(8.0))
        assertEquals("7.5 hours", MonthSummaryCalculator.formatHours(7.5))
        assertEquals("0 hours", MonthSummaryCalculator.formatHours(0.0))
    }
}
