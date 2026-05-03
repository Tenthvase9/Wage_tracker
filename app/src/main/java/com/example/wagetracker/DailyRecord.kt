package com.example.wagetracker

data class DailyRecord(
    val date: String,
    var cardTips: Double = 0.0,
    var cashTips: Double = 0.0,
    var cashPaidIn: Double = 0.0,
    var isOff: Boolean = false,
    val monthKey: String = ""  // New: Stores "YYYY-MM" for filtering
)