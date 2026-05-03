package com.example.wagetracker

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wagetracker.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class MonthTotals(
    val cardTips: Double,
    val cashTips: Double,
    val cashPaidIn: Double,
    val owed: Double
)

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordAdapter
    private val records = mutableListOf<DailyRecord>()
    private val paidMonths = mutableListOf<PaidMonthRecord>()
    private val sharedPrefs by lazy { getSharedPreferences("wages_tracker", MODE_PRIVATE) }
    private val gson = Gson()

    private var currentMonth = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadRecords()
        loadPaidMonths()
        updateMonthDisplay()
        updateSummary()

        binding.btnAddRecord.setOnClickListener {
            showAddRecordDialog()
        }

        binding.btnMarkPaid.setOnClickListener {
            showMarkPaidDialog()
        }

        binding.btnPrevMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, -1)
            updateMonthDisplay()
            updateSummary()
        }

        binding.btnNextMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, 1)
            updateMonthDisplay()
            updateSummary()
        }
    }

    private fun getMonthKey(date: Calendar): String {
        return SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(date.time)
    }

    private fun getMonthName(date: Calendar): String {
        return SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(date.time)
    }

    private fun isMonthPaid(): Boolean {
        val monthKey = getMonthKey(currentMonth)
        return paidMonths.any { it.monthKey == monthKey }
    }

    private fun showMarkPaidDialog() {
        val monthName = getMonthName(currentMonth)
        val currentOwed = calculateCurrentMonthOwed()

        if (currentOwed <= 0) {
            Toast.makeText(this, "No amount owed for $monthName", Toast.LENGTH_SHORT).show()
            return
        }

        if (isMonthPaid()) {
            Toast.makeText(this, "$monthName has already been marked as paid", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_paid_month, null)
        val tvMonthInfo = dialogView.findViewById<TextView>(R.id.tvMonthInfo)
        tvMonthInfo.text = "$monthName\nAmount Owed: R${String.format("%.0f", currentOwed)}"

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Confirm Paid") { _, _ ->
                markMonthAsPaid()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun markMonthAsPaid() {
        val monthKey = getMonthKey(currentMonth)
        val monthName = getMonthName(currentMonth)
        val (cardTips, cashTips, cashPaidIn, owed) = calculateMonthTotals()
        val currentDate = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date())

        val paidRecord = PaidMonthRecord(
            monthKey = monthKey,
            monthName = monthName,
            paidDate = currentDate,
            amountOwed = owed,
            totalCardTips = cardTips,
            totalCashTips = cashTips,
            totalCashPaidIn = cashPaidIn
        )

        paidMonths.add(paidRecord)
        savePaidMonths()

        Toast.makeText(this, "$monthName marked as paid! Amount R${String.format("%.0f", owed)}", Toast.LENGTH_LONG).show()

        // Refresh display (owed will show 0 for this month)
        updateSummary()
    }

    private fun calculateMonthTotals(): MonthTotals {
        var totalCardTips = 0.0
        var totalCashTips = 0.0
        var totalCashPaidIn = 0.0

        val monthKey = getMonthKey(currentMonth)

        for (record in records) {
            if (!record.isOff && record.monthKey == monthKey) {
                totalCardTips += record.cardTips
                totalCashTips += record.cashTips
                totalCashPaidIn += record.cashPaidIn
            }
        }

        val owed = totalCardTips + totalCashPaidIn
        return MonthTotals(totalCardTips, totalCashTips, totalCashPaidIn, owed)
    }

    private fun calculateCurrentMonthOwed(): Double {
        return calculateMonthTotals().owed
    }

    private fun showAddRecordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_record, null)
        val etDate = dialogView.findViewById<EditText>(R.id.etDate)
        val etCardTips = dialogView.findViewById<EditText>(R.id.etCardTips)
        val etCashTips = dialogView.findViewById<EditText>(R.id.etCashTips)
        val etCashPaidIn = dialogView.findViewById<EditText>(R.id.etCashPaidIn)

        AlertDialog.Builder(this)
            .setTitle("Add Daily Entry")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val date = etDate.text.toString()
                if (date.isNotEmpty()) {
                    val cardTips = etCardTips.text.toString().toDoubleOrNull() ?: 0.0
                    val cashTips = etCashTips.text.toString().toDoubleOrNull() ?: 0.0
                    val cashPaidIn = etCashPaidIn.text.toString().toDoubleOrNull() ?: 0.0

                    // Parse month from date
                    val monthKey = parseMonthKey(date)

                    val newRecord = DailyRecord(
                        date = date,
                        cardTips = cardTips,
                        cashTips = cashTips,
                        cashPaidIn = cashPaidIn,
                        isOff = false,
                        monthKey = monthKey
                    )

                    records.add(newRecord)
                    saveRecords()
                    updateSummary()

                    Toast.makeText(this, "Added: $date", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a date", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun parseMonthKey(dateString: String): String {
        val months = mapOf(
            "january" to 1, "february" to 2, "march" to 3, "april" to 4,
            "may" to 5, "june" to 6, "july" to 7, "august" to 8,
            "september" to 9, "october" to 10, "november" to 11, "december" to 12
        )

        val lowerDate = dateString.lowercase()
        for ((month, number) in months) {
            if (lowerDate.contains(month)) {
                val year = Calendar.getInstance().get(Calendar.YEAR)
                return String.format("%d-%02d", year, number)
            }
        }
        return getMonthKey(currentMonth)
    }

    private fun setupRecyclerView() {
        adapter = RecordAdapter(records) { record ->
            val position = records.indexOfFirst { it.date == record.date }
            if (position != -1) {
                records.removeAt(position)
                saveRecords()
                updateSummary()
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun updateMonthDisplay() {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
        binding.tvCurrentMonth.text = monthFormat.format(currentMonth.time)
    }

    private fun updateSummary() {
        val totals = calculateMonthTotals()
        val monthKey = getMonthKey(currentMonth)
        val isPaid = isMonthPaid()

        val totalCardCashTips = totals.cardTips + totals.cashTips
        // If month is paid, owed amount is 0
        val totalOwed = if (isPaid) 0.0 else totals.owed

        binding.totalCardTips.text = String.format("R%.0f", totals.cardTips)
        binding.totalCashTips.text = String.format("R%.0f", totals.cashTips)
        binding.totalCardCashTips.text = String.format("R%.0f", totalCardCashTips)
        binding.totalCashPaidIn.text = String.format("R%.0f", totals.cashPaidIn)
        binding.totalOwed.text = String.format("R%.0f", totalOwed)

        // Show paid status
        if (isPaid) {
            binding.totalOwed.setBackgroundColor(resources.getColor(android.R.color.holo_green_light))
            Toast.makeText(this, "✓ ${getMonthName(currentMonth)} has been paid", Toast.LENGTH_SHORT).show()
        } else {
            binding.totalOwed.setBackgroundColor(resources.getColor(android.R.color.holo_blue_light))
        }

        // Filter records for current month
        val monthRecords = records.filter { it.monthKey == monthKey }
        adapter.updateRecords(monthRecords.sortedByDescending { it.date })
    }

    private fun saveRecords() {
        val json = gson.toJson(records)
        sharedPrefs.edit().putString("records", json).apply()
    }

    private fun loadRecords() {
        val json = sharedPrefs.getString("records", "[]")
        val type = object : TypeToken<MutableList<DailyRecord>>() {}.type
        val loadedRecords: MutableList<DailyRecord> = gson.fromJson(json, type)
        records.clear()
        records.addAll(loadedRecords)
    }

    private fun savePaidMonths() {
        val json = gson.toJson(paidMonths)
        sharedPrefs.edit().putString("paid_months", json).apply()
    }

    private fun loadPaidMonths() {
        val json = sharedPrefs.getString("paid_months", "[]")
        val type = object : TypeToken<MutableList<PaidMonthRecord>>() {}.type
        val loaded: MutableList<PaidMonthRecord> = gson.fromJson(json, type)
        paidMonths.clear()
        paidMonths.addAll(loaded)
    }
}