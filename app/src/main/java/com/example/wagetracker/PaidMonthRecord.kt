package com.example.wagetracker

data class PaidMonthRecord(
    val monthKey: String,      // e.g., "2026-04"
    val monthName: String,     // e.g., "April 2026"
    val paidDate: String,      // When it was marked paid
    val amountOwed: Double,    // Amount that was paid
    val totalCardTips: Double,
    val totalCashTips: Double,
    val totalCashPaidIn: Double
)