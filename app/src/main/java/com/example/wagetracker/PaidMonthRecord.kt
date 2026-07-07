package com.example.wagetracker

data class PaidMonthRecord(
    val monthKey: String,         // e.g., "2026-04"
    val monthName: String,        // e.g., "April 2026"
    val paidDate: String,         // when it was marked paid
    val amountOwed: Double,       // amount that was owed at mark time
    val paidAmount: Double,       // amount actually paid (may be < amountOwed)
    val totalCardTips: Double,
    val totalCashTips: Double,
    val totalCashPaidIn: Double
) {
    val shortBy: Double
        get() = (amountOwed - paidAmount).coerceAtLeast(0.0)
}