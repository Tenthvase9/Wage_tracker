package com.example.wagetracker

data class DailyRecord(
    val date: String,
    var tipsToPay: Double = 0.0,
    var cashPaidIn: Double = 0.0,
    var cardPaidIn: Double = 0.0,
    var isOff: Boolean = false
)