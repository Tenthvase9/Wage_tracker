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
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.appcompat.app.AppCompatDelegate


data class MonthTotals(
    val cardTips: Double,
    val cashTips: Double,
    val cashPaidIn: Double,
    val owed: Double
)

class MainActivity : AppCompatActivity() {
    private lateinit var settingsDialog: AlertDialog
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordAdapter
    private val records = mutableListOf<DailyRecord>()
    private val paidMonths = mutableListOf<PaidMonthRecord>()
    private val sharedPrefs by lazy { getSharedPreferences("wages_tracker", MODE_PRIVATE) }
    private val gson = Gson()

    private var currentMonth = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force Light Mode - add this FIRST
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

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

        binding.btnShare.setOnClickListener {
            shareCurrentMonthData()
        }

        binding.btnExportCSV.setOnClickListener {
            exportToCSV()
        }

        // Add settings button listener
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
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
        val isAlreadyPaid = isMonthPaid()

        if (!isAlreadyPaid && currentOwed <= 0) {
            Toast.makeText(this, "No amount owed for $monthName", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_paid_month, null)
        val tvMonthInfo = dialogView.findViewById<TextView>(R.id.tvMonthInfo)

        if (isAlreadyPaid) {
            tvMonthInfo.text = "$monthName is currently marked as PAID\n\nTap confirm to REVERSE (mark as unpaid)"
        } else {
            tvMonthInfo.text = "$monthName\nAmount Owed: R${String.format("%.0f", currentOwed)}\n\nTap confirm to mark as PAID"
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(if (isAlreadyPaid) "REVERSE (Unpaid)" else "Confirm Paid") { _, _ ->
                if (isAlreadyPaid) {
                    reverseMonthPaid()
                } else {
                    markMonthAsPaid()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reverseMonthPaid() {
        val monthKey = getMonthKey(currentMonth)
        val monthName = getMonthName(currentMonth)

        // Remove the paid record
        val index = paidMonths.indexOfFirst { it.monthKey == monthKey }
        if (index != -1) {
            paidMonths.removeAt(index)
            savePaidMonths()
            Toast.makeText(this, "$monthName marked as UNPAID", Toast.LENGTH_LONG).show()
            updateSummary()
        }
    }

    private fun markMonthAsPaid() {
        val monthKey = getMonthKey(currentMonth)
        val monthName = getMonthName(currentMonth)
        val totals = calculateMonthTotals()
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        val paidRecord = PaidMonthRecord(
            monthKey = monthKey,
            monthName = monthName,
            paidDate = today,
            amountOwed = totals.owed,
            totalCardTips = totals.cardTips,
            totalCashTips = totals.cashTips,
            totalCashPaidIn = totals.cashPaidIn
        )

        paidMonths.add(paidRecord)
        savePaidMonths()
        Toast.makeText(this, "$monthName marked as PAID", Toast.LENGTH_LONG).show()
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
        adapter = RecordAdapter(
            records = records,
            onDeleteClick = { record ->
                val position = records.indexOfFirst { it.date == record.date }
                if (position != -1) {
                    records.removeAt(position)
                    saveRecords()
                    updateSummary()
                    Toast.makeText(this, "Deleted ${record.date}", Toast.LENGTH_SHORT).show()
                }
            },
            onEditClick = { record ->
                showEditDialog(record)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun updateMonthDisplay() {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
        binding.tvCurrentMonth.text = monthFormat.format(currentMonth.time)
    }

    private fun updateSummary() {
        var totalCardTips = 0.0
        var totalCashTips = 0.0
        var totalCashPaidIn = 0.0

        val monthKey = getMonthKey(currentMonth)
        val isPaid = isMonthPaid()

        for (record in records) {
            if (!record.isOff && record.monthKey == monthKey) {
                totalCardTips += record.cardTips
                totalCashTips += record.cashTips
                totalCashPaidIn += record.cashPaidIn
            }
        }

        val totalCardCashTips = totalCardTips + totalCashTips
        val totalOwed = if (isPaid) 0.0 else totalCardTips + totalCashPaidIn

        binding.totalCardTips.text = String.format("R%.0f", totalCardTips)
        binding.totalCashTips.text = String.format("R%.0f", totalCashTips)
        binding.totalCardCashTips.text = String.format("R%.0f", totalCardCashTips)
        binding.totalCashPaidIn.text = String.format("R%.0f", totalCashPaidIn)
        binding.totalOwed.text = String.format("R%.0f", totalOwed)

        // Visual feedback for paid/unpaid status
        if (isPaid) {
            binding.btnMarkPaid.text = "✓ PAID (Tap to reverse)"
            binding.btnMarkPaid.setBackgroundColor(resources.getColor(android.R.color.holo_orange_dark))
        } else {
            binding.btnMarkPaid.text = "MARK PAID"
            binding.btnMarkPaid.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark))
        }

        // Filter records for current month
        val monthRecords = records.filter { it.monthKey == monthKey }
        adapter.updateRecords(monthRecords.sortedByDescending { it.date })

        // Update the mini chart
        updateMiniChart()
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

    private fun shareCurrentMonthData() {
        val monthName = getMonthName(currentMonth)
        val monthKey = getMonthKey(currentMonth)

        // Get records for current month
        val monthRecords = records.filter { it.monthKey == monthKey && !it.isOff }

        if (monthRecords.isEmpty()) {
            Toast.makeText(this, "No data for $monthName", Toast.LENGTH_SHORT).show()
            return
        }

        // Build share text
        val shareText = buildShareText(monthName, monthRecords)

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        startActivity(Intent.createChooser(shareIntent, "Share $monthName Data"))
    }

    private fun buildShareText(monthName: String, records: List<DailyRecord>): String {
        var totalCardTips = 0.0
        var totalCashTips = 0.0
        var totalCashPaidIn = 0.0

        val builder = StringBuilder()
        builder.appendLine("📊 WAGE TRACKER REPORT")
        builder.appendLine("=" .repeat(30))
        builder.appendLine("Month: $monthName")
        builder.appendLine()
        builder.appendLine("DAILY BREAKDOWN:")
        builder.appendLine("-" .repeat(20))

        for (record in records.sortedBy { it.date }) {
            builder.appendLine("${record.date}:")
            builder.appendLine("  Card Tips: R${String.format("%.0f", record.cardTips)}")
            builder.appendLine("  Cash Tips: R${String.format("%.0f", record.cashTips)}")
            builder.appendLine("  Cash Paid In: R${String.format("%.0f", record.cashPaidIn)}")
            builder.appendLine()

            totalCardTips += record.cardTips
            totalCashTips += record.cashTips
            totalCashPaidIn += record.cashPaidIn
        }

        val totalOwed = totalCardTips + totalCashPaidIn
        val totalTips = totalCardTips + totalCashTips

        builder.appendLine("=" .repeat(30))
        builder.appendLine("SUMMARY:")
        builder.appendLine("Total Card Tips: R${String.format("%.0f", totalCardTips)}")
        builder.appendLine("Total Cash Tips: R${String.format("%.0f", totalCashTips)}")
        builder.appendLine("Total Tips: R${String.format("%.0f", totalTips)}")
        builder.appendLine("Total Cash Paid In: R${String.format("%.0f", totalCashPaidIn)}")
        builder.appendLine("-" .repeat(20))
        builder.appendLine("💰 YOU ARE OWED: R${String.format("%.0f", totalOwed)}")
        builder.appendLine("=" .repeat(30))
        builder.appendLine("Generated by Wage Tracker App")

        return builder.toString()
    }

    private fun exportToCSV() {
        // Request permission for Android 10 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1001)
                return
            }
        }

        val monthName = getMonthName(currentMonth).replace(" ", "_")
        val fileName = "WageTracker_${monthName}_${System.currentTimeMillis()}.csv"

        val csvContent = buildCSVContent()

        try {
            val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, save to Downloads folder
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(downloadsDir, fileName)
            } else {
                // For older Android, save to external storage
                val file = File(Environment.getExternalStorageDirectory(), fileName)
                file
            }

            FileOutputStream(file).use { outputStream ->
                outputStream.write(csvContent.toByteArray())
            }

            // Share the file
            shareCSVFile(file)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildCSVContent(): String {
        val monthKey = getMonthKey(currentMonth)
        val monthRecords = records.filter { it.monthKey == monthKey && !it.isOff }.sortedBy { it.date }

        val builder = StringBuilder()
        builder.appendLine("Date,Card Tips (R),Cash Tips (R),Cash Paid In (R)")

        for (record in monthRecords) {
            builder.appendLine("${record.date},${record.cardTips},${record.cashTips},${record.cashPaidIn}")
        }

        // Add totals row
        val totalCardTips = monthRecords.sumOf { it.cardTips }
        val totalCashTips = monthRecords.sumOf { it.cashTips }
        val totalCashPaidIn = monthRecords.sumOf { it.cashPaidIn }
        val totalOwed = totalCardTips + totalCashPaidIn

        builder.appendLine()
        builder.appendLine("TOTALS,,,")
        builder.appendLine("Total Card Tips,R${String.format("%.0f", totalCardTips)},,")
        builder.appendLine("Total Cash Tips,R${String.format("%.0f", totalCashTips)},,")
        builder.appendLine("Total Cash Paid In,R${String.format("%.0f", totalCashPaidIn)},,")
        builder.appendLine("YOU ARE OWED,R${String.format("%.0f", totalOwed)},,")

        return builder.toString()
    }

    private fun shareCSVFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share CSV File"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "File saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
    private fun updateMiniChart() {
        val monthKey = getMonthKey(currentMonth)
        val monthRecords = records.filter { it.monthKey == monthKey && !it.isOff }
            .sortedBy { it.date }

        // Get last 7 days (or fewer if not enough data)
        val last7Days = monthRecords.takeLast(7)

        val bars = listOf(
            binding.bar1, binding.bar2, binding.bar3, binding.bar4,
            binding.bar5, binding.bar6, binding.bar7
        )

        if (last7Days.isEmpty()) {
            // Hide all bars if no data
            bars.forEach { it.visibility = android.view.View.GONE }
            binding.chartLabel.text = "No data available for chart"
            return
        }

        // Calculate max value for scaling
        val maxDailyTotal = last7Days.maxOfOrNull { it.cardTips + it.cashTips } ?: 1.0

        // Show and scale bars
        bars.forEachIndexed { index, bar ->
            if (index < last7Days.size) {
                val dailyTotal = last7Days[index].cardTips + last7Days[index].cashTips
                val heightPercent = (dailyTotal / maxDailyTotal).toFloat()
                val maxHeight = 60 // dp
                val targetHeight = (maxHeight * heightPercent).toInt()

                bar.layoutParams.height = targetHeight
                bar.visibility = android.view.View.VISIBLE

                // Color based on value (green for high, blue for medium, light blue for low)
                when {
                    dailyTotal >= maxDailyTotal * 0.7 -> bar.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark))
                    dailyTotal >= maxDailyTotal * 0.3 -> bar.setBackgroundColor(resources.getColor(android.R.color.holo_blue_dark))
                    else -> bar.setBackgroundColor(resources.getColor(android.R.color.holo_blue_light))
                }
            } else {
                bar.visibility = android.view.View.GONE
            }
        }

        // Update label with date range
        if (last7Days.isNotEmpty()) {
            val firstDate = last7Days.first().date
            val lastDate = last7Days.last().date
            binding.chartLabel.text = "Last ${last7Days.size} days: $firstDate - $lastDate (Card + Cash Tips)"
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            exportToCSV()
        } else {
            Toast.makeText(this, "Permission denied. Cannot export CSV.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditDialog(record: DailyRecord) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_record, null)
        val tvEditDate = dialogView.findViewById<TextView>(R.id.tvEditDate)
        val etCardTips = dialogView.findViewById<EditText>(R.id.etEditCardTips)
        val etCashTips = dialogView.findViewById<EditText>(R.id.etEditCashTips)
        val etCashPaidIn = dialogView.findViewById<EditText>(R.id.etEditCashPaidIn)

        // Show which date we're editing
        tvEditDate.text = "Editing: ${record.date}"

        // Pre-fill with existing values
        etCardTips.setText(if (record.cardTips > 0) String.format("%.0f", record.cardTips) else "")
        etCashTips.setText(if (record.cashTips > 0) String.format("%.0f", record.cashTips) else "")
        etCashPaidIn.setText(if (record.cashPaidIn > 0) String.format("%.0f", record.cashPaidIn) else "")

        AlertDialog.Builder(this)
            .setTitle("Edit Entry")
            .setView(dialogView)
            .setPositiveButton("Save Changes") { _, _ ->
                // Get updated values
                val newCardTips = etCardTips.text.toString().toDoubleOrNull() ?: 0.0
                val newCashTips = etCashTips.text.toString().toDoubleOrNull() ?: 0.0
                val newCashPaidIn = etCashPaidIn.text.toString().toDoubleOrNull() ?: 0.0

                // Update the record
                record.cardTips = newCardTips
                record.cashTips = newCashTips
                record.cashPaidIn = newCashPaidIn

                // Save and refresh
                saveRecords()
                updateSummary()

                Toast.makeText(this, "Updated ${record.date}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset to Zero") { _, _ ->
                // Reset all values to zero
                record.cardTips = 0.0
                record.cashTips = 0.0
                record.cashPaidIn = 0.0

                saveRecords()
                updateSummary()

                Toast.makeText(this, "Reset ${record.date} to zero", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}

