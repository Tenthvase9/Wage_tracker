package com.example.wagetracker

data class DailyRecord(
    val id: String,           // stable id, used by RecyclerView and adapter deletes
    var date: String,         // display string, e.g. "Mon 3 Apr"
    var isoDate: String,      // ISO yyyy-MM-dd, used for sort and monthKey derivation
    var cardTips: Double = 0.0,
    var cashTips: Double = 0.0,
    var cashPaidIn: Double = 0.0,
    var isOff: Boolean = false,
    var notes: String = ""
) {
    val monthKey: String
        get() = (isoDate as? String)?.take(7) ?: ""

    val dayTotal: Double
        get() = cardTips + cashTips + cashPaidIn
}