package com.example.wagetracker

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Single source of truth for records and paid months.
 * Wraps SharedPreferences + Gson; callers receive immutable lists and
 * mutate through add/update/delete. This is the only place that
 * reads or writes the JSON blobs.
 */
class WageRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    private var _records: MutableList<DailyRecord> = loadList(KEY_RECORDS)
    private var _paid: MutableList<PaidMonthRecord> = loadList(KEY_PAID)

    init {
        // Gson can set non-null fields to null during deserialisation.
        // Remove any corrupted records whose isoDate is null or blank.
        val bad = _records.filter { (it.isoDate as? String).isNullOrBlank() }
        if (bad.isNotEmpty()) {
            _records.removeAll(bad)
            persist()
        }
    }

    val records: List<DailyRecord> get() = _records
    val paidMonths: List<PaidMonthRecord> get() = _paid

    fun addRecord(record: DailyRecord) {
        // Replace by id; add if new
        val idx = _records.indexOfFirst { it.id == record.id }
        if (idx >= 0) _records[idx] = record else _records.add(record)
        persist()
    }

    fun updateRecord(record: DailyRecord) {
        val idx = _records.indexOfFirst { it.id == record.id }
        if (idx >= 0) {
            _records[idx] = record
            persist()
        }
    }

    fun deleteRecord(id: String): DailyRecord? {
        val idx = _records.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val removed = _records.removeAt(idx)
        persist()
        return removed
    }

    fun insertRecordAt(index: Int, record: DailyRecord) {
        val safe = index.coerceIn(0, _records.size)
        _records.add(safe, record)
        persist()
    }

    fun monthPaid(monthKey: String): Boolean = _paid.any { it.monthKey == monthKey }

    fun paidRecord(monthKey: String): PaidMonthRecord? = _paid.firstOrNull { it.monthKey == monthKey }

    fun markMonthPaid(record: PaidMonthRecord) {
        val idx = _paid.indexOfFirst { it.monthKey == record.monthKey }
        if (idx >= 0) _paid[idx] = record else _paid.add(record)
        persistPaid()
    }

    fun unmarkMonthPaid(monthKey: String) {
        val idx = _paid.indexOfFirst { it.monthKey == monthKey }
        if (idx >= 0) {
            _paid.removeAt(idx)
            persistPaid()
        }
    }

    fun clearAll() {
        _records.clear()
        _paid.clear()
        persist()
        persistPaid()
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK, enabled).apply()
    }

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK, false)

    private fun persist() {
        prefs.edit().putString(KEY_RECORDS, gson.toJson(_records)).apply()
    }

    private fun persistPaid() {
        prefs.edit().putString(KEY_PAID, gson.toJson(_paid)).apply()
    }

    private inline fun <reified T> loadList(key: String): MutableList<T> {
        val json = prefs.getString(key, "[]")
        val type = object : TypeToken<MutableList<T>>() {}.type
        return gson.fromJson<MutableList<T>>(json, type) ?: mutableListOf()
    }

    companion object {
        private const val PREFS = "wages_tracker"
        private const val KEY_RECORDS = "records"
        private const val KEY_PAID = "paid_months"
        private const val KEY_DARK = "dark_mode"
    }
}