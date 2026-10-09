package com.example.wagetracker

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PaidMonthRecordCompatTest {

    private val gson = Gson()

    // Pre-branch JSON: no wages fields at all.
    private val oldJson = """
        {"monthKey":"2026-03","monthName":"March 2026","paidDate":"2026-03-31",
         "amountOwed":1200.0,"paidAmount":1200.0,
         "totalCardTips":900.0,"totalCashTips":300.0,"totalCashPaidIn":0.0}
    """.trimIndent()

    @Test
    fun old_json_leaves_wagesPaidDate_null() {
        val parsed = gson.fromJson(oldJson, PaidMonthRecord::class.java)
        val raw: String? = parsed.wagesPaidDate
        assertNull(raw)
    }

    @Test
    fun withWageDefaults_coerces_null_and_preserves_paidDate() {
        val parsed = gson.fromJson(oldJson, PaidMonthRecord::class.java)
        val fixed = WageRepository.withWageDefaults(parsed)
        assertEquals("", fixed.wagesPaidDate)
        assertEquals("2026-03-31", fixed.paidDate)
        assertEquals("2026-03", fixed.monthKey)
    }

    @Test
    fun withWageDefaults_passes_through_record_with_wagesPaidDate() {
        val parsed = gson.fromJson(
            """{"monthKey":"2026-04","monthName":"April 2026","paidDate":"2026-04-30",
               "amountOwed":0.0,"paidAmount":0.0,"totalCardTips":0.0,"totalCashTips":0.0,
               "totalCashPaidIn":0.0,"totalHours":8.0,"wagesOwed":960.0,"wagesPaid":960.0,
               "wagesPaidDate":"2026-04-30"}""",
            PaidMonthRecord::class.java
        )
        val fixed = WageRepository.withWageDefaults(parsed)
        assertSame(parsed, fixed)
        assertEquals("2026-04-30", fixed.wagesPaidDate)
    }
}
