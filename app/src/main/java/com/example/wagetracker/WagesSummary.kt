package com.example.wagetracker

data class WagesSummary(
    val totalCashPaidIn: Double = 0.0,
    val totalCardPaidIn: Double = 0.0,
    val totalTipsToPay: Double = 0.0
) {
    val overallTotalToPay: Double
        get() = totalTipsToPay
}