package com.example.wagetracker

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wagetracker.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordAdapter
    private val records = mutableListOf<DailyRecord>()
    private val sharedPrefs by lazy { getSharedPreferences("wages_tracker", MODE_PRIVATE) }
    private val gson = Gson()

    private var currentMonth = Calendar.getInstance()
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadRecords()
        updateMonthDisplay()
        updateSummary()
        filterRecords()

        binding.btnAddRecord.setOnClickListener {
            showAddRecordDialog()
        }

        binding.btnPrevMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, -1)
            updateMonthDisplay()
            filterRecords()
            updateSummary()
        }

        binding.btnNextMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, 1)
            updateMonthDisplay()
            filterRecords()
            updateSummary()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString().lowercase()
                filterRecords()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = RecordAdapter(records) { record ->
            // Find and remove the record by its date
            val actualPosition = records.indexOfFirst { it.date == record.date }
            if (actualPosition != -1) {
                records.removeAt(actualPosition)
                saveRecords()
                filterRecords()
                updateSummary()
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun showAddRecordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_record, null)
        val etDate = dialogView.findViewById<EditText>(R.id.etDate)
        val etTips = dialogView.findViewById<EditText>(R.id.etTips)
        val etCashPaid = dialogView.findViewById<EditText>(R.id.etCashPaid)
        val etCardPaid = dialogView.findViewById<EditText>(R.id.etCardPaid)

        AlertDialog.Builder(this)
            .setTitle("Add Daily Entry")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val date = etDate.text.toString()
                if (date.isNotEmpty()) {
                    val cardAmount = etCardPaid.text.toString().toDoubleOrNull() ?: 0.0
                    val cashAmount = etCashPaid.text.toString().toDoubleOrNull() ?: 0.0

                    val record = DailyRecord(
                        date = date,
                        tipsToPay = cardAmount,  // Card tips
                        cashPaidIn = cashAmount,  // Cash tips
                        cardPaidIn = 0.0,
                        isOff = false
                    )
                    records.add(record)
                    saveRecords()
                    filterRecords()
                    updateSummary()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun filterRecords() {
        val filtered = records.filter { record ->
            val matchesMonth = isInCurrentMonth(record.date)
            val matchesSearch = if (searchQuery.isNotEmpty()) {
                record.date.lowercase().contains(searchQuery)
            } else true
            matchesMonth && matchesSearch
        }
        adapter.updateRecords(filtered.sortedByDescending { parseDate(it.date) })
    }

    private fun isInCurrentMonth(dateString: String): Boolean {
        try {
            val date = parseDate(dateString)
            return date?.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH) &&
                    date.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR)
        } catch (e: Exception) {
            return true
        }
    }

    private fun parseDate(dateString: String): Calendar? {
        val formats = listOf("d MMM yyyy", "d MMMM yyyy", "d MMM", "d MMMM", "d MMMM yyyy")
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.ENGLISH)
                val date = sdf.parse(dateString)
                if (date != null) {
                    return Calendar.getInstance().apply { time = date }
                }
            } catch (e: Exception) { }
        }
        return null
    }

    private fun updateMonthDisplay() {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
        binding.tvCurrentMonth.text = monthFormat.format(currentMonth.time)
    }

    private fun updateSummary() {
        // Get records for current month only
        val monthRecords = records.filter { isInCurrentMonth(it.date) }

        var totalCardTips = 0.0
        var totalCashTips = 0.0
        var totalCashPaidIn = 0.0

        for (record in monthRecords) {
            if (!record.isOff) {
                totalCardTips += record.tipsToPay
                totalCashTips += record.cashPaidIn
                totalCashPaidIn += record.cardPaidIn
            }
        }

        val totalTips = totalCardTips + totalCashTips
        // Owed = (Card Tips + Cash Paid In) - Cash Tips
        val totalOwed = (totalCardTips + totalCashPaidIn) - totalCashTips

        binding.totalCardTips.text = String.format("R%.0f", totalCardTips)
        binding.totalCashTips.text = String.format("R%.0f", totalCashTips)
        binding.totalTips.text = String.format("R%.0f", totalTips)
        binding.totalCashPaidIn.text = String.format("R%.0f", totalCashPaidIn)
        binding.totalOwed.text = String.format("R%.0f", totalOwed)

        // Show calculation
        binding.tvCalculation.text = "Calculation: (R${String.format("%.0f", totalCardTips)} + R${String.format("%.0f", totalCashPaidIn)}) - R${String.format("%.0f", totalCashTips)} = R${String.format("%.0f", totalOwed)}"
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
}